import { useParams } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { harvestSourceApi, harvestJobApi, PageHeader } from '@datacatalog/shared';

export default function HarvestSourceDetailPage() {
  const { id } = useParams();

  const { data: source } = useQuery({
    queryKey: ['harvest-source', id],
    queryFn: () => harvestSourceApi.get(id!),
    enabled: !!id,
  });

  const { data: jobs = [] } = useQuery({
    queryKey: ['harvest-jobs'],
    queryFn: () => harvestJobApi.list(),
  });

  const testMut = useMutation({
    mutationFn: () => harvestSourceApi.test(id!),
  });

  const sourceJobs = jobs.filter(j => j.sourceId === id);

  if (!source) return <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>Loading...</Typography>;

  return (
    <Box>
      <PageHeader
        title={source.name}
        description={`Source type: ${source.sourceType}`}
        actions={
          <Button variant="outlined" size="small" onClick={() => testMut.mutate()} disabled={testMut.isPending} sx={{ textTransform: 'none' }}>
            {testMut.isPending ? 'Testing...' : 'Test Connection'}
          </Button>
        }
      />

      {testMut.data && (
        <Box sx={{ mx: 3, mt: 2 }}>
          <Alert severity={testMut.data.success ? 'success' : 'error'}>
            {testMut.data.success ? 'Connection successful' : (testMut.data.message ?? 'Connection failed')}
          </Alert>
        </Box>
      )}

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            {source.baseUrl && (
              <Box>
                <Typography variant="caption" color="text.secondary">Base URL</Typography>
                <Typography variant="body2" fontFamily="monospace" sx={{ mt: 0.25 }}>{source.baseUrl}</Typography>
              </Box>
            )}
            {source.region && (
              <Box>
                <Typography variant="caption" color="text.secondary">Region</Typography>
                <Typography variant="body2" sx={{ mt: 0.25 }}>{source.region}</Typography>
              </Box>
            )}
            {source.databaseName && (
              <Box>
                <Typography variant="caption" color="text.secondary">Database</Typography>
                <Typography variant="body2" sx={{ mt: 0.25 }}>{source.databaseName}</Typography>
              </Box>
            )}
            {source.schemaFilter && source.schemaFilter.length > 0 && (
              <Box>
                <Typography variant="caption" color="text.secondary">Schema Filter</Typography>
                <Typography variant="body2" sx={{ mt: 0.25 }}>{source.schemaFilter.join(', ')}</Typography>
              </Box>
            )}
          </Box>
        </Paper>

        <Box>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Jobs</Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {sourceJobs.map(job => (
              <Paper key={job.id} variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Box>
                  <Typography variant="body2" fontWeight={600}>{job.name}</Typography>
                  <Typography variant="caption" color="text.secondary">{job.scheduleCron ?? 'Manual trigger'}</Typography>
                </Box>
                <Chip
                  label={job.enabled ? 'Enabled' : 'Disabled'}
                  color={job.enabled ? 'success' : 'default'}
                  size="small"
                  sx={{ height: 18, fontSize: 11 }}
                />
              </Paper>
            ))}
            {sourceJobs.length === 0 && (
              <Typography variant="body2" color="text.secondary">No jobs for this source.</Typography>
            )}
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
