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
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import { harvestSourceApi, harvestJobApi, PageHeader } from '@datacatalog/shared';
import type { HarvestSource, HarvestJob } from '@datacatalog/shared';
import HarvestSourceForm from '../../components/admin/HarvestSourceForm';
import HarvestJobForm from '../../components/admin/HarvestJobForm';

const SOURCE_TYPE_LABELS: Record<string, string> = {
  dcat_http: 'DCAT HTTP',
  aws_glue: 'AWS Glue',
  snowflake: 'Snowflake',
  teradata: 'Teradata',
};

export default function HarvestPage() {
  const { tenant } = useParams();
  const qc = useQueryClient();

  const [showSourceForm, setShowSourceForm] = useState(false);
  const [editingSource, setEditingSource] = useState<HarvestSource | null>(null);
  const [deletingSourceId, setDeletingSourceId] = useState<string | null>(null);

  const [showJobForm, setShowJobForm] = useState(false);
  const [editingJob, setEditingJob] = useState<HarvestJob | null>(null);
  const [deletingJobId, setDeletingJobId] = useState<string | null>(null);

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

  const deleteSourceMut = useMutation({
    mutationFn: (id: string) => harvestSourceApi.delete(id),
    onSuccess: () => {
      setDeletingSourceId(null);
      qc.invalidateQueries({ queryKey: ['harvest-sources'] });
    },
  });

  const deleteJobMut = useMutation({
    mutationFn: (id: string) => harvestJobApi.delete(id),
    onSuccess: () => {
      setDeletingJobId(null);
      qc.invalidateQueries({ queryKey: ['harvest-jobs'] });
    },
  });

  function closeSourceForm() {
    setShowSourceForm(false);
    setEditingSource(null);
    qc.invalidateQueries({ queryKey: ['harvest-sources'] });
  }

  function closeJobForm() {
    setShowJobForm(false);
    setEditingJob(null);
    qc.invalidateQueries({ queryKey: ['harvest-jobs'] });
  }

  return (
    <Box>
      <PageHeader
        title="Harvest"
        description="Configure sources and schedule metadata harvests"
        actions={
          <Button variant="contained" size="small" onClick={() => setShowSourceForm(true)} sx={{ textTransform: 'none' }}>
            + Add Source
          </Button>
        }
      />

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 4 }}>
        {/* Sources */}
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
                    <TableCell sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <Button size="small" onClick={() => setEditingSource(src)} sx={{ textTransform: 'none', mr: 1 }}>Edit</Button>
                      <Button size="small" color="error" onClick={() => setDeletingSourceId(src.id)} sx={{ textTransform: 'none' }}>Delete</Button>
                    </TableCell>
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

        {/* Jobs */}
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
            <Typography variant="subtitle2" fontWeight={600}>Jobs</Typography>
            <Button size="small" variant="outlined" onClick={() => setShowJobForm(true)} sx={{ textTransform: 'none' }}>+ Add Job</Button>
          </Box>
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
                    <TableCell sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <Button size="small" variant="outlined" onClick={() => triggerMut.mutate(job.id)} sx={{ textTransform: 'none', mr: 1 }}>Run Now</Button>
                      <Button size="small" onClick={() => setEditingJob(job)} sx={{ textTransform: 'none', mr: 1 }}>Edit</Button>
                      <Button size="small" color="error" onClick={() => setDeletingJobId(job.id)} sx={{ textTransform: 'none' }}>Delete</Button>
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

      {/* Source form (create / edit) */}
      {(showSourceForm || editingSource) && (
        <HarvestSourceForm source={editingSource ?? undefined} onClose={closeSourceForm} />
      )}

      {/* Job form (create / edit) */}
      {(showJobForm || editingJob) && (
        <HarvestJobForm job={editingJob ?? undefined} onClose={closeJobForm} />
      )}

      {/* Delete source confirmation */}
      <Dialog open={!!deletingSourceId} onClose={() => setDeletingSourceId(null)} maxWidth="xs">
        <DialogTitle>Delete source?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">This will also remove all associated jobs and runs.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletingSourceId(null)} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteSourceMut.isPending}
            onClick={() => deleteSourceMut.mutate(deletingSourceId!)}
            sx={{ textTransform: 'none' }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete job confirmation */}
      <Dialog open={!!deletingJobId} onClose={() => setDeletingJobId(null)} maxWidth="xs">
        <DialogTitle>Delete job?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">Associated run history will also be removed.</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeletingJobId(null)} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteJobMut.isPending}
            onClick={() => deleteJobMut.mutate(deletingJobId!)}
            sx={{ textTransform: 'none' }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
