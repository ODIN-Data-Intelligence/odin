import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Skeleton from '@mui/material/Skeleton';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Button from '@mui/material/Button';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DownloadIcon from '@mui/icons-material/Download';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import CheckIcon from '@mui/icons-material/Check';
import { datasetApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { Distribution, CsvwColumn, LogicalDataElement } from '@datacatalog/shared';

function useDistributionSchema(distId: string) {
  return useQuery({
    queryKey: ['distribution-schema', distId],
    queryFn: () => datasetApi.getDistributionPhysicalSchema(distId),
  });
}

const FORMAT_COLORS: Record<string, 'primary' | 'success' | 'warning' | 'error' | 'info' | 'secondary'> = {
  Snowflake: 'info',
  Parquet:   'warning',
  CSV:       'success',
  JSON:      'secondary',
  Kafka:     'warning',
  XML:       'error',
  'ISO 20022 XML': 'error',
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  function copy() {
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }
  return (
    <Tooltip title={copied ? 'Copied!' : `Copy ${label}`}>
      <IconButton size="small" onClick={copy} color={copied ? 'success' : 'default'}>
        {copied ? <CheckIcon fontSize="small" /> : <ContentCopyIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}

function DistributionCard({ dist, elements }: { dist: Distribution; elements: LogicalDataElement[] }) {
  const url = dist.accessUrl ?? dist.downloadUrl;
  const { data: columns = [], isLoading: schemaLoading } = useDistributionSchema(dist.id);

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Box sx={{ px: 2, py: 1.5, bgcolor: 'grey.50', borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <Chip
            label={dist.format ?? dist.mediaType ?? 'File'}
            color={FORMAT_COLORS[dist.format ?? ''] ?? 'default'}
            size="small"
          />
          <Typography variant="body2" fontWeight={600} noWrap>{dist.title}</Typography>
        </Box>
        {dist.availability && (
          <Chip
            label={dist.availability}
            color={dist.availability === 'available' ? 'success' : 'warning'}
            size="small"
            variant="outlined"
          />
        )}
      </Box>

      <Box sx={{ px: 2, py: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        {dist.description && (
          <Typography variant="body2" color="text.secondary">{dist.description}</Typography>
        )}

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
          {dist.mediaType && <MetaItem label="Media type" value={dist.mediaType} mono />}
          {dist.byteSize != null && <MetaItem label="Size" value={formatBytes(dist.byteSize)} />}
          {dist.checksumAlgorithm && dist.checksumValue && (
            <Box sx={{ gridColumn: '1 / -1' }}>
              <Typography variant="caption" color="text.secondary" fontWeight={600}>{dist.checksumAlgorithm} checksum</Typography>
              <Typography variant="caption" fontFamily="monospace" display="block" sx={{ wordBreak: 'break-all' }}>{dist.checksumValue}</Typography>
            </Box>
          )}
        </Box>

        {url && (
          <Paper variant="outlined" sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="caption" color="text.secondary">{dist.accessUrl ? 'Access URL' : 'Download URL'}</Typography>
              <Typography variant="caption" fontFamily="monospace" display="block" noWrap>{url}</Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
              <CopyButton value={url} label="URL" />
              {dist.downloadUrl && (
                <Tooltip title="Download">
                  <IconButton size="small" component="a" href={dist.downloadUrl} target="_blank" rel="noreferrer">
                    <DownloadIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              {dist.accessUrl && (
                <Tooltip title="Open">
                  <IconButton size="small" component="a" href={dist.accessUrl} target="_blank" rel="noreferrer">
                    <OpenInNewIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          </Paper>
        )}

        {(dist.databaseName || dist.schemaName || dist.tableName) && (
          <Paper variant="outlined" sx={{ px: 2, py: 1.5 }}>
            <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>Table Location</Typography>
            <Typography variant="body2" fontFamily="monospace" fontWeight={700}>
              {[dist.databaseName, dist.schemaName, dist.tableName].filter(Boolean).join('.')}
            </Typography>
            <Box sx={{ display: 'flex', gap: 3, mt: 1 }}>
              {dist.databaseName && <MetaItem label="Database" value={dist.databaseName} mono />}
              {dist.schemaName && <MetaItem label="Schema" value={dist.schemaName} mono />}
              {dist.tableName && <MetaItem label="Table" value={dist.tableName} mono />}
            </Box>
          </Paper>
        )}

        <PhysicalSchemaSection columns={columns} isLoading={schemaLoading} elements={elements} />
      </Box>
    </Paper>
  );
}

function MetaItem({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block">{label}</Typography>
      <Typography variant="caption" fontFamily={mono ? 'monospace' : undefined}>{value}</Typography>
    </Box>
  );
}

function PhysicalSchemaSection({ columns, isLoading, elements = [] }: {
  columns: CsvwColumn[];
  isLoading: boolean;
  elements?: LogicalDataElement[];
}) {
  const [expanded, setExpanded] = useState(false);

  if (isLoading) {
    return <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>{[...Array(3)].map((_, i) => <Skeleton key={i} height={32} />)}</Box>;
  }

  if (columns.length === 0) {
    return (
      <Paper variant="outlined" sx={{ px: 2, py: 4, textAlign: 'center', borderStyle: 'dashed' }}>
        <Typography variant="body2" color="text.secondary">No physical schema harvested yet.</Typography>
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 0.5 }}>
          Configure a harvest source and trigger a run to populate column-level details.
        </Typography>
      </Paper>
    );
  }

  const preview = expanded ? columns : columns.slice(0, 8);

  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Box sx={{ px: 2, py: 1, bgcolor: 'grey.50', borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="caption" fontWeight={600} color="text.secondary">Physical columns · {columns.length} total</Typography>
        <Typography variant="caption" fontFamily="monospace" color="text.disabled">CSV-W</Typography>
      </Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: 'grey.50' }}>
              <TableCell sx={{ width: 32, color: 'text.disabled', fontSize: 11 }}>#</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Column</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Datatype</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Nullable</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Description</TableCell>
              {elements.length > 0 && <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Logical Element</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {preview.map(col => (
              <TableRow key={col.id} hover>
                <TableCell sx={{ color: 'text.disabled', fontSize: 11 }}>{col.ordinal + 1}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace', fontWeight: 600, fontSize: 12 }}>
                  {col.name}
                  {col.titles && col.titles.length > 0 && col.titles[0] !== col.name && (
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>({col.titles[0]})</Typography>
                  )}
                </TableCell>
                <TableCell>
                  {col.datatype
                    ? <Chip label={col.datatype} size="small" variant="outlined" sx={{ height: 18, fontSize: 11, fontFamily: 'monospace' }} />
                    : <Typography variant="caption" color="text.disabled">—</Typography>}
                </TableCell>
                <TableCell>
                  {col.required
                    ? <Chip label="NOT NULL" color="error" size="small" sx={{ height: 18, fontSize: 11 }} />
                    : <Typography variant="caption" color="text.secondary">nullable</Typography>}
                </TableCell>
                <TableCell sx={{ maxWidth: 160 }}>
                  <Typography variant="caption" color="text.secondary" noWrap title={col.description ?? ''}>
                    {col.description ?? <Typography component="span" variant="caption" color="text.disabled">—</Typography>}
                  </Typography>
                </TableCell>
                {elements.length > 0 && (
                  <TableCell>
                    {col.logicalDataElementId ? (() => {
                      const el = elements.find(e => e.id === col.logicalDataElementId);
                      return el
                        ? <Chip label={el.name} size="small" color="secondary" component={Link} to={`/search?q=${encodeURIComponent(el.name)}`} clickable sx={{ height: 18, fontSize: 11 }} />
                        : <Typography variant="caption" color="text.disabled">—</Typography>;
                    })() : <Typography variant="caption" color="text.disabled">—</Typography>}
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      {columns.length > 8 && (
        <Box sx={{ px: 2, py: 1, borderTop: 1, borderColor: 'divider', bgcolor: 'grey.50', textAlign: 'center' }}>
          <Button size="small" onClick={() => setExpanded(e => !e)} sx={{ fontSize: 12, textTransform: 'none' }}>
            {expanded ? 'Show fewer' : `Show all ${columns.length} columns`}
          </Button>
        </Box>
      )}
    </Paper>
  );
}

export default function DistributionsTab({ datasetId }: { datasetId: string }) {
  const { data: distributions = [], isLoading: distLoading } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  const { data: logicalModels = [] } = useQuery({
    queryKey: ['logical-models', datasetId],
    queryFn: () => logicalModelApi.list(datasetId),
    enabled: distributions.length > 0,
  });

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', logicalModels[0]?.id],
    queryFn: () => logicalElementApi.list(logicalModels[0]!.id),
    enabled: logicalModels.length > 0,
  });

  if (distLoading) {
    return <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>{[...Array(2)].map((_, i) => <Skeleton key={i} variant="rounded" height={120} />)}</Box>;
  }

  if (distributions.length === 0) {
    return <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 5 }}>No distributions registered for this dataset.</Typography>;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {distributions.map(dist => (
        <DistributionCard key={dist.id} dist={dist} elements={elements} />
      ))}
    </Box>
  );
}
