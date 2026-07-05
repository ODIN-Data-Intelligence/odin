import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import DownloadIcon from '@mui/icons-material/Download';
import { datasetApi, PageHeader } from '@datacatalog/shared';
import type { Distribution } from '@datacatalog/shared';
import PhysicalSchemaSection from '../components/catalog/PhysicalSchemaSection';
import { formatDate } from '../lib/utils';
import { useAuthStore } from '../store/authStore';

const FORMAT_COLORS: Record<string, 'warning' | 'success' | 'secondary' | 'error' | 'info' | 'primary'> = {
  Parquet: 'warning',
  CSV:     'success',
  JSON:    'secondary',
  Avro:    'primary',
  ORC:     'info',
  Kafka:   'error',
  Delta:   'secondary',
};

function formatBytes(bytes?: number) {
  if (!bytes) return null;
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(1)} KB`;
  return `${bytes} B`;
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Tooltip title={copied ? 'Copied!' : 'Copy'}>
      <IconButton size="small" onClick={() => { navigator.clipboard.writeText(value); setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
        {copied ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}

export default function DistributionDetailPage() {
  const { datasetId, id, tenant } = useParams();
  const { userId } = useAuthStore();

  const { data: distributions = [], isLoading } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId!),
    enabled: !!datasetId,
  });

  const { data: dataset } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => datasetApi.get(datasetId!),
    enabled: !!datasetId,
  });

  const dist: Distribution | undefined = distributions.find(d => d.id === id);
  const canOwnerAction = !!dataset?.ownerId && dataset.ownerId === userId;

  if (isLoading) return <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>Loading...</Typography>;
  if (!dist) return <Typography variant="body2" color="error" sx={{ p: 3 }}>Distribution not found</Typography>;

  return (
    <Box>
      <PageHeader
        title={dist.title ?? dist.format ?? 'Distribution'}
        description={dist.description}
        actions={
          <Typography
            component={Link}
            to={`/${tenant}/datasets/${datasetId}`}
            variant="body2"
            color="primary"
            sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
          >
            ← Back to dataset
          </Typography>
        }
      />

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2.5, maxWidth: 900 }}>
        {/* Metadata */}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Details</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(3, 1fr)' }, gap: 2 }}>
            {dist.format && (
              <MetaItem label="Format">
                <Chip label={dist.format} color={FORMAT_COLORS[dist.format] ?? 'default'} size="small" sx={{ height: 20, fontSize: 11 }} />
              </MetaItem>
            )}
            {dist.mediaType && <MetaItem label="Media Type"><Typography variant="caption" fontFamily="monospace">{dist.mediaType}</Typography></MetaItem>}
            {dist.byteSize && <MetaItem label="Size"><Typography variant="caption">{formatBytes(dist.byteSize)}</Typography></MetaItem>}
            {dist.compressFormat && <MetaItem label="Compression"><Typography variant="caption">{dist.compressFormat}</Typography></MetaItem>}
            {dist.availability && <MetaItem label="Availability"><Typography variant="caption">{dist.availability}</Typography></MetaItem>}
            {dist.createdAt && <MetaItem label="Created"><Typography variant="caption">{formatDate(dist.createdAt)}</Typography></MetaItem>}
          </Box>
        </Paper>

        {/* Access */}
        {(dist.accessUrl || dist.downloadUrl) && (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Access</Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              {dist.accessUrl && (
                <Box>
                  <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>Access URL</Typography>
                  <Paper variant="outlined" sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="caption" fontFamily="monospace" noWrap sx={{ flex: 1 }}>{dist.accessUrl}</Typography>
                    <CopyButton value={dist.accessUrl} />
                    <Tooltip title="Open"><IconButton size="small" component="a" href={dist.accessUrl} target="_blank" rel="noreferrer"><OpenInNewIcon fontSize="small" /></IconButton></Tooltip>
                  </Paper>
                </Box>
              )}
              {dist.downloadUrl && (
                <Box>
                  <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>Download URL</Typography>
                  <Paper variant="outlined" sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="caption" fontFamily="monospace" noWrap sx={{ flex: 1 }}>{dist.downloadUrl}</Typography>
                    <Tooltip title="Download"><IconButton size="small" component="a" href={dist.downloadUrl} download><DownloadIcon fontSize="small" /></IconButton></Tooltip>
                  </Paper>
                </Box>
              )}
            </Box>
          </Paper>
        )}

        {/* Checksum */}
        {dist.checksumValue && (
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 0.75 }}>Integrity</Typography>
            <Typography variant="caption" color="text.secondary">
              <Typography component="span" variant="caption" fontWeight={600} color="text.primary">{dist.checksumAlgorithm ?? 'Checksum'}:</Typography>
              {' '}
              <Typography component="span" variant="caption" fontFamily="monospace">{dist.checksumValue}</Typography>
            </Typography>
          </Paper>
        )}

        <PhysicalSchemaSection distributionId={id!} datasetId={datasetId!} tenant={tenant!} canAction={canOwnerAction} />
      </Box>
    </Box>
  );
}

function MetaItem({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.25 }}>{label}</Typography>
      {children}
    </Box>
  );
}
