import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Grid2 from '@mui/material/Grid2';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import { dataProductApi, PageHeader } from '@datacatalog/shared';
import { LIFECYCLE_COLORS, formatDate } from '../lib/utils';
import DataProductWizard from '../components/catalog/DataProductWizard';

const LIFECYCLE_STAGES = ['', 'Ideation', 'Design', 'Build', 'Deploy', 'Consume'];

export default function DataProductsPage() {
  const { tenant } = useParams();
  const [showWizard, setShowWizard] = useState(false);
  const [lifecycleFilter, setLifecycleFilter] = useState('');

  const { data: productPage, refetch } = useQuery({
    queryKey: ['data-products', lifecycleFilter],
    queryFn: () => dataProductApi.list(lifecycleFilter ? { lifecycleStatus: lifecycleFilter } : {}),
  });
  const products = productPage?.content ?? [];

  return (
    <Box>
      <PageHeader
        title="Data Products"
        description="Browse and manage data products across your organization"
        actions={
          <Button variant="contained" size="small" onClick={() => setShowWizard(true)} sx={{ textTransform: 'none' }}>
            + New Data Product
          </Button>
        }
      />

      <Box sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', gap: 1, mb: 2.5, flexWrap: 'wrap' }}>
          {LIFECYCLE_STAGES.map(s => (
            <Chip
              key={s}
              label={s || 'All'}
              color={lifecycleFilter === s ? 'primary' : 'default'}
              variant={lifecycleFilter === s ? 'filled' : 'outlined'}
              size="small"
              onClick={() => setLifecycleFilter(s)}
              sx={{ cursor: 'pointer' }}
            />
          ))}
        </Box>

        <Grid2 container spacing={2}>
          {products.map(dp => (
            <Grid2 key={dp.id} size={{ xs: 12, md: 6, xl: 4 }}>
              <Paper
                variant="outlined"
                component={Link}
                to={`/${tenant}/data-products/${dp.id}`}
                sx={{ p: 2, display: 'block', textDecoration: 'none', '&:hover': { borderColor: 'primary.light', boxShadow: 1 }, transition: 'border-color 0.15s, box-shadow 0.15s', height: '100%' }}
              >
                <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1, mb: 1 }}>
                  <Typography variant="body2" fontWeight={600} sx={{ minWidth: 0, flex: 1 }}>
                    {dp.title}
                  </Typography>
                  <Chip
                    label={dp.lifecycleStatus}
                    color={LIFECYCLE_COLORS[dp.lifecycleStatus] ?? 'default'}
                    size="small"
                    sx={{ height: 18, fontSize: 11, flexShrink: 0 }}
                  />
                </Box>
                {dp.description && (
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', mb: 1 }}
                  >
                    {dp.description}
                  </Typography>
                )}
                {dp.keywords && dp.keywords.length > 0 && (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
                    {dp.keywords.slice(0, 3).map(kw => (
                      <Chip key={kw} label={kw} size="small" variant="outlined" sx={{ height: 18, fontSize: 11 }} />
                    ))}
                  </Box>
                )}
                <Typography variant="caption" color="text.disabled">Updated {formatDate(dp.updatedAt)}</Typography>
              </Paper>
            </Grid2>
          ))}
          {products.length === 0 && (
            <Grid2 size={12}>
              <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 8 }}>
                No data products found. Create your first one.
              </Typography>
            </Grid2>
          )}
        </Grid2>
      </Box>

      {showWizard && <DataProductWizard onClose={() => { setShowWizard(false); refetch(); }} />}
    </Box>
  );
}
