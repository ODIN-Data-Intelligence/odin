import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import { datasetApi, JsonDiffView } from '@datacatalog/shared';
import type { DatasetAuditEntry, LogicalElementAuditEntry } from '@datacatalog/shared';

const DATASET_EVENT_COLORS: Record<string, 'success' | 'primary' | 'error' | 'secondary' | 'warning' | 'info' | 'default'> = {
  CREATED:                   'success',
  UPDATED:                   'primary',
  DELETED:                   'error',
  OWNER_ASSIGNED:            'secondary',
  OWNER_TRANSFER_PROPOSED:   'warning',
  OWNER_TRANSFER_APPROVED:   'info',
  OWNER_TRANSFER_REJECTED:   'default',
};

const ELEMENT_EVENT_COLORS: Record<string, 'success' | 'primary' | 'error' | 'secondary' | 'warning' | 'info' | 'default'> = {
  UPDATED:                    'primary',
  CLASSIFICATION_ACCEPTED:    'success',
  CLASSIFICATION_REJECTED:    'error',
  DESCRIPTION_ACCEPTED:       'success',
  DESCRIPTION_REJECTED:       'error',
  VOCAB_MAPPING_ADDED:        'info',
  VOCAB_MAPPING_DELETED:      'warning',
  VOCAB_CONCEPTS_ACCEPTED:    'success',
  VOCAB_CONCEPTS_REJECTED:    'error',
  PII_ACCEPTED:               'secondary',
  PII_REJECTED:               'default',
};

function formatTs(iso: string) {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function AuditEntryRow({ entry }: { entry: DatasetAuditEntry }) {
  const [expanded, setExpanded] = useState(false);
  const hasDiff = entry.payloadBefore != null || entry.payloadAfter != null;
  const color = DATASET_EVENT_COLORS[entry.eventType] ?? 'default';

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

function ElementAuditEntryRow({ entry }: { entry: LogicalElementAuditEntry }) {
  const [expanded, setExpanded] = useState(false);
  const hasDiff = entry.payloadBefore != null || entry.payloadAfter != null;
  const color = ELEMENT_EVENT_COLORS[entry.eventType] ?? 'default';

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
        {entry.elementName && (
          <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.primary', flexShrink: 0 }}>
            {entry.elementName}
          </Typography>
        )}
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
  const [elementPage, setElementPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['dataset-history', datasetId, page],
    queryFn: () => datasetApi.getHistory(datasetId, page, 20),
  });

  const { data: elementData, isLoading: elementLoading } = useQuery({
    queryKey: ['dataset-element-history', datasetId, elementPage],
    queryFn: () => datasetApi.getElementHistory(datasetId, elementPage, 20),
  });

  return (
    <Box sx={{ maxWidth: 800, display: 'flex', flexDirection: 'column', gap: 3 }}>
      {/* Dataset events section */}
      <Box>
        <Divider textAlign="left" sx={{ mb: 2 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Dataset events
          </Typography>
        </Divider>
        {isLoading ? (
          <Typography variant="body2" color="text.secondary">Loading history…</Typography>
        ) : !data || data.content.length === 0 ? (
          <Typography variant="body2" color="text.secondary">No dataset events yet.</Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
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
        )}
      </Box>

      {/* Element changes section */}
      <Box>
        <Divider textAlign="left" sx={{ mb: 2 }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 600, textTransform: 'uppercase', letterSpacing: 0.5 }}>
            Element changes
          </Typography>
        </Divider>
        {elementLoading ? (
          <Typography variant="body2" color="text.secondary">Loading element history…</Typography>
        ) : !elementData || elementData.content.length === 0 ? (
          <Typography variant="body2" color="text.secondary">No element changes yet.</Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            <Typography variant="caption" color="text.disabled">{elementData.totalElements} total entries</Typography>
            {elementData.content.map(entry => <ElementAuditEntryRow key={entry.id} entry={entry} />)}
            {elementData.totalPages > 1 && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, pt: 1 }}>
                <Button size="small" variant="outlined" disabled={elementPage === 0} onClick={() => setElementPage(p => p - 1)} sx={{ textTransform: 'none' }}>
                  ← Newer
                </Button>
                <Typography variant="caption" color="text.secondary">Page {elementPage + 1} of {elementData.totalPages}</Typography>
                <Button size="small" variant="outlined" disabled={elementPage >= elementData.totalPages - 1} onClick={() => setElementPage(p => p + 1)} sx={{ textTransform: 'none' }}>
                  Older →
                </Button>
              </Box>
            )}
          </Box>
        )}
      </Box>
    </Box>
  );
}
