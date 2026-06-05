import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bookmarkApi, bookmarkCollectionApi } from '@datacatalog/shared';
import type { Bookmark, BookmarkCollection } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';
import DatasetDetailDrawer from '../components/DatasetDetailDrawer';

export default function BookmarksPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { openDataset, openDatasetId } = useDrawerStore();

  const [selectedCollectionId, setSelectedCollectionId] = useState<string | null>(null);
  const [newCollectionName, setNewCollectionName] = useState('');
  const [showNewCollectionForm, setShowNewCollectionForm] = useState(false);
  const [editingCollection, setEditingCollection] = useState<BookmarkCollection | null>(null);
  const [editName, setEditName] = useState('');

  const { data: collections = [] } = useQuery({
    queryKey: ['bookmark-collections'],
    queryFn: () => bookmarkCollectionApi.list(),
    staleTime: 30_000,
  });

  const { data: bookmarks = [], isLoading } = useQuery({
    queryKey: ['bookmarks', selectedCollectionId],
    queryFn: () => bookmarkApi.list(selectedCollectionId ?? undefined),
    staleTime: 30_000,
  });

  const createCollection = useMutation({
    mutationFn: (name: string) => bookmarkCollectionApi.create({ name }),
    onSuccess: (col) => {
      qc.invalidateQueries({ queryKey: ['bookmark-collections'] });
      setNewCollectionName('');
      setShowNewCollectionForm(false);
      setSelectedCollectionId(col.id);
    },
  });

  const updateCollection = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      bookmarkCollectionApi.update(id, { name }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmark-collections'] });
      setEditingCollection(null);
    },
  });

  const deleteCollection = useMutation({
    mutationFn: (id: string) => bookmarkCollectionApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmark-collections'] });
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
      if (selectedCollectionId === deleteCollection.variables) setSelectedCollectionId(null);
    },
  });

  const removeBookmark = useMutation({
    mutationFn: (id: string) => bookmarkApi.delete(id),
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
      qc.invalidateQueries({ queryKey: ['bookmark-check'] });
    },
  });

  const moveBookmark = useMutation({
    mutationFn: ({ id, collectionId }: { id: string; collectionId: string | null }) =>
      bookmarkApi.patch(id, { collectionId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
    },
  });

  const totalCount = useQuery({
    queryKey: ['bookmarks', null],
    queryFn: () => bookmarkApi.list(),
    staleTime: 30_000,
    select: (data) => data.length,
  });

  function handleCreateCollection(e: React.FormEvent) {
    e.preventDefault();
    if (newCollectionName.trim()) createCollection.mutate(newCollectionName.trim());
  }

  function handleUpdateCollection(e: React.FormEvent) {
    e.preventDefault();
    if (editingCollection && editName.trim()) {
      updateCollection.mutate({ id: editingCollection.id, name: editName.trim() });
    }
  }

  function startEdit(col: BookmarkCollection) {
    setEditingCollection(col);
    setEditName(col.name);
  }

  const selectedCollection = collections.find(c => c.id === selectedCollectionId) ?? null;

  return (
    <div className="flex h-screen overflow-hidden">
      {/* ── Sidebar ── */}
      <div className="w-56 flex-shrink-0 border-r border-gray-200 bg-gray-50 flex flex-col overflow-y-auto">
        <div className="px-4 pt-5 pb-3 border-b border-gray-200">
          <Link to="/" className="text-xs text-gray-400 hover:text-gray-600 flex items-center gap-1 mb-3">
            ← Home
          </Link>
          <h1 className="font-semibold text-gray-900 text-sm">My Bookmarks</h1>
        </div>

        <nav className="flex-1 px-2 py-3 space-y-0.5">
          {/* All Bookmarks */}
          <button
            onClick={() => setSelectedCollectionId(null)}
            className={`w-full text-left px-3 py-2 rounded-md text-sm flex items-center justify-between transition-colors ${
              selectedCollectionId === null
                ? 'bg-white text-gray-900 font-medium shadow-sm border border-gray-200'
                : 'text-gray-600 hover:bg-white hover:text-gray-900'
            }`}
          >
            <span className="flex items-center gap-2">
              <span>★</span> All Bookmarks
            </span>
            {totalCount.data !== undefined && (
              <span className="text-xs text-gray-400">{totalCount.data}</span>
            )}
          </button>

          {/* Collections */}
          {collections.map(col => (
            <div key={col.id} className="group relative">
              {editingCollection?.id === col.id ? (
                <form onSubmit={handleUpdateCollection} className="px-2 py-1">
                  <input
                    autoFocus
                    value={editName}
                    onChange={e => setEditName(e.target.value)}
                    onBlur={() => setEditingCollection(null)}
                    className="w-full text-xs border border-blue-400 rounded px-2 py-1 focus:outline-none"
                  />
                </form>
              ) : (
                <button
                  onClick={() => setSelectedCollectionId(col.id)}
                  className={`w-full text-left px-3 py-2 rounded-md text-sm flex items-center justify-between transition-colors ${
                    selectedCollectionId === col.id
                      ? 'bg-white text-gray-900 font-medium shadow-sm border border-gray-200'
                      : 'text-gray-600 hover:bg-white hover:text-gray-900'
                  }`}
                >
                  <span className="flex items-center gap-2 min-w-0">
                    <span className="text-gray-400">📁</span>
                    <span className="truncate">{col.name}</span>
                  </span>
                  <span className="hidden group-hover:flex items-center gap-0.5 flex-shrink-0">
                    <button
                      onClick={e => { e.stopPropagation(); startEdit(col); }}
                      className="text-gray-400 hover:text-gray-600 text-xs px-1"
                      title="Rename"
                    >✎</button>
                    <button
                      onClick={e => { e.stopPropagation(); if (confirm(`Delete "${col.name}"?`)) deleteCollection.mutate(col.id); }}
                      className="text-gray-400 hover:text-red-500 text-xs px-1"
                      title="Delete"
                    >✕</button>
                  </span>
                </button>
              )}
            </div>
          ))}
        </nav>

        {/* New collection */}
        <div className="px-3 pb-4">
          {showNewCollectionForm ? (
            <form onSubmit={handleCreateCollection} className="flex gap-1">
              <input
                autoFocus
                value={newCollectionName}
                onChange={e => setNewCollectionName(e.target.value)}
                onBlur={() => { if (!newCollectionName.trim()) setShowNewCollectionForm(false); }}
                placeholder="Collection name"
                className="flex-1 text-xs border border-gray-300 rounded px-2 py-1.5 focus:outline-none focus:border-blue-400"
              />
              <button
                type="submit"
                disabled={!newCollectionName.trim()}
                className="text-xs bg-blue-600 text-white rounded px-2 py-1.5 hover:bg-blue-700 disabled:opacity-50"
              >Add</button>
            </form>
          ) : (
            <button
              onClick={() => setShowNewCollectionForm(true)}
              className="w-full text-left text-xs text-gray-400 hover:text-gray-700 px-3 py-2 rounded-md hover:bg-white transition-colors flex items-center gap-1"
            >
              <span className="text-base leading-none">+</span> New collection
            </button>
          )}
        </div>
      </div>

      {/* ── Main content ── */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto px-6 py-8">
          {/* Header */}
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-gray-900">
              {selectedCollection ? selectedCollection.name : 'All Bookmarks'}
            </h2>
            <p className="text-sm text-gray-500 mt-0.5">
              {bookmarks.length} {bookmarks.length === 1 ? 'dataset' : 'datasets'}
            </p>
          </div>

          {/* Empty state */}
          {!isLoading && bookmarks.length === 0 && (
            <div className="text-center py-20 text-gray-400">
              <div className="text-5xl mb-4">☆</div>
              <p className="font-medium text-gray-500 mb-1">No bookmarks yet</p>
              <p className="text-sm">
                Star a dataset in{' '}
                <button onClick={() => navigate('/search')} className="text-blue-600 hover:underline">
                  search
                </button>{' '}
                to save it here
              </p>
            </div>
          )}

          {/* Bookmark cards */}
          {isLoading && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-28 bg-gray-100 rounded-lg animate-pulse" />
              ))}
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {bookmarks.map(bm => (
              <BookmarkCard
                key={bm.id}
                bookmark={bm}
                collections={collections}
                onOpen={() => openDataset(bm.datasetId)}
                onRemove={() => removeBookmark.mutate(bm.id)}
                onMove={(collectionId) => moveBookmark.mutate({ id: bm.id, collectionId })}
              />
            ))}
          </div>
        </div>
      </div>

      {/* Detail drawer */}
      {openDatasetId && <DatasetDetailDrawer />}
    </div>
  );
}

