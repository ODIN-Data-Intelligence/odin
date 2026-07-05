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
import Button from '@mui/material/Button';
import { vocabularyApi, PageHeader } from '@datacatalog/shared';

const VOCAB_TYPE_COLORS: Record<string, 'default' | 'warning' | 'success' | 'info' | 'secondary'> = {
  general: 'default',
  financial: 'warning',
  healthcare: 'success',
  geospatial: 'info',
  custom: 'secondary',
};

export default function SettingsPage() {
  const { data: vocabs = [] } = useQuery({
    queryKey: ['vocabularies'],
    queryFn: () => vocabularyApi.list(),
  });

  return (
    <Box>
      <PageHeader title="Settings" description="API keys, LLM providers, vocabulary registry" />
      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
            <Typography variant="subtitle2" fontWeight={600}>Registered Vocabularies</Typography>
            <Button size="small" variant="outlined" sx={{ textTransform: 'none' }}>+ Register Vocabulary</Button>
          </Box>
          <Paper variant="outlined" sx={{ overflow: 'auto' }}>
            <Table size="small" sx={{ minWidth: 680 }}>
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Name</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Prefix</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Type</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Base IRI</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {vocabs.map(v => (
                  <TableRow key={v.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>
                        {v.name}
                        {v.isSystem && <Typography component="span" variant="caption" color="text.disabled" sx={{ ml: 1 }}>(system)</Typography>}
                      </Typography>
                    </TableCell>
                    <TableCell><Typography variant="caption" fontFamily="monospace" color="text.secondary">{v.prefix}</Typography></TableCell>
                    <TableCell>
                      <Chip label={v.vocabularyType} color={VOCAB_TYPE_COLORS[v.vocabularyType] ?? 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />
                    </TableCell>
                    <TableCell sx={{ maxWidth: 240 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {v.baseIri}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
                {vocabs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>No vocabularies registered</TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Paper>
        </Box>

        <Box>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>API Keys</Typography>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="body2" color="text.secondary">API key management will be shown here.</Typography>
            <Button size="small" variant="outlined" sx={{ mt: 1.5, textTransform: 'none' }}>Generate API Key</Button>
          </Paper>
        </Box>
      </Box>
    </Box>
  );
}
