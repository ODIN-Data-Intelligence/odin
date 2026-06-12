import { useRef, useEffect } from 'react';
import { useSearchParams, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import Pagination from '@mui/material/Pagination';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { searchApi } from '@datacatalog/shared';
import SearchBar from '../components/SearchBar';
import FacetPanel from '../components/FacetPanel';
import DatasetSummaryCard from '../components/DatasetSummaryCard';
import DatasetDetailDrawer from '../components/DatasetDetailDrawer';
import { useSearchStore } from '../store/searchStore';
import { useDrawerStore } from '../store/drawerStore';

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;

export default function SearchPage() {
  const searchBarRef = useRef<HTMLInputElement>(null);
  const [searchParams] = useSearchParams();
  const { id: pathDatasetId } = useParams<{ id: string }>();
  const { query, filters, page, size, setQuery, setPage, setSize } = useSearchStore();
  const { openDatasetId, openDataset } = useDrawerStore();

  useEffect(() => {
    const urlQ = searchParams.get('q');
    if (urlQ && urlQ !== query) setQuery(urlQ);

    const dsId = pathDatasetId ?? searchParams.get('ds');
    if (dsId) openDataset(dsId);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { data, isLoading } = useQuery({
    queryKey: ['search', query, filters, page, size],
    queryFn: () => searchApi.search({ q: query, ...filters, page, size }),
    enabled: true,
  });

  const results = data?.results ?? [];
  const facets = data?.facets ?? {};
  const total = data?.total ?? 0;
  const pageCount = Math.ceil(total / size);
  const firstItem = total > 0 ? page * size + 1 : 0;
  const lastItem = Math.min((page + 1) * size, total);

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden', flexDirection: 'column' }}>
      {/* Top bar */}
      <Box sx={{ bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider', px: 3, py: 1.5, display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
        <Box sx={{ flex: 1, maxWidth: 640 }}>
          <SearchBar ref={searchBarRef} />
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ flexShrink: 0, minWidth: 80, textAlign: 'right' }}>
          {isLoading ? 'Searching…' : total > 0 ? `${total.toLocaleString()} results` : ''}
        </Typography>
      </Box>

      {/* Body */}
      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Facets + Results */}
        <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden', width: openDatasetId ? '50%' : '100%' }}>
          {/* Facets sidebar */}
          <Box sx={{ width: 240, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'grey.50', overflowY: 'auto', px: 2, py: 2.5 }}>
            <FacetPanel facets={facets} />
          </Box>

          {/* Results column: scrollable list + fixed pagination bar */}
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            {/* Scrollable results */}
            <Box sx={{ flex: 1, overflowY: 'auto', px: 2.5, py: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
              {isLoading ? (
                [...Array(Math.min(size, 5))].map((_, i) => <Skeleton key={i} variant="rounded" height={174} />)
              ) : results.length === 0 ? (
                <Box sx={{ textAlign: 'center', py: 10 }}>
                  <Typography variant="h6" color="text.secondary" gutterBottom>No results found</Typography>
                  <Typography variant="body2" color="text.disabled">
                    Try different keywords or clear some filters
                  </Typography>
                </Box>
              ) : (
                results.map(result => (
                  <DatasetSummaryCard
                    key={result.id}
                    result={result}
                    isActive={openDatasetId === result.id}
                  />
                ))
              )}
            </Box>

            {/* Pagination bar */}
            {!isLoading && total > 0 && (
              <Box
                sx={{
                  flexShrink: 0,
                  borderTop: 1,
                  borderColor: 'divider',
                  bgcolor: 'background.paper',
                  px: 2.5,
                  py: 1.25,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 2,
                }}
              >
                <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                  {firstItem}–{lastItem} of {total.toLocaleString()}
                </Typography>

                <Pagination
                  count={pageCount}
                  page={page + 1}
                  onChange={(_, p) => setPage(p - 1)}
                  size="small"
                  color="primary"
                  siblingCount={1}
                  boundaryCount={1}
                />

                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
                  <Typography variant="caption" color="text.secondary">Per page:</Typography>
                  <Select
                    value={size}
                    onChange={e => setSize(Number(e.target.value))}
                    size="small"
                    sx={{ fontSize: 12, '.MuiSelect-select': { py: 0.5, px: 1.25 } }}
                  >
                    {PAGE_SIZE_OPTIONS.map(n => (
                      <MenuItem key={n} value={n} sx={{ fontSize: 13 }}>{n}</MenuItem>
                    ))}
                  </Select>
                </Box>
              </Box>
            )}
          </Box>
        </Box>

        {/* Drawer */}
        {openDatasetId && <DatasetDetailDrawer />}
      </Box>
    </Box>
  );
}
