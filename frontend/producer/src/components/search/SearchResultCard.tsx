import { Link, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import type { SearchResult } from '@datacatalog/shared';
import { formatDate, LIFECYCLE_COLORS } from '../../lib/utils';

const TYPE_COLORS: Record<string, 'primary' | 'secondary' | 'default'> = {
  DATASET:      'primary',
  DATA_PRODUCT: 'secondary',
  DISTRIBUTION: 'default',
};

const TYPE_LABELS: Record<string, string> = {
  DATASET:      'Dataset',
  DATA_PRODUCT: 'Data Product',
  DISTRIBUTION: 'Distribution',
};

interface Props {
  result: SearchResult;
}

export default function SearchResultCard({ result }: Props) {
  const { tenant } = useParams();

  const href = (() => {
    if (result.entityType === 'DATASET') return `/${tenant}/datasets/${result.id}`;
    if (result.entityType === 'DATA_PRODUCT') return `/${tenant}/data-products/${result.id}`;
    return `/${tenant}/datasets`;
  })();

  const lifecycle = result.lifecycleStatus;

  return (
    <Paper
      variant="outlined"
      sx={{ px: 2.5, py: 2, '&:hover': { borderColor: 'primary.light', boxShadow: 1 }, transition: 'border-color 0.15s, box-shadow 0.15s' }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
            <Typography
              component={Link}
              to={href}
              variant="body2"
              fontWeight={600}
              color="primary"
              noWrap
              sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
            >
              {result.title}
            </Typography>
            <Chip
              label={TYPE_LABELS[result.entityType] ?? result.entityType}
              color={TYPE_COLORS[result.entityType] ?? 'default'}
              size="small"
              sx={{ height: 18, fontSize: 11, flexShrink: 0 }}
            />
            {result.format && (
              <Chip label={result.format} size="small" variant="outlined" sx={{ height: 18, fontSize: 11, flexShrink: 0 }} />
            )}
          </Box>

          {result.description && (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: '-webkit-box', WebkitLineClamp: 1, WebkitBoxOrient: 'vertical', overflow: 'hidden', mb: 1 }}
            >
              {result.description}
            </Typography>
          )}

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            {(result.keywords ?? []).slice(0, 4).map(kw => (
              <Chip key={kw} label={kw} size="small" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
            ))}
            {lifecycle && (
              <Chip
                label={lifecycle}
                color={LIFECYCLE_COLORS[lifecycle] ?? 'default'}
                size="small"
                sx={{ height: 18, fontSize: 11 }}
              />
            )}
            {result.updatedAt && (
              <Typography variant="caption" color="text.disabled" sx={{ ml: 'auto' }}>
                {formatDate(result.updatedAt)}
              </Typography>
            )}
          </Box>
        </Box>
      </Box>
    </Paper>
  );
}
