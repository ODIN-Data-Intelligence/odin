import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { userApi, datasetApi } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';
import Button from '../ui/Button';

const ROLE_LABELS: Record<string, string> = {
  'administrator':   'Administrator',
  'data-governance': 'Data Governance',
  'data-owner':      'Data Owner',
  'data-steward':    'Data Steward',
};

interface Props {
  datasetId: string;
  onSuccess: (updated: Dataset) => void;
  onCancel: () => void;
}

export default function AssignOwnerModal({ datasetId, onSuccess, onCancel }: Props) {
  const [search, setSearch]     = useState('');
  const [selectedId, setSelectedId] = useState('');

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
  });

  const mutation = useMutation({
    mutationFn: () => {
      const user = users.find(u => u.id === selectedId);
      const ownerId = user?.keycloakUserId ?? selectedId;
      return datasetApi.assignOwner(datasetId, ownerId);
    },
    onSuccess,
  });

  const filtered = users
    .filter(u => u.active)
    .filter(u => {
      const q = search.toLowerCase();
      return (
        u.email.toLowerCase().includes(q) ||
        (u.firstName ?? '').toLowerCase().includes(q) ||
        (u.lastName ?? '').toLowerCase().includes(q)
      );
    });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={e => e.target === e.currentTarget && onCancel()}
    >
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">Assign Data Owner</h2>
          <p className="text-xs text-gray-500 mt-0.5">
            Select a user to assign as the data owner for this dataset.
          </p>
        </div>

        <div className="p-5 space-y-3">
          <input
            type="text"
            placeholder="Search by name or email…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            autoFocus
          />

          <div className="max-h-60 overflow-y-auto divide-y divide-gray-100 border border-gray-200 rounded-lg">
            {isLoading && <p className="text-sm text-gray-400 p-3">Loading users…</p>}
            {!isLoading && filtered.length === 0 && (
              <p className="text-sm text-gray-400 p-3">No users found.</p>
            )}
            {filtered.map(u => {
              const displayName = u.firstName || u.lastName
                ? `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim()
                : null;
              return (
                <button
                  key={u.id}
                  onClick={() => setSelectedId(u.id)}
                  className={`w-full text-left px-3 py-2.5 hover:bg-gray-50 transition-colors ${
                    selectedId === u.id ? 'bg-blue-50' : ''
                  }`}
                >
                  <p className="text-sm font-medium text-gray-900">{displayName ?? u.email}</p>
                  {displayName && <p className="text-xs text-gray-500">{u.email}</p>}
                  <div className="flex flex-wrap gap-1 mt-1">
                    {u.roles.map(r => (
                      <span key={r} className="text-xs text-blue-600 font-medium">
                        {ROLE_LABELS[r] ?? r}
                      </span>
                    ))}
                  </div>
                </button>
              );
            })}
          </div>

          {mutation.isError && (
            <p className="text-xs text-red-600">Failed to assign owner. Please try again.</p>
          )}
        </div>

        <div className="px-5 py-4 border-t border-gray-100 flex items-center justify-end gap-3">
          <Button variant="secondary" size="sm" onClick={onCancel}>Cancel</Button>
          <Button
            size="sm"
            disabled={!selectedId || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? 'Assigning…' : 'Assign Owner'}
          </Button>
        </div>
      </div>
    </div>
  );
}