// ── Bookmark card ──────────────────────────────────────────────────────────────

interface BookmarkCardProps {
  bookmark: Bookmark;
  collections: BookmarkCollection[];
  onOpen: () => void;
  onRemove: () => void;
  onMove: (collectionId: string | null) => void;
}

function BookmarkCard({ bookmark, collections, onOpen, onRemove, onMove }: BookmarkCardProps) {
  const [showMoveMenu, setShowMoveMenu] = useState(false);

  return (
    <div className="relative border border-gray-200 rounded-lg bg-white p-4 hover:border-gray-300 hover:shadow-sm transition-all group">
      {/* Title — click opens drawer */}
      <button
        onClick={onOpen}
        className="w-full text-left"
      >
        <h3 className="font-medium text-gray-900 text-sm truncate pr-14">{bookmark.datasetTitle}</h3>
        {bookmark.note && (
          <p className="mt-1 text-xs text-gray-500 line-clamp-2 italic">{bookmark.note}</p>
        )}
        <p className="mt-2 text-xs text-gray-400">
          Saved {new Date(bookmark.createdAt).toLocaleDateString()}
        </p>
      </button>

      {/* Actions */}
      <div className="absolute top-3 right-3 flex items-center gap-1">
        {/* Move to collection */}
        <div className="relative">
          <button
            onClick={() => setShowMoveMenu(v => !v)}
            title="Move to collection"
            className="text-xs text-gray-300 hover:text-gray-600 px-1 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity"
          >
            📁
          </button>
          {showMoveMenu && (
            <div
              className="absolute right-0 top-6 z-20 bg-white border border-gray-200 rounded-lg shadow-lg py-1 w-48"
              onMouseLeave={() => setShowMoveMenu(false)}
            >
              <div className="px-3 py-1 text-xs font-semibold text-gray-400 uppercase tracking-wide">Move to</div>
              <button
                onClick={() => { onMove(null); setShowMoveMenu(false); }}
                className={`w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 ${!bookmark.collectionId ? 'text-blue-600 font-medium' : 'text-gray-700'}`}
              >
                ★ No collection
              </button>
              {collections.map(col => (
                <button
                  key={col.id}
                  onClick={() => { onMove(col.id); setShowMoveMenu(false); }}
                  className={`w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 truncate ${bookmark.collectionId === col.id ? 'text-blue-600 font-medium' : 'text-gray-700'}`}
                >
                  📁 {col.name}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Remove bookmark */}
        <button
          onClick={onRemove}
          title="Remove bookmark"
          className="text-xs text-gray-300 hover:text-red-500 px-1 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity"
        >
          ✕
        </button>
      </div>
    </div>
  );
}
