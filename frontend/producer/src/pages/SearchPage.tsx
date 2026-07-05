import { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuList from '@mui/material/MenuList';
import MenuItem from '@mui/material/MenuItem';
import ClickAwayListener from '@mui/material/ClickAwayListener';
import SearchIcon from '@mui/icons-material/Search';
import { searchApi, PageHeader } from '@datacatalog/shared';
import SearchResultCard from '../components/search/SearchResultCard';

type TypeFilter = '' | 'DATASET' | 'DATA_PRODUCT' | 'DISTRIBUTION';

const TYPE_PILLS: { label: string; value: TypeFilter }[] = [
  { label: 'All', value: '' },
  { label: 'Data Product', value: 'DATA_PRODUCT' },
  { label: 'Dataset', value: 'DATASET' },
  { label: 'Distribution', value: 'DISTRIBUTION' },
];

const PAGE_SIZE = 20;

function useDebounce(value: string, delay: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

export default function SearchPage() {
  const [inputValue, setInputValue] = useState('');
  const [query, setQuery] = useState('');
  const [type, setType] = useState<TypeFilter>('');
  const [page, setPage] = useState(0);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const debouncedInput = useDebounce(inputValue, 250);

  const { data: searchData, isLoading } = useQuery({
    queryKey: ['search', query, type, page],
    queryFn: () => searchApi.search({ q: query || undefined, type: type || undefined, page, size: PAGE_SIZE }),
    placeholderData: prev => prev,
  });

  const { data: suggestions = [] } = useQuery({
    queryKey: ['suggest', debouncedInput],
    queryFn: () => searchApi.suggest(debouncedInput),
    enabled: debouncedInput.length >= 2,
  });

  const results = searchData?.results ?? [];
  const total = searchData?.total ?? 0;
  const totalPages = Math.ceil(total / PAGE_SIZE);

  function handleSearch() {
    setQuery(inputValue);
    setPage(0);
    setShowSuggestions(false);
  }

  function handleSuggestionSelect(s: string) {
    setInputValue(s);
    setQuery(s);
    setPage(0);
    setShowSuggestions(false);
    inputRef.current?.focus();
  }

  const showDropdown = showSuggestions && suggestions.length > 0;

  return (
    <Box>
      <PageHeader title="Search" description="Find datasets, data products, and distributions across the catalog" />

      <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
        {/* Search input */}
        <Box sx={{ maxWidth: 640 }}>
          <ClickAwayListener onClickAway={() => setShowSuggestions(false)}>
            <Box sx={{ position: 'relative' }}>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <TextField
                  inputRef={inputRef}
                  type="search"
                  value={inputValue}
                  onChange={e => { setInputValue(e.target.value); setShowSuggestions(true); }}
                  onKeyDown={e => {
                    if (e.key === 'Enter') handleSearch();
                    if (e.key === 'Escape') setShowSuggestions(false);
                  }}
                  placeholder="Search the catalog…"
                  size="small"
                  fullWidth
                  InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" color="disabled" /></InputAdornment> }}
                />
                <Button variant="contained" onClick={handleSearch} sx={{ textTransform: 'none', flexShrink: 0 }}>
                  Search
                </Button>
              </Box>
              {showDropdown && (
                <Paper
                  variant="outlined"
                  sx={{ position: 'absolute', top: '100%', left: 0, right: 0, mt: 0.5, zIndex: 10, overflow: 'hidden' }}
                >
                  <MenuList dense>
                    {suggestions.map(s => (
                      <MenuItem key={s} onMouseDown={() => handleSuggestionSelect(s)}>
                        <Typography variant="body2">{s}</Typography>
                      </MenuItem>
                    ))}
                  </MenuList>
                </Paper>
              )}
            </Box>
          </ClickAwayListener>
        </Box>

        {/* Type filter chips */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          {TYPE_PILLS.map(pill => (
            <Chip
              key={pill.value}
              label={pill.label}
              color={type === pill.value ? 'primary' : 'default'}
              variant={type === pill.value ? 'filled' : 'outlined'}
              size="small"
              onClick={() => { setType(pill.value); setPage(0); }}
              sx={{ cursor: 'pointer' }}
            />
          ))}
          {total > 0 && (
            <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
              {total.toLocaleString()} {total === 1 ? 'result' : 'results'}
            </Typography>
          )}
        </Box>

        {/* Results */}
        {isLoading ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {[...Array(5)].map((_, i) => <Skeleton key={i} variant="rounded" height={80} />)}
          </Box>
        ) : results.length > 0 ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {results.map(r => <SearchResultCard key={r.id} result={r} />)}
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 8 }}>
            {query
              ? `No results for "${query}"${type ? ` in ${type.replace('_', ' ').toLowerCase()}s` : ''}.`
              : 'Enter a search term or browse by asset type above.'}
          </Typography>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pt: 1 }}>
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
