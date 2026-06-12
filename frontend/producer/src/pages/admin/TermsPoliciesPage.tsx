import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
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
import TextField from '@mui/material/TextField';
import Alert from '@mui/material/Alert';
import { termsPolicyApi, PageHeader } from '@datacatalog/shared';
import type { TermsPolicySet } from '@datacatalog/shared';

const STATUS_COLORS: Record<TermsPolicySet['status'], 'success' | 'info' | 'default'> = {
  ACTIVE:   'success',
  DRAFT:    'info',
  ARCHIVED: 'default',
};

function formatDate(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

interface CreateModalProps { onClose: () => void; onCreated: (id: string) => void; }

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
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>New Terms-of-Use Policy</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <TextField label="Name" required value={name} onChange={e => setName(e.target.value)} size="small" placeholder="e.g. Default Terms Policy" fullWidth />
          <TextField label="Description" value={description} onChange={e => setDescription(e.target.value)} size="small" placeholder="Optional description" multiline rows={3} fullWidth />
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={createMut.isPending} sx={{ textTransform: 'none' }}>
            {createMut.isPending ? 'Creating…' : 'Create Policy'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

interface CloneModalProps { policy: TermsPolicySet; onClose: () => void; onCloned: (id: string) => void; }

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
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Clone Policy</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <Typography variant="body2" color="text.secondary">Creates a new DRAFT based on "{policy.name}".</Typography>
          <TextField label="New Policy Name" required value={name} onChange={e => setName(e.target.value)} size="small" fullWidth />
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={cloneMut.isPending} sx={{ textTransform: 'none' }}>
            {cloneMut.isPending ? 'Cloning…' : 'Clone'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
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
    onSuccess: () => { setConfirmActivate(null); qc.invalidateQueries({ queryKey: ['terms-policies'] }); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => termsPolicyApi.delete(id),
    onSuccess: () => { setConfirmDelete(null); qc.invalidateQueries({ queryKey: ['terms-policies'] }); },
  });

  function goToDetail(id: string) {
    navigate(`/${tenant}/admin/governance/terms-policies/${id}`);
  }

  return (
    <Box>
      <PageHeader
        title="Terms-of-Use Policies"
        description="Manage versioned policy sets that govern dataset access terms"
        actions={
          <Button variant="contained" size="small" onClick={() => setShowCreate(true)} sx={{ textTransform: 'none' }}>
            + New Policy
          </Button>
        }
      />

      <Box sx={{ p: 3 }}>
        {isLoading && <Typography variant="body2" color="text.secondary">Loading…</Typography>}
        {isError && <Alert severity="error">Failed to load policies.</Alert>}

        {!isLoading && !isError && (
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Name</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Status</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Version</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Effective From</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {policies.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ textAlign: 'center', py: 6, color: 'text.disabled' }}>
                      No policies yet. Create one to get started.
                    </TableCell>
                  </TableRow>
                )}
                {policies.map(policy => (
                  <TableRow key={policy.id} hover>
                    <TableCell>
                      <Button variant="text" size="small" onClick={() => goToDetail(policy.id)} sx={{ textTransform: 'none', fontWeight: 600, p: 0, minWidth: 0 }}>
                        {policy.name}
                      </Button>
                      {policy.description && (
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 240, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {policy.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip label={policy.status} color={STATUS_COLORS[policy.status]} size="small" sx={{ height: 18, fontSize: 11 }} />
                    </TableCell>
                    <TableCell><Typography variant="body2" color="text.secondary">v{policy.version}</Typography></TableCell>
                    <TableCell><Typography variant="body2" color="text.secondary">{formatDate(policy.effectiveFrom)}</Typography></TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        {policy.status === 'DRAFT' && (
                          <>
                            <Button size="small" sx={{ textTransform: 'none', fontSize: 12, minWidth: 0, p: '2px 6px' }} onClick={() => goToDetail(policy.id)}>Edit</Button>
                            <Button size="small" color="success" sx={{ textTransform: 'none', fontSize: 12, minWidth: 0, p: '2px 6px' }} onClick={() => setConfirmActivate(policy)}>Activate</Button>
                            <Button size="small" color="error" sx={{ textTransform: 'none', fontSize: 12, minWidth: 0, p: '2px 6px' }} onClick={() => setConfirmDelete(policy)}>Delete</Button>
                          </>
                        )}
                        {policy.status === 'ACTIVE' && (
                          <Button size="small" color="secondary" sx={{ textTransform: 'none', fontSize: 12, minWidth: 0, p: '2px 6px' }} onClick={() => setCloneTarget(policy)}>Clone</Button>
                        )}
                        {policy.status === 'ARCHIVED' && (
                          <Button size="small" sx={{ textTransform: 'none', fontSize: 12, minWidth: 0, p: '2px 6px' }} onClick={() => goToDetail(policy.id)}>View</Button>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        )}
      </Box>

      {showCreate && (
        <CreateModal
          onClose={() => setShowCreate(false)}
          onCreated={(id) => { setShowCreate(false); qc.invalidateQueries({ queryKey: ['terms-policies'] }); goToDetail(id); }}
        />
      )}

      {cloneTarget && (
        <CloneModal
          policy={cloneTarget}
          onClose={() => setCloneTarget(null)}
          onCloned={(id) => { setCloneTarget(null); qc.invalidateQueries({ queryKey: ['terms-policies'] }); goToDetail(id); }}
        />
      )}

      <Dialog open={!!confirmActivate} onClose={() => setConfirmActivate(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Activate Policy</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Activate "<strong>{confirmActivate?.name}</strong>"? The current ACTIVE policy will be archived.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmActivate(null)} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button variant="contained" color="success" disabled={activateMut.isPending} onClick={() => confirmActivate && activateMut.mutate(confirmActivate.id)} sx={{ textTransform: 'none' }}>
            {activateMut.isPending ? 'Activating…' : 'Activate'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={!!confirmDelete} onClose={() => setConfirmDelete(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete Policy</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Delete "<strong>{confirmDelete?.name}</strong>"? This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button variant="contained" color="error" disabled={deleteMut.isPending} onClick={() => confirmDelete && deleteMut.mutate(confirmDelete.id)} sx={{ textTransform: 'none' }}>
            {deleteMut.isPending ? 'Deleting…' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
