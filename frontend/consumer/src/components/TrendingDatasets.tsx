import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid2';
import { searchApi } from '@datacatalog/shared';

const TYPE_COLORS: Record<string, 'primary' | 'secondary'> = {
  DATASET:      'primary',
  DATA_PRODUCT: 'secondary',
};

export default function TrendingDatasets() {
  const navigate = useNavigate();

  const { data } = useQuery({
    queryKey: ['trending'],
    queryFn: () => searchApi.search({ size: 6 }),
  });

  const results = data?.results ?? [];
  if (results.length === 0) return null;

  return (
    <Box>
      <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Trending Datasets</Typography>
      <Grid container spacing={2}>
        {results.map(r => (
          <Grid key={r.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card variant="outlined" sx={{ '&:hover': { borderColor: 'primary.light', boxShadow: 2 }, transition: 'border-color 0.15s, box-shadow 0.15s' }}>
              <CardActionArea onClick={() => navigate(`/search?ds=${r.id}`)}>
                <CardContent sx={{ p: 2, '&:last-child': { pb: 2 }, display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {/* Title + type badge */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, overflow: 'hidden' }}>
                    <Typography variant="body2" fontWeight={600} noWrap sx={{ flex: 1 }}>{r.title}</Typography>
                    <Chip
                      label={r.entityType === 'DATA_PRODUCT' ? 'product' : r.entityType === 'DISTRIBUTION' ? 'dist' : 'dataset'}
                      color={TYPE_COLORS[r.entityType] ?? 'default'}
                      size="small"
                      sx={{ height: 18, fontSize: 10, flexShrink: 0 }}
                    />
                  </Box>
                  {/* Description — fixed 2-line height so all cards are the same */}
                  <Box sx={{ height: 40, overflow: 'hidden' }}>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
                    >
                      {r.description ?? ''}
                    </Typography>
                  </Box>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}
