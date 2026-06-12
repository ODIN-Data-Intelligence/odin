import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import { userApi, PageHeader } from '@datacatalog/shared';
import type { User } from '@datacatalog/shared';
import UserInviteForm from '../../components/admin/UserInviteForm';

const ROLE_COLORS: Record<string, 'error' | 'secondary' | 'primary' | 'success'> = {
  'administrator':   'error',
  'data-governance': 'secondary',
  'data-owner':      'primary',
  'data-steward':    'success',
};

function RoleBadge({ role }: { role: string }) {
  const label = role.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  return <Chip label={label} color={ROLE_COLORS[role] ?? 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />;
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
    onSuccess: () => { setConfirmDeactivate(null); qc.invalidateQueries({ queryKey: ['users'] }); },
  });

  const active   = users.filter(u => u.active);
  const inactive = users.filter(u => !u.active);

  return (
    <Box>
      <PageHeader
        title="Users"
        description={`${active.length} active${inactive.length > 0 ? `, ${inactive.length} inactive` : ''}`}
        actions={
          <Button variant="contained" size="small" onClick={() => setShowInvite(true)} sx={{ textTransform: 'none' }}>
            Invite User
          </Button>
        }
      />

      <Box sx={{ p: 3 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {[...Array(4)].map((_, i) => <Skeleton key={i} height={52} variant="rounded" />)}
          </Box>
        )}
        {isError && <Alert severity="error">Failed to load users.</Alert>}

        {!isLoading && !isError && (
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>User</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Roles</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Status</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Invited</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ textAlign: 'center', py: 6, color: 'text.disabled' }}>
                      No users yet. Invite your first team member.
                    </TableCell>
                  </TableRow>
                )}
                {users.map(user => (
                  <TableRow key={user.id} sx={{ opacity: user.active ? 1 : 0.6 }} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {user.firstName || user.lastName
                          ? `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim()
                          : '—'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">{user.email}</Typography>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {user.roles.length > 0
                          ? user.roles.map(r => <RoleBadge key={r} role={r} />)
                          : <Typography variant="caption" color="text.disabled">No roles</Typography>}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                        <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: user.active ? 'success.main' : 'grey.400' }} />
                        <Typography variant="caption" color={user.active ? 'success.main' : 'text.disabled'} fontWeight={500}>
                          {user.active ? 'Active' : 'Inactive'}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {user.createdAt ? new Date(user.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ textAlign: 'right' }}>
                      {user.active ? (
                        <Button size="small" color="error" sx={{ textTransform: 'none', minWidth: 'auto', fontSize: 12 }} onClick={() => setConfirmDeactivate(user)}>
                          Deactivate
                        </Button>
                      ) : (
                        <Button size="small" color="primary" sx={{ textTransform: 'none', minWidth: 'auto', fontSize: 12 }} onClick={() => activateMut.mutate(user.id)} disabled={activateMut.isPending}>
                          {activateMut.isPending ? 'Activating…' : 'Activate'}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        )}
      </Box>

      {showInvite && <UserInviteForm onClose={() => setShowInvite(false)} />}

      <Dialog open={!!confirmDeactivate} onClose={() => setConfirmDeactivate(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Deactivate user?</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            <strong>{confirmDeactivate?.email}</strong> will lose access immediately. You can reactivate their account at any time.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDeactivate(null)} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            disabled={deactivateMut.isPending}
            onClick={() => confirmDeactivate && deactivateMut.mutate(confirmDeactivate.id)}
            sx={{ textTransform: 'none' }}
          >
            {deactivateMut.isPending ? 'Deactivating…' : 'Deactivate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
