import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import ListItemIcon from '@mui/material/ListItemIcon';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Skeleton from '@mui/material/Skeleton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import BookmarksIcon from '@mui/icons-material/Bookmarks';
import FolderIcon from '@mui/icons-material/Folder';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import DriveFileMoveIcon from '@mui/icons-material/DriveFileMove';
import CloseIcon from '@mui/icons-material/Close';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
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

  const totalCount = useQuery({
    queryKey: ['bookmarks', null],
    queryFn: () => bookmarkApi.list(),
    staleTime: 30_000,
    select: (data) => data.length,
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
    mutationFn: ({ id, name }: { id: string; name: string }) => bookmarkCollectionApi.update(id, { name }),
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
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['bookmarks'] });
      qc.invalidateQueries({ queryKey: ['bookmark-check'] });
    },
  });

  const moveBookmark = useMutation({
    mutationFn: ({ id, collectionId }: { id: string; collectionId: string | null }) =>
      bookmarkApi.patch(id, { collectionId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bookmarks'] }),
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
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* Sidebar */}
      <Paper
        square
        elevation={0}
        sx={{ width: 224, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'grey.50', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}
      >
        <Box sx={{ px: 2, pt: 2.5, pb: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <Button
            component={Link}
            to="/"
            startIcon={<ArrowBackIcon fontSize="small" />}
            size="small"
            sx={{ fontSize: 12, mb: 1.5, textTransform: 'none', color: 'text.secondary' }}
          >
            Home
          </Button>
          <Typography variant="subtitle2" fontWeight={700}>My Bookmarks</Typography>
        </Box>

        <List dense sx={{ flex: 1, px: 1, py: 1 }}>
          <ListItemButton
            selected={selectedCollectionId === null}
            onClick={() => setSelectedCollectionId(null)}
            sx={{ borderRadius: 1, mb: 0.25 }}
          >
            <ListItemIcon sx={{ minWidth: 28 }}>
              <BookmarksIcon fontSize="small" color="warning" />
            </ListItemIcon>
            <ListItemText primary="All Bookmarks" primaryTypographyProps={{ variant: 'body2' }} />
            {totalCount.data !== undefined && (
              <Typography variant="caption" color="text.secondary">{totalCount.data}</Typography>
            )}
          </ListItemButton>

          {collections.map(col => (
            <Box key={col.id} sx={{ '&:hover .col-actions': { opacity: 1 } }}>
              {editingCollection?.id === col.id ? (
                <Box component="form" onSubmit={handleUpdateCollection} sx={{ px: 1, py: 0.5 }}>
                  <TextField
                    autoFocus
                    value={editName}
                    onChange={e => setEditName(e.target.value)}
                    onBlur={() => setEditingCollection(null)}
                    size="small"
                    variant="outlined"
                    fullWidth
                    inputProps={{ style: { fontSize: 13, padding: '4px 8px' } }}
                  />
                </Box>
              ) : (
                <ListItemButton
                  selected={selectedCollectionId === col.id}
                  onClick={() => setSelectedCollectionId(col.id)}
                  sx={{ borderRadius: 1, mb: 0.25 }}
                >
                  <ListItemIcon sx={{ minWidth: 28 }}>
                    <FolderIcon fontSize="small" color="action" />
                  </ListItemIcon>
                  <ListItemText primary={col.name} primaryTypographyProps={{ variant: 'body2', noWrap: true }} />
                  <Box className="col-actions" sx={{ display: 'flex', opacity: 0, transition: 'opacity 0.15s' }}>
                    <Tooltip title="Rename">
                      <IconButton size="small" onClick={e => { e.stopPropagation(); startEdit(col); }} sx={{ p: 0.25 }}>
                        <EditIcon sx={{ fontSize: 14 }} />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={e => { e.stopPropagation(); if (confirm(`Delete "${col.name}"?`)) deleteCollection.mutate(col.id); }}
                        sx={{ p: 0.25 }}
                      >
                        <DeleteIcon sx={{ fontSize: 14 }} />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </ListItemButton>
              )}
            </Box>
          ))}
        </List>

        <Box sx={{ px: 2, pb: 2 }}>
          {showNewCollectionForm ? (
            <Box component="form" onSubmit={handleCreateCollection} sx={{ display: 'flex', gap: 0.75 }}>
              <TextField
                autoFocus
                value={newCollectionName}
                onChange={e => setNewCollectionName(e.target.value)}
                onBlur={() => { if (!newCollectionName.trim()) setShowNewCollectionForm(false); }}
                placeholder="Collection name"
                size="small"
                variant="outlined"
                fullWidth
                inputProps={{ style: { fontSize: 13, padding: '4px 8px' } }}
              />
              <Button
                type="submit"
                variant="contained"
                size="small"
                disabled={!newCollectionName.trim()}
                sx={{ flexShrink: 0, px: 1, minWidth: 0 }}
              >
                Add
              </Button>
            </Box>
          ) : (
            <Button
              startIcon={<AddIcon />}
              size="small"
              fullWidth
              onClick={() => setShowNewCollectionForm(true)}
              sx={{ justifyContent: 'flex-start', textTransform: 'none', fontSize: 13, color: 'text.secondary' }}
            >
              New collection
            </Button>
          )}
        </Box>
      </Paper>

      {/* Main content */}
      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <Box sx={{ maxWidth: 800, mx: 'auto', px: 3, py: 4 }}>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h6" fontWeight={600}>
              {selectedCollection ? selectedCollection.name : 'All Bookmarks'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {bookmarks.length} {bookmarks.length === 1 ? 'dataset' : 'datasets'}
            </Typography>
          </Box>

          {!isLoading && bookmarks.length === 0 && (
            <Box sx={{ textAlign: 'center', py: 15 }}>
              <Typography variant="h2" color="text.disabled" sx={{ mb: 2 }}>☆</Typography>
              <Typography variant="subtitle1" color="text.secondary" gutterBottom fontWeight={500}>
                No bookmarks yet
              </Typography>
              <Typography variant="body2" color="text.disabled">
                Star a dataset in{' '}
                <Typography
                  component="span"
                  variant="body2"
                  color="primary"
                  sx={{ cursor: 'pointer', '&:hover': { textDecoration: 'underline' } }}
                  onClick={() => navigate('/search')}
                >
                  search
                </Typography>
                {' '}to save it here
              </Typography>
            </Box>
          )}

          {isLoading && (
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              {[...Array(4)].map((_, i) => <Skeleton key={i} variant="rounded" height={112} />)}
            </Box>
          )}

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
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
          </Box>
        </Box>
      </Box>

      {openDatasetId && <DatasetDetailDrawer />}
    </Box>
  );
}

interface BookmarkCardProps {
  bookmark: Bookmark;
  collections: BookmarkCollection[];
  onOpen: () => void;
  onRemove: () => void;
  onMove: (collectionId: string | null) => void;
}

function BookmarkCard({ bookmark, collections, onOpen, onRemove, onMove }: BookmarkCardProps) {
  const [moveAnchorEl, setMoveAnchorEl] = useState<HTMLElement | null>(null);

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        cursor: 'pointer',
        '&:hover': { borderColor: 'primary.light', boxShadow: 1 },
        transition: 'border-color 0.15s, box-shadow 0.15s',
        position: 'relative',
      }}
    >
      <Box onClick={onOpen} sx={{ pr: 8 }}>
        <Typography variant="body2" fontWeight={600} noWrap>{bookmark.datasetTitle}</Typography>
        {bookmark.note && (
          <Typography
            variant="caption"
            color="text.secondary"
            fontStyle="italic"
            sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', mt: 0.5 }}
          >
            {bookmark.note}
          </Typography>
        )}
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 1 }}>
          Saved {new Date(bookmark.createdAt).toLocaleDateString()}
        </Typography>
      </Box>

      <Box sx={{ position: 'absolute', top: 8, right: 8, display: 'flex', gap: 0.25 }}>
        <Tooltip title="Move to collection">
          <IconButton
            size="small"
            onClick={e => setMoveAnchorEl(e.currentTarget)}
            sx={{ opacity: 0.4, '&:hover': { opacity: 1 }, p: 0.5 }}
          >
            <DriveFileMoveIcon sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
        <Tooltip title="Remove bookmark">
          <IconButton
            size="small"
            color="error"
            onClick={onRemove}
            sx={{ opacity: 0.4, '&:hover': { opacity: 1 }, p: 0.5 }}
          >
            <CloseIcon sx={{ fontSize: 16 }} />
          </IconButton>
        </Tooltip>
      </Box>

      <Menu
        anchorEl={moveAnchorEl}
        open={!!moveAnchorEl}
        onClose={() => setMoveAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 0.75, display: 'block', textTransform: 'uppercase', letterSpacing: 0.5, fontWeight: 600 }}>
          Move to
        </Typography>
        <MenuItem
          onClick={() => { onMove(null); setMoveAnchorEl(null); }}
          selected={!bookmark.collectionId}
          dense
        >
          ★ No collection
        </MenuItem>
        <Divider />
        {collections.map(col => (
          <MenuItem
            key={col.id}
            onClick={() => { onMove(col.id); setMoveAnchorEl(null); }}
            selected={bookmark.collectionId === col.id}
            dense
          >
            <FolderIcon fontSize="small" sx={{ mr: 1 }} /> {col.name}
          </MenuItem>
        ))}
      </Menu>
    </Paper>
  );
}
