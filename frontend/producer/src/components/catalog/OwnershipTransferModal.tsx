import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { userApi, datasetApi } from '@datacatalog/shared';
import type { OwnershipProposal } from '@datacatalog/shared';
import Button from '../ui/Button';

interface OwnershipTransferModalProps {
  datasetId: string;
  title?: string;
  description?: string;
  submitLabel?: string;
  onSuccess: (proposal: OwnershipProposal) => void;
  onCancel: () => void;
}

export default function OwnershipTransferModal({
  datasetId,
  title = 'Propose Ownership Transfer',
  description = 'The selected user will become owner once the current owner approves.',
  submitLabel = 'Propose Transfer',
  onSuccess,
  onCancel,
}: OwnershipTransferModalProps) {
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState('');

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
  });

  const mutation = useMutation({
    mutationFn: () => {
      const user = users.find(u => u.id === selectedId);
      const ownerId = user?.keycloakUserId ?? selectedId;
      return datasetApi.proposeTransfer(datasetId, ownerId);
    },
    onSuccess,
  });

  const filtered = users.filter(u => {
    const q = search.toLowerCase();
    return (
      u.email.toLowerCase().includes(q) ||
      (u.firstName ?? '').toLowerCase().includes(q) ||
      (u.lastName ?? '').toLowerCase().includes(q)
    );
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          <p className="text-xs text-gray-500 mt-0.5">{description}</p>
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
          <div className="max-h-56 overflow-y-auto divide-y divide-gray-100 border border-gray-200 rounded-lg">
            {isLoading && (
              <p className="text-sm text-gray-400 p-3">Loading users…</p>
            )}
            {!isLoading && filtered.length === 0 && (
              <p className="text-sm text-gray-400 p-3">No users found.</p>
            )}
            {filtered.map(u => (
              <button
                key={u.id}
                onClick={() => setSelectedId(u.id)}
                className={`w-full text-left px-3 py-2.5 hover:bg-gray-50 transition-colors ${
                  selectedId === u.id ? 'bg-blue-50' : ''
                }`}
              >
                <p className="text-sm font-medium text-gray-900">
                  {u.firstName && u.lastName ? `${u.firstName} ${u.lastName}` : u.email}
                </p>
                {(u.firstName || u.lastName) && (
                  <p className="text-xs text-gray-500">{u.email}</p>
                )}
                {u.roles.includes('DATA_OWNER') && (
                  <span className="text-xs text-purple-600 font-medium">DATA_OWNER</span>
                )}
              </button>
            ))}
          </div>
          {mutation.isError && (
            <p className="text-xs text-red-600">Failed to submit proposal. Please try again.</p>
          )}
        </div>
        <div className="px-5 py-4 border-t border-gray-100 flex items-center justify-end gap-3">
          <Button variant="secondary" size="sm" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            size="sm"
            disabled={!selectedId || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? 'Submitting…' : submitLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
