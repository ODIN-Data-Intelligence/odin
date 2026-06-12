import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
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
import { harvestSourceApi, harvestJobApi, PageHeader } from '@datacatalog/shared';
import HarvestSourceForm from '../../components/admin/HarvestSourceForm';

const SOURCE_TYPE_LABELS: Record<string, string> = {
  dcat_http: 'DCAT HTTP',
  aws_glue: 'AWS Glue',
  snowflake: 'Snowflake',
  teradata: 'Teradata',
};

export default function HarvestPage() {
  const { tenant } = useParams();
  const [showForm, setShowForm] = useState(false);
  const qc = useQueryClient();

  const { data: sources = [] } = useQuery({
    queryKey: ['harvest-sources'],
    queryFn: () => harvestSourceApi.list(),
  });

  const { data: jobs = [] } = useQuery({
    queryKey: ['harvest-jobs'],
    queryFn: () => harvestJobApi.list(),
  });

  const triggerMut = useMutation({
    mutationFn: (jobId: string) => harvestJobApi.trigger(jobId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['harvest-jobs'] }),
  });

  return (
    <Box>
      <PageHeader
        title="Harvest"
        description="Configure sources and schedule metadata harvests"
        actions={
          <Button variant="contained" size="small" onClick={() => setShowForm(true)} sx={{ textTransform: 'none' }}>
            + Add Source
          </Button>
        }
      />

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Box>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Sources</Typography>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Name</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Type</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Endpoint</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {sources.map(src => (
                  <TableRow key={src.id} hover>
                    <TableCell>
                      <Typography
                        component={Link}
                        to={`/${tenant}/admin/harvest/sources/${src.id}`}
                        variant="body2"
                        fontWeight={600}
                        color="primary"
                        sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                      >
                        {src.name}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={SOURCE_TYPE_LABELS[src.sourceType] ?? src.sourceType} color="info" size="small" sx={{ height: 18, fontSize: 11 }} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {src.baseUrl ?? src.databaseName ?? '—'}
                      </Typography>
                    </TableCell>
                    <TableCell />
                  </TableRow>
                ))}
                {sources.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>No sources configured</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Paper>
        </Box>

        <Box>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Jobs</Typography>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Name</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Schedule</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Status</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {jobs.map(job => (
                  <TableRow key={job.id} hover>
                    <TableCell><Typography variant="body2" fontWeight={600}>{job.name}</Typography></TableCell>
                    <TableCell>
                      <Typography variant="caption" fontFamily="monospace" color="text.secondary">
                        {job.scheduleCron ?? 'Manual'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={job.enabled ? 'Enabled' : 'Disabled'}
                        color={job.enabled ? 'success' : 'default'}
                        size="small"
                        sx={{ height: 18, fontSize: 11 }}
                      />
                    </TableCell>
                    <TableCell sx={{ textAlign: 'right' }}>
                      <Button size="small" variant="outlined" onClick={() => triggerMut.mutate(job.id)} sx={{ textTransform: 'none' }}>
                        Run Now
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {jobs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>No jobs configured</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Paper>
        </Box>
      </Box>

      {showForm && <HarvestSourceForm onClose={() => { setShowForm(false); qc.invalidateQueries({ queryKey: ['harvest-sources'] }); }} />}
    </Box>
  );
}
