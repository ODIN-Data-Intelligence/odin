import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useQueries } from '@tanstack/react-query';
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
import Skeleton from '@mui/material/Skeleton';
import { distributionApi, datasetApi, PageHeader, useIsMobile } from '@datacatalog/shared';
import { formatDate } from '../lib/utils';

const FORMAT_COLORS: Record<string, 'warning' | 'success' | 'secondary' | 'error' | 'info' | 'primary' | 'default'> = {
  Parquet:   'warning',
  CSV:       'success',
  JSON:      'secondary',
  Avro:      'primary',
  ORC:       'info',
  Kafka:     'error',
  Delta:     'secondary',
  Snowflake: 'info',
};

const PAGE_SIZE = 20;

export default function DistributionsPage() {
  const { tenant } = useParams();
  const isMobile = useIsMobile();
  const [page, setPage] = useState(0);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['distributions', page],
    queryFn: () => distributionApi.list({ page, size: PAGE_SIZE }),
  });

  const distributions = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const uniqueDatasetIds = [...new Set(distributions.map(d => d.datasetId).filter(Boolean))] as string[];
  const datasetResults = useQueries({
    queries: uniqueDatasetIds.map(id => ({
      queryKey: ['dataset', id],
      queryFn: () => datasetApi.get(id),
      staleTime: 300_000,
    })),
  });
  const datasetTitles: Record<string, string> = Object.fromEntries(
    uniqueDatasetIds.map((id, i) => [id, datasetResults[i].data?.title ?? '']).filter(([, t]) => t)
  );

  return (
    <Box>
      <PageHeader title="Distributions" description={`${totalElements} distributions across all datasets`} />

      <Box sx={{ p: 3 }}>
        {isMobile ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {isLoading && [...Array(5)].map((_, i) => <Skeleton key={i} variant="rounded" height={92} />)}
            {!isLoading && distributions.map(dist => (
              <Paper key={dist.id} variant="outlined" sx={{ p: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                  {dist.format && (
                    <Chip label={dist.format} color={FORMAT_COLORS[dist.format] ?? 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />
                  )}
                  <Typography
                    component={Link}
                    to={`/${tenant}/datasets/${dist.datasetId}/distributions/${dist.id}`}
                    variant="body2"
                    fontWeight={600}
                    color="primary"
                    sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' }, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  >
                    {dist.title ?? dist.id}
                  </Typography>
                </Box>
                {dist.description && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {dist.description}
                  </Typography>
                )}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 1, mt: 0.75 }}>
                  <Typography
                    component={Link}
                    to={`/${tenant}/datasets/${dist.datasetId}`}
                    variant="caption"
                    color="primary"
                    sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' }, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                  >
                    {dist.datasetId ? (datasetTitles[dist.datasetId] || dist.datasetId.slice(0, 8) + '…') : '—'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>{formatDate(dist.updatedAt)}</Typography>
                </Box>
              </Paper>
            ))}
            {!isLoading && distributions.length === 0 && (
              <Typography variant="body2" sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>
                No distributions found.
              </Typography>
            )}
          </Box>
        ) : (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: 'grey.50' }}>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Format</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Title</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Dataset</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Media Type</TableCell>
                <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Updated</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && [...Array(5)].map((_, i) => (
                <TableRow key={i}>
                  {[...Array(5)].map((_, j) => <TableCell key={j}><Skeleton height={20} /></TableCell>)}
                </TableRow>
              ))}
              {!isLoading && distributions.map(dist => (
                <TableRow key={dist.id} hover>
                  <TableCell>
                    {dist.format
                      ? <Chip label={dist.format} color={FORMAT_COLORS[dist.format] ?? 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />
                      : <Typography variant="caption" color="text.disabled">—</Typography>}
                  </TableCell>
                  <TableCell>
                    <Typography
                      component={Link}
                      to={`/${tenant}/datasets/${dist.datasetId}/distributions/${dist.id}`}
                      variant="body2"
                      fontWeight={600}
                      color="primary"
                      sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                    >
                      {dist.title ?? dist.id}
                    </Typography>
                    {dist.description && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                        {dist.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Typography
                      component={Link}
                      to={`/${tenant}/datasets/${dist.datasetId}`}
                      variant="caption"
                      color="primary"
                      sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                    >
                      {dist.datasetId ? (datasetTitles[dist.datasetId] || dist.datasetId.slice(0, 8) + '…') : '—'}
                    </Typography>
                  </TableCell>
                  <TableCell><Typography variant="caption" color="text.secondary">{dist.mediaType ?? '—'}</Typography></TableCell>
                  <TableCell><Typography variant="caption" color="text.secondary">{formatDate(dist.updatedAt)}</Typography></TableCell>
                </TableRow>
              ))}
              {!isLoading && distributions.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} sx={{ textAlign: 'center', py: 5, color: 'text.disabled' }}>
                    No distributions found.
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
              <Button size="small" variant="outlined" onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} sx={{ textTransform: 'none' }}>Previous</Button>
              <Button size="small" variant="outlined" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} sx={{ textTransform: 'none' }}>Next</Button>
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
}
