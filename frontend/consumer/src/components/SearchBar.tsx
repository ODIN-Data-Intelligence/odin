import { useState, useEffect, useRef, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';
import Paper from '@mui/material/Paper';
import InputBase from '@mui/material/InputBase';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import MenuItem from '@mui/material/MenuItem';
import MenuList from '@mui/material/MenuList';
import ClickAwayListener from '@mui/material/ClickAwayListener';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { searchApi } from '@datacatalog/shared';
import { useSearchStore } from '../store/searchStore';

interface SearchBarProps {
  large?: boolean;
}

const SearchBar = forwardRef<HTMLInputElement, SearchBarProps>(function SearchBar({ large = false }, ref) {
  const { query, setQuery } = useSearchStore();
  const [localQuery, setLocalQuery] = useState(query);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const navigate = useNavigate();
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    clearTimeout(debounceRef.current);
    if (localQuery.length < 2) { setSuggestions([]); return; }
    debounceRef.current = setTimeout(async () => {
      try {
        const s = await searchApi.suggest(localQuery);
        setSuggestions(s.slice(0, 8));
        setShowSuggestions(true);
      } catch { /* ignore */ }
    }, 250);
  }, [localQuery]);

  function submit(value: string) {
    setQuery(value);
    setShowSuggestions(false);
    navigate(`/search?q=${encodeURIComponent(value)}`);
  }

  return (
    <ClickAwayListener onClickAway={() => setShowSuggestions(false)}>
      <Paper
        elevation={large ? 3 : 1}
        sx={{
          display: 'flex',
          alignItems: 'center',
          borderRadius: 3,
          px: 1,
          py: large ? 0.75 : 0.25,
          position: 'relative',
        }}
      >
        <SearchIcon color="action" sx={{ ml: 0.5, mr: 0.5 }} />
        <InputBase
          inputRef={ref}
          value={localQuery}
          onChange={e => setLocalQuery(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter') submit(localQuery);
            if (e.key === 'Escape') setShowSuggestions(false);
          }}
          onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
          placeholder="Search datasets, data products, schemas..."
          sx={{ flex: 1, fontSize: large ? 18 : 14, py: large ? 0.5 : 0 }}
          inputProps={{ 'aria-label': 'search' }}
        />
        {localQuery && (
          <>
            <IconButton size="small" onClick={() => { setLocalQuery(''); setQuery(''); }}>
              <ClearIcon fontSize="small" />
            </IconButton>
            <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          </>
        )}
        <Button
          variant="contained"
          size={large ? 'medium' : 'small'}
          onClick={() => submit(localQuery)}
          disableElevation
          sx={{ borderRadius: 2, ml: 0.5 }}
        >
          Search
        </Button>

        {showSuggestions && suggestions.length > 0 && (
          <Paper
            elevation={4}
            sx={{ position: 'absolute', top: '100%', left: 0, right: 0, mt: 0.5, zIndex: 20, borderRadius: 2, overflow: 'hidden' }}
          >
            <MenuList dense>
              {suggestions.map(s => (
                <MenuItem key={s} onMouseDown={() => submit(s)} sx={{ gap: 1, fontSize: 14 }}>
                  <SearchIcon fontSize="small" color="action" /> {s}
                </MenuItem>
              ))}
            </MenuList>
          </Paper>
        )}
      </Paper>
    </ClickAwayListener>
  );
});

export default SearchBar;
