import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi } from '@datacatalog/shared';
import type { User } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';
import UserInviteForm from '../../components/admin/UserInviteForm';

const ROLE_STYLES: Record<string, string> = {
  'administrator':    'bg-red-100 text-red-700',
  'data-governance':  'bg-purple-100 text-purple-700',
  'data-owner':       'bg-blue-100 text-blue-700',
  'data-steward':     'bg-green-100 text-green-700',
};

function RoleBadge({ role }: { role: string }) {
  const className = ROLE_STYLES[role] ?? 'bg-gray-100 text-gray-600';
  const label = role.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  return <Badge label={label} className={className} />;
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export default function UsersPage() {
  const qc = useQueryClient();
  const [showInvite, setShowInvite] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState<User | null>(null);

  const { data: users = [], isLoading, isError } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
  });

  const activateMut = useMutation({
    mutationFn: (id: string) => userApi.activate(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  });

  const deactivateMut = useMutation({
    mutationFn: (id: string) => userApi.deactivate(id),
    onSuccess: () => {
      setConfirmDeactivate(null);
      qc.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const active   = users.filter(u => u.active);
  const inactive = users.filter(u => !u.active);

  return (
    <div>
      <PageHeader
        title="Users"
        description={`${active.length} active${inactive.length > 0 ? `, ${inactive.length} inactive` : ''}`}
        actions={<Button onClick={() => setShowInvite(true)}>Invite User</Button>}
      />

      <div className="p-6 space-y-6">
        {isLoading && (
          <p className="text-sm text-gray-400 text-center py-8">Loading users…</p>
        )}
        {isError && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-4 py-3">
            Failed to load users.
          </p>
        )}

        {!isLoading && !isError && (
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">User</th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Roles</th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-5 py-3 text-left text-xs font-medium text-gray-500 uppercase">Invited</th>
                  <th className="px-5 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {users.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-10 text-center text-gray-400">
                      No users yet. Invite your first team member.
                    </td>
                  </tr>
                )}
                {users.map(user => (
                  <tr key={user.id} className={`hover:bg-gray-50 ${!user.active ? 'opacity-60' : ''}`}>
                    <td className="px-5 py-3.5">
                      <p className="font-medium text-gray-900">
                        {user.firstName || user.lastName
                          ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim()
                          : '—'}
                      </p>
                      <p className="text-xs text-gray-500">{user.email}</p>
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.length > 0
                          ? user.roles.map(r => <RoleBadge key={r} role={r} />)
                          : <span className="text-gray-400 text-xs">No roles</span>}
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      {user.active
                        ? <span className="inline-flex items-center gap-1.5 text-xs font-medium text-green-700">
                            <span className="h-1.5 w-1.5 rounded-full bg-green-500 inline-block" />
                            Active
                          </span>
                        : <span className="inline-flex items-center gap-1.5 text-xs font-medium text-gray-400">
                            <span className="h-1.5 w-1.5 rounded-full bg-gray-300 inline-block" />
                            Inactive
                          </span>}
                    </td>
                    <td className="px-5 py-3.5 text-xs text-gray-500">
                      {user.createdAt ? formatDate(user.createdAt) : '—'}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {user.active ? (
                        <button
                          onClick={() => setConfirmDeactivate(user)}
                          className="text-xs text-red-500 hover:text-red-700"
                        >
                          Deactivate
                        </button>
                      ) : (
                        <button
                          onClick={() => activateMut.mutate(user.id)}
                          disabled={activateMut.isPending}
                          className="text-xs text-blue-600 hover:text-blue-800 disabled:opacity-50"
                        >
                          {activateMut.isPending ? 'Activating…' : 'Activate'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showInvite && <UserInviteForm onClose={() => setShowInvite(false)} />}

      {confirmDeactivate && (
        <div
          className="fixed inset-0 bg-black/40 flex items-center justify-center z-50"
          onClick={e => e.target === e.currentTarget && setConfirmDeactivate(null)}
        >
          <div className="bg-white rounded-xl shadow-2xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-2">Deactivate user?</h2>
            <p className="text-sm text-gray-600 mb-5">
              <strong>{confirmDeactivate.email}</strong> will lose access immediately. You can
              reactivate their account at any time.
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setConfirmDeactivate(null)}
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={() => deactivateMut.mutate(confirmDeactivate.id)}
                disabled={deactivateMut.isPending}
                className="px-4 py-2 text-sm text-white bg-red-600 rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deactivateMut.isPending ? 'Deactivating…' : 'Deactivate'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
