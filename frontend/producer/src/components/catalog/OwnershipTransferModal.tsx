import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Paper from '@mui/material/Paper';
import Chip from '@mui/material/Chip';
import { userApi, datasetApi } from '@datacatalog/shared';
import type { OwnershipProposal } from '@datacatalog/shared';

interface Props {
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
}: Props) {
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
    <Dialog open onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle>
        {title}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>{description}</Typography>
      </DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, pt: '8px !important' }}>
        <TextField
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Search by name or email…"
          size="small"
          fullWidth
          autoFocus
        />
        <Paper variant="outlined" sx={{ maxHeight: 224, overflowY: 'auto' }}>
          {isLoading && <Typography variant="body2" color="text.secondary" sx={{ p: 1.5 }}>Loading users…</Typography>}
          {!isLoading && filtered.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ p: 1.5 }}>No users found.</Typography>
          )}
          {filtered.map(u => {
            const displayName = u.firstName && u.lastName ? `${u.firstName} ${u.lastName}` : null;
            return (
              <Box
                key={u.id}
                component="button"
                onClick={() => setSelectedId(u.id)}
                sx={{
                  width: '100%', textAlign: 'left', px: 1.5, py: 1.25,
                  bgcolor: selectedId === u.id ? 'primary.50' : 'transparent',
                  border: 'none', cursor: 'pointer', display: 'block',
                  borderBottom: 1, borderColor: 'divider',
                  '&:hover': { bgcolor: selectedId === u.id ? 'primary.50' : 'grey.50' },
                  '&:last-child': { borderBottom: 'none' },
                }}
              >
                <Typography variant="body2" fontWeight={600}>{displayName ?? u.email}</Typography>
                {displayName && <Typography variant="caption" color="text.secondary">{u.email}</Typography>}
                {u.roles.includes('DATA_OWNER') && (
                  <Chip label="DATA_OWNER" size="small" color="secondary" sx={{ ml: 0.5, height: 16, fontSize: 10 }} />
                )}
              </Box>
            );
          })}
        </Paper>
        {mutation.isError && <Alert severity="error">Failed to submit proposal. Please try again.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} sx={{ textTransform: 'none' }}>Cancel</Button>
        <Button variant="contained" disabled={!selectedId || mutation.isPending} onClick={() => mutation.mutate()} sx={{ textTransform: 'none' }}>
          {mutation.isPending ? 'Submitting…' : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
