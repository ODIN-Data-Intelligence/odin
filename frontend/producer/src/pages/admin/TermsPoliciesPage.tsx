import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { termsPolicyApi } from '@datacatalog/shared';
import type { TermsPolicySet } from '@datacatalog/shared';
import { PageHeader, Button, Badge } from '@datacatalog/shared';

const STATUS_COLORS: Record<TermsPolicySet['status'], string> = {
  ACTIVE:   'bg-green-100 text-green-700',
  DRAFT:    'bg-blue-100 text-blue-700',
  ARCHIVED: 'bg-gray-100 text-gray-500',
};

function formatDate(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

interface CreateModalProps {
  onClose: () => void;
  onCreated: (id: string) => void;
}

function CreateModal({ onClose, onCreated }: CreateModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');

  const createMut = useMutation({
    mutationFn: () => termsPolicyApi.create({ name: name.trim(), description: description.trim() || undefined }),
    onSuccess: (policy) => onCreated(policy.id),
    onError: () => setError('Failed to create policy. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) { setError('Name is required.'); return; }
    setError('');
    createMut.mutate();
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">New Terms-of-Use Policy</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name *</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. Default Terms Policy"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={3}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              placeholder="Optional description"
            />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <Button type="submit" disabled={createMut.isPending}>
              {createMut.isPending ? 'Creating…' : 'Create Policy'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface CloneModalProps {
  policy: TermsPolicySet;
  onClose: () => void;
  onCloned: (id: string) => void;
}

function CloneModal({ policy, onClose, onCloned }: CloneModalProps) {
  const [name, setName] = useState(`${policy.name} (copy)`);
  const [error, setError] = useState('');

  const cloneMut = useMutation({
    mutationFn: () => termsPolicyApi.clone(policy.id, name.trim()),
    onSuccess: (cloned) => onCloned(cloned.id),
    onError: () => setError('Failed to clone policy. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) { setError('Name is required.'); return; }
    setError('');
    cloneMut.mutate();
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-1">Clone Policy</h2>
        <p className="text-sm text-gray-500 mb-4">Creates a new DRAFT based on "{policy.name}".</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">New Policy Name *</label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <Button type="submit" disabled={cloneMut.isPending}>
              {cloneMut.isPending ? 'Cloning…' : 'Clone'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function TermsPoliciesPage() {
  const { tenant } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [showCreate, setShowCreate] = useState(false);
  const [cloneTarget, setCloneTarget] = useState<TermsPolicySet | null>(null);
  const [confirmActivate, setConfirmActivate] = useState<TermsPolicySet | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<TermsPolicySet | null>(null);

  const { data: policies = [], isLoading, isError } = useQuery({
    queryKey: ['terms-policies'],
    queryFn: () => termsPolicyApi.list(),
  });

  const activateMut = useMutation({
    mutationFn: (id: string) => termsPolicyApi.activate(id),
    onSuccess: () => {
      setConfirmActivate(null);
      qc.invalidateQueries({ queryKey: ['terms-policies'] });
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => termsPolicyApi.delete(id),
    onSuccess: () => {
      setConfirmDelete(null);
      qc.invalidateQueries({ queryKey: ['terms-policies'] });
    },
  });

  function goToDetail(id: string) {
    navigate(`/${tenant}/admin/governance/terms-policies/${id}`);
  }

  return (
    <div>
      <PageHeader
        title="Terms-of-Use Policies"
        description="Manage versioned policy sets that govern dataset access terms"
        actions={<Button onClick={() => setShowCreate(true)}>+ New Policy</Button>}
      />

      <div className="p-6">
        {isLoading && <p className="text-sm text-gray-500">Loading…</p>}
        {isError && <p className="text-sm text-red-500">Failed to load policies.</p>}

        {!isLoading && !isError && (
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Name</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Status</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Version</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Effective From</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {policies.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-gray-400">
                      No policies yet. Create one to get started.
                    </td>
                  </tr>
                )}
                {policies.map(policy => (
                  <tr key={policy.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <button
                        onClick={() => goToDetail(policy.id)}
                        className="text-blue-600 hover:underline font-medium text-left"
                      >
                        {policy.name}
                      </button>
                      {policy.description && (
                        <p className="text-xs text-gray-400 mt-0.5 truncate max-w-xs">{policy.description}</p>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge label={policy.status} className={STATUS_COLORS[policy.status]} />
                    </td>
                    <td className="px-4 py-3 text-gray-600">v{policy.version}</td>
                    <td className="px-4 py-3 text-gray-500">{formatDate(policy.effectiveFrom)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {policy.status === 'DRAFT' && (
                          <>
                            <button
                              onClick={() => goToDetail(policy.id)}
                              className="text-xs text-blue-600 hover:underline"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => setConfirmActivate(policy)}
                              className="text-xs text-green-600 hover:underline"
                            >
                              Activate
                            </button>
                            <button
                              onClick={() => setConfirmDelete(policy)}
                              className="text-xs text-red-500 hover:underline"
                            >
                              Delete
                            </button>
                          </>
                        )}
                        {policy.status === 'ACTIVE' && (
                          <button
                            onClick={() => setCloneTarget(policy)}
                            className="text-xs text-purple-600 hover:underline"
                          >
                            Clone
                          </button>
                        )}
                        {policy.status === 'ARCHIVED' && (
                          <button
                            onClick={() => goToDetail(policy.id)}
                            className="text-xs text-gray-500 hover:underline"
                          >
                            View
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showCreate && (
        <CreateModal
          onClose={() => setShowCreate(false)}
          onCreated={(id) => {
            setShowCreate(false);
            qc.invalidateQueries({ queryKey: ['terms-policies'] });
            goToDetail(id);
          }}
        />
      )}

      {cloneTarget && (
        <CloneModal
          policy={cloneTarget}
          onClose={() => setCloneTarget(null)}
          onCloned={(id) => {
            setCloneTarget(null);
            qc.invalidateQueries({ queryKey: ['terms-policies'] });
            goToDetail(id);
          }}
        />
      )}

      {confirmActivate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Activate Policy</h2>
            <p className="text-sm text-gray-600 mb-4">
              Activate "<strong>{confirmActivate.name}</strong>"? The current ACTIVE policy will be archived.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmActivate(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <Button
                onClick={() => activateMut.mutate(confirmActivate.id)}
                disabled={activateMut.isPending}
              >
                {activateMut.isPending ? 'Activating…' : 'Activate'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Delete Policy</h2>
            <p className="text-sm text-gray-600 mb-4">
              Delete "<strong>{confirmDelete.name}</strong>"? This cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDelete(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button
                onClick={() => deleteMut.mutate(confirmDelete.id)}
                disabled={deleteMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deleteMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
