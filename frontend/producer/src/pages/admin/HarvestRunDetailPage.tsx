import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Alert from '@mui/material/Alert';
import { harvestRunApi, PageHeader } from '@datacatalog/shared';
import type { HarvestRun } from '@datacatalog/shared';
import { formatDateTime, RUN_STATUS_COLORS } from '../../lib/utils';

export default function HarvestRunDetailPage() {
  const { id } = useParams();

  const { data: run } = useQuery({
    queryKey: ['harvest-run', id],
    queryFn: () => harvestRunApi.get(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = (query.state.data as HarvestRun | undefined)?.status;
      return status === 'running' || status === 'pending' ? 3000 : false;
    },
  });

  const { data: items = [] } = useQuery({
    queryKey: ['harvest-run-items', id],
    queryFn: () => harvestRunApi.listItems(id!),
    enabled: !!id && run?.status === 'completed',
  });

  if (!run) return <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>Loading...</Typography>;

  const progress = run.entitiesDiscovered
    ? Math.round(((run.entitiesCreated ?? 0) + (run.entitiesUpdated ?? 0)) / run.entitiesDiscovered * 100)
    : 0;

  return (
    <Box>
      <PageHeader
        title={`Run ${run.id.slice(0, 8)}…`}
        description={`Triggered by: ${run.triggeredBy ?? 'system'}`}
        actions={
          <Chip label={run.status} color={RUN_STATUS_COLORS[run.status] ?? 'default'} size="small" sx={{ height: 22, fontSize: 12 }} />
        }
      />

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
        {run.status === 'running' && (
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="caption" color="text.secondary">Progress</Typography>
              <Typography variant="caption" color="text.secondary">{progress}%</Typography>
            </Box>
            <LinearProgress variant="determinate" value={progress} sx={{ borderRadius: 1 }} />
          </Box>
        )}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' }, gap: 2 }}>
          <StatCard label="Discovered" value={run.entitiesDiscovered ?? 0} color="text.primary" />
          <StatCard label="Created" value={run.entitiesCreated ?? 0} color="success.main" />
          <StatCard label="Updated" value={run.entitiesUpdated ?? 0} color="primary.main" />
          <StatCard label="Failed" value={run.entitiesFailed ?? 0} color="error.main" />
        </Box>

        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <DlItem label="Started" value={formatDateTime(run.startedAt)} />
          <DlItem label="Completed" value={formatDateTime(run.completedAt)} />
        </Box>

        {run.errorMessage && <Alert severity="error">{run.errorMessage}</Alert>}

        {items.length > 0 && (
          <Box>
            <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Items ({items.length})</Typography>
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ bgcolor: 'grey.50' }}>
                    <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Entity</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Type</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Action</TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Error</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {items.map(item => (
                    <TableRow key={item.id} sx={item.errorDetail ? { bgcolor: '#fff5f5' } : undefined}>
                      <TableCell sx={{ maxWidth: 240 }}>
                        <Typography variant="caption" fontFamily="monospace" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {item.sourceKey}
                        </Typography>
                      </TableCell>
                      <TableCell><Typography variant="caption" color="text.secondary">{item.entityType}</Typography></TableCell>
                      <TableCell>
                        {item.action && <Chip label={item.action} size="small" sx={{ height: 18, fontSize: 11 }} />}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 240 }}>
                        <Typography variant="caption" color="error" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {item.errorDetail ?? '—'}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Box>
        )}
      </Box>
    </Box>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight={600} color={color} sx={{ mt: 0.5 }}>{value}</Typography>
    </Paper>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="body2" sx={{ mt: 0.25 }}>{value}</Typography>
    </Box>
  );
}
