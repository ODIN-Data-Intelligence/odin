import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Collapse from '@mui/material/Collapse';
import { datasetApi, JsonDiffView } from '@datacatalog/shared';
import type { DatasetAuditEntry } from '@datacatalog/shared';

const EVENT_COLORS: Record<string, 'success' | 'primary' | 'error' | 'secondary' | 'warning' | 'info' | 'default'> = {
  CREATED:                   'success',
  UPDATED:                   'primary',
  DELETED:                   'error',
  OWNER_ASSIGNED:            'secondary',
  OWNER_TRANSFER_PROPOSED:   'warning',
  OWNER_TRANSFER_APPROVED:   'info',
  OWNER_TRANSFER_REJECTED:   'default',
};

function formatTs(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function AuditEntryRow({ entry }: { entry: DatasetAuditEntry }) {
  const [expanded, setExpanded] = useState(false);
  const hasDiff = entry.payloadBefore != null || entry.payloadAfter != null;
  const color = EVENT_COLORS[entry.eventType] ?? 'default';

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Box
        component={hasDiff ? 'button' : 'div'}
        onClick={hasDiff ? () => setExpanded(e => !e) : undefined}
        sx={{
          width: '100%', display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 1.5,
          textAlign: 'left', bgcolor: 'transparent', border: 'none',
          cursor: hasDiff ? 'pointer' : 'default',
          '&:hover': hasDiff ? { bgcolor: 'grey.50' } : {},
        }}
      >
        <Chip
          label={entry.eventType.replace(/_/g, ' ')}
          color={color}
          size="small"
          sx={{ height: 20, fontSize: 11, fontWeight: 600, flexShrink: 0 }}
        />
        <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
          {entry.changedByEmail ?? entry.changedById ?? 'system'}
        </Typography>
        <Typography variant="caption" color="text.disabled">{formatTs(entry.createdAt)}</Typography>
        {hasDiff && (
          <Typography variant="caption" color="text.disabled">{expanded ? '▲' : '▼'}</Typography>
        )}
      </Box>
      {hasDiff && (
        <Collapse in={expanded}>
          <Box sx={{ px: 2, pb: 2, borderTop: 1, borderColor: 'divider' }}>
            <JsonDiffView before={entry.payloadBefore} after={entry.payloadAfter} />
          </Box>
        </Collapse>
      )}
    </Paper>
  );
}

interface Props {
  datasetId: string;
}

export default function DatasetHistoryTab({ datasetId }: Props) {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['dataset-history', datasetId, page],
    queryFn: () => datasetApi.getHistory(datasetId, page, 20),
  });

  if (isLoading) return <Typography variant="body2" color="text.secondary">Loading history…</Typography>;
  if (!data || data.content.length === 0) {
    return <Typography variant="body2" color="text.secondary">No audit history yet.</Typography>;
  }

  return (
    <Box sx={{ maxWidth: 800, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Typography variant="caption" color="text.disabled">{data.totalElements} total entries</Typography>
      {data.content.map(entry => <AuditEntryRow key={entry.id} entry={entry} />)}
      {data.totalPages > 1 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, pt: 1 }}>
          <Button size="small" variant="outlined" disabled={page === 0} onClick={() => setPage(p => p - 1)} sx={{ textTransform: 'none' }}>
            ← Newer
          </Button>
          <Typography variant="caption" color="text.secondary">Page {page + 1} of {data.totalPages}</Typography>
          <Button size="small" variant="outlined" disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)} sx={{ textTransform: 'none' }}>
            Older →
          </Button>
        </Box>
      )}
    </Box>
  );
}
