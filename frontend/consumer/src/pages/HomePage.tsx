import { useRef } from 'react';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Typography from '@mui/material/Typography';
import SearchBar from '../components/SearchBar';
import TrendingDatasets from '../components/TrendingDatasets';
import RecentlyViewed from '../components/RecentlyViewed';
import DatasetDetailDrawer from '../components/DatasetDetailDrawer';
import { useDrawerStore } from '../store/drawerStore';

export default function HomePage() {
  const localRef = useRef<HTMLInputElement>(null);
  const { openDatasetId } = useDrawerStore();

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <Container maxWidth="md" sx={{ px: 3, py: 8 }}>
          <Box sx={{ textAlign: 'center', mb: 5 }}>
            <Typography variant="h3" fontWeight={700} gutterBottom>Find Your Data</Typography>
            <Typography variant="h6" color="text.secondary" fontWeight={400}>
              Search thousands of datasets, data products, and schemas
            </Typography>
          </Box>

          <SearchBar ref={localRef} large />

          <Box sx={{ textAlign: 'center', mt: 1.5 }}>
            <Typography variant="caption" color="text.disabled">
              Press{' '}
              <Box component="kbd" sx={{ px: 0.75, py: 0.25, bgcolor: 'grey.100', borderRadius: 0.5, fontFamily: 'monospace', fontSize: 12 }}>/</Box>
              {' '}to focus search ·{' '}
              <Box component="kbd" sx={{ px: 0.75, py: 0.25, bgcolor: 'grey.100', borderRadius: 0.5, fontFamily: 'monospace', fontSize: 12 }}>⌘K</Box>
              {' '}to open AI chat
            </Typography>
          </Box>

          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 1.5 }}>
            <Typography
              component={Link}
              to="/bookmarks"
              variant="caption"
              color="warning.main"
              sx={{ display: 'flex', alignItems: 'center', gap: 0.75, textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
            >
              ★ My Bookmarks
            </Typography>
          </Box>

          <Box sx={{ mt: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <RecentlyViewed />
            <TrendingDatasets />
          </Box>
        </Container>
      </Box>

      {openDatasetId && <DatasetDetailDrawer />}
    </Box>
  );
}
