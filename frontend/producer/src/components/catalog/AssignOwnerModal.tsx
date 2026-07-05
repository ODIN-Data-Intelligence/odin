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
import { userApi, datasetApi } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';

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
    <Dialog open onClose={onCancel} maxWidth="sm" fullWidth>
      <DialogTitle>
        Assign Data Owner
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
          Select a user to assign as the data owner for this dataset.
        </Typography>
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
        <Paper variant="outlined" sx={{ maxHeight: 240, overflowY: 'auto' }}>
          {isLoading && <Typography variant="body2" color="text.secondary" sx={{ p: 1.5 }}>Loading users…</Typography>}
          {!isLoading && filtered.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ p: 1.5 }}>No users found.</Typography>
          )}
          {filtered.map(u => {
            const displayName = u.firstName || u.lastName
              ? `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim()
              : null;
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
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.25 }}>
                  {u.roles.map(r => (
                    <Typography key={r} variant="caption" color="primary" fontWeight={500}>{ROLE_LABELS[r] ?? r}</Typography>
                  ))}
                </Box>
              </Box>
            );
          })}
        </Paper>
        {mutation.isError && <Alert severity="error">Failed to assign owner. Please try again.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} sx={{ textTransform: 'none' }}>Cancel</Button>
        <Button variant="contained" disabled={!selectedId || mutation.isPending} onClick={() => mutation.mutate()} sx={{ textTransform: 'none' }}>
          {mutation.isPending ? 'Assigning…' : 'Assign Owner'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
