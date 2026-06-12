import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import type { SearchResult } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';
import BookmarkButton from './BookmarkButton';

interface DatasetSummaryCardProps {
  result: SearchResult;
  isActive: boolean;
}

const FORMAT_COLORS: Record<string, 'success' | 'primary' | 'warning' | 'info' | 'secondary'> = {
  'text/csv': 'success',
  CSV: 'success',
  'application/parquet': 'primary',
  Parquet: 'primary',
  'application/json': 'warning',
  JSON: 'warning',
  SNOWFLAKE: 'info',
  GLUE: 'secondary',
};

export default function DatasetSummaryCard({ result, isActive }: DatasetSummaryCardProps) {
  const { openDataset } = useDrawerStore();

  return (
    <Card
      variant="outlined"
      sx={{
        flexShrink: 0,
        borderColor: isActive ? 'primary.main' : 'divider',
        bgcolor: isActive ? 'primary.50' : 'background.paper',
        transition: 'border-color 0.15s, box-shadow 0.15s',
        overflow: 'hidden',
        '&:hover': { borderColor: 'primary.light', boxShadow: 2 },
      }}
    >
      <CardActionArea onClick={() => openDataset(result.id, result.entityType as 'DATASET' | 'DATA_PRODUCT')}>
        <CardContent sx={{ p: 2, '&:last-child': { pb: 2 }, display: 'flex', flexDirection: 'column', gap: 1 }}>

          {/* Title + bookmark — row height set by the IconButton (~30px) */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, overflow: 'hidden' }}>
            <Typography variant="body2" fontWeight={600} noWrap sx={{ flex: 1 }}>
              {result.title}
            </Typography>
            <Box sx={{ flexShrink: 0 }}>
              <BookmarkButton datasetId={result.id} datasetTitle={result.title} />
            </Box>
          </Box>

          {/* Description — single line with ellipsis */}
          <Box sx={{ height: 20, overflow: 'hidden' }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
              {result.description ?? ''}
            </Typography>
          </Box>

          {/* Badges: distribution formats, lineage, entity type · lifecycle */}
          <Box sx={{ height: 20, display: 'flex', alignItems: 'center', gap: 0.5, overflow: 'hidden' }}>
            {(result.distributionFormats ?? []).map(fmt => (
              <Chip
                key={fmt}
                label={fmt}
                color={FORMAT_COLORS[fmt] ?? 'default'}
                size="small"
                sx={{ height: 18, fontSize: 10 }}
              />
            ))}
            {result.hasLineage && (
              <Chip label="lineage" color="secondary" size="small" sx={{ height: 18, fontSize: 10 }} />
            )}
            <Typography
              variant="caption"
              color="text.disabled"
              sx={{ ml: 'auto', fontSize: 10, flexShrink: 0, textTransform: 'capitalize' }}
            >
              {result.entityType.replace('_', ' ').toLowerCase()}
              {result.lifecycleStatus ? ` · ${result.lifecycleStatus}` : ''}
            </Typography>
          </Box>

          {/* Keywords / tags */}
          <Box sx={{ height: 20, display: 'flex', alignItems: 'center', gap: 0.5, overflow: 'hidden' }}>
            {(result.keywords ?? []).slice(0, 4).map(kw => (
              <Chip key={kw} label={kw} size="small" sx={{ height: 18, fontSize: 10 }} />
            ))}
          </Box>

          {/* Vocab concepts */}
          <Box sx={{ height: 20, display: 'flex', alignItems: 'center', gap: 0.5, overflow: 'hidden' }}>
            {(result.vocabConceptLabels ?? []).slice(0, 4).map(label => (
              <Chip key={label} label={label} size="small" variant="outlined" color="primary" sx={{ height: 18, fontSize: 10 }} />
            ))}
          </Box>

        </CardContent>
      </CardActionArea>
    </Card>
  );
}
