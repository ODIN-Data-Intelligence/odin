import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import { datasetApi, useIriTranslations, iriFragment, PageHeader, useIsMobile } from '@datacatalog/shared';
import { formatDate } from '../lib/utils';

export default function DatasetsPage() {
  const { tenant } = useParams();
  const isMobile = useIsMobile();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 20;

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['datasets', page],
    queryFn: () => datasetApi.list({ page, size: PAGE_SIZE }),
  });

  const datasets = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const freqIris = datasets.flatMap(ds => ds.accrualPeriodicity ? [ds.accrualPeriodicity] : []);
  const freqTranslations = useIriTranslations(freqIris);
  const tFreq = (iri: string) => freqTranslations[iri] ?? iriFragment(iri);

  const filtered = search
    ? datasets.filter(ds =>
        ds.title?.toLowerCase().includes(search.toLowerCase()) ||
        ds.description?.toLowerCase().includes(search.toLowerCase()) ||
        ds.keywords?.some(k => k.toLowerCase().includes(search.toLowerCase()))
      )
    : datasets;

  return (
    <Box>
      <PageHeader
        title="Datasets"
        description={`${totalElements} datasets in the catalog`}
        actions={
          <Button variant="contained" component={Link} to={`/${tenant}/datasets/new`} size="small" sx={{ textTransform: 'none' }}>
            + New Dataset
          </Button>
        }
      />

      <Box sx={{ p: 3 }}>
        <TextField
          type="search"
          placeholder="Filter by title, description, or keyword…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          size="small"
          sx={{ mb: 2, maxWidth: 480 }}
          fullWidth
        />

        {isMobile ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {isLoading && [...Array(5)].map((_, i) => <Skeleton key={i} variant="rounded" height={92} />)}
            {!isLoading && filtered.map(ds => (
              <Paper key={ds.id} variant="outlined" sx={{ p: 1.5 }}>
                <Typography
                  component={Link}
                  to={`/${tenant}/datasets/${ds.id}`}
                  variant="body2"
                  fontWeight={600}
                  color="primary"
                  sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                >
                  {ds.title}
                </Typography>
                {ds.description && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', mt: 0.25 }}
                  >
                    {ds.description}
                  </Typography>
                )}
                {(ds.keywords ?? []).length > 0 && (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.75 }}>
                    {(ds.keywords ?? []).slice(0, 3).map(kw => (
                      <Chip key={kw} label={kw} size="small" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
                    ))}
                  </Box>
                )}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 0.75 }}>
                  <Typography variant="caption" color="text.secondary">
                    {ds.accrualPeriodicity ? tFreq(ds.accrualPeriodicity) : '—'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">{formatDate(ds.updatedAt)}</Typography>
                </Box>
              </Paper>
            ))}
            {!isLoading && filtered.length === 0 && (
              <Typography variant="body2" sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>
                {search ? 'No datasets match your filter.' : 'No datasets found.'}
              </Typography>
            )}
          </Box>
        ) : (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'grey.50' }}>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Title</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Keywords</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Frequency</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Updated</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && [...Array(5)].map((_, i) => (
                <TableRow key={i}>
                  {[...Array(4)].map((_, j) => (
                    <TableCell key={j}><Skeleton height={20} /></TableCell>
                  ))}
                </TableRow>
              ))}
              {!isLoading && filtered.map(ds => (
                <TableRow key={ds.id} hover>
                  <TableCell>
                    <Typography
                      component={Link}
                      to={`/${tenant}/datasets/${ds.id}`}
                      variant="body2"
                      fontWeight={600}
                      color="primary"
                      sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                    >
                      {ds.title}
                    </Typography>
                    {ds.description && (
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
                      >
                        {ds.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                      {(ds.keywords ?? []).slice(0, 3).map(kw => (
                        <Chip key={kw} label={kw} size="small" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
                      ))}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {ds.accrualPeriodicity ? tFreq(ds.accrualPeriodicity) : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">{formatDate(ds.updatedAt)}</Typography>
                  </TableCell>
                </TableRow>
              ))}
              {!isLoading && filtered.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>
                    {search ? 'No datasets match your filter.' : 'No datasets found.'}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Paper>
        )}

        {totalPages > 1 && (
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 2 }}>
            <Typography variant="body2" color="text.secondary">Page {page + 1} of {totalPages}</Typography>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button size="small" variant="outlined" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} sx={{ textTransform: 'none' }}>
                Previous
              </Button>
              <Button size="small" variant="outlined" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} sx={{ textTransform: 'none' }}>
                Next
              </Button>
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
}
