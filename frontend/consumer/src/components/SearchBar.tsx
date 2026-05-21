import { useState, useEffect, useRef, forwardRef } from 'react';
import { useNavigate } from 'react-router-dom';
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
    <div className="relative w-full">
      <div className={`flex items-center border-2 rounded-xl bg-white transition-all ${large ? 'border-gray-200 focus-within:border-blue-500 shadow-md' : 'border-gray-300 focus-within:border-blue-500'}`}>
        <span className="pl-4 text-gray-400 text-lg">🔍</span>
        <input
          ref={ref}
          type="text"
          value={localQuery}
          onChange={e => setLocalQuery(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter') submit(localQuery);
            if (e.key === 'Escape') setShowSuggestions(false);
          }}
          onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
          onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
          placeholder="Search datasets, data products, schemas..."
          className={`flex-1 px-4 bg-transparent focus:outline-none text-gray-900 placeholder-gray-400 ${large ? 'py-4 text-lg' : 'py-2.5 text-sm'}`}
        />
        {localQuery && (
          <button onClick={() => { setLocalQuery(''); setQuery(''); }} className="pr-3 text-gray-400 hover:text-gray-600 text-lg">&times;</button>
        )}
        <button
          onClick={() => submit(localQuery)}
          className={`mr-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors ${large ? 'px-5 py-2.5' : 'px-3 py-1.5 text-sm'}`}
        >
          Search
        </button>
      </div>

      {showSuggestions && suggestions.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-20 overflow-hidden">
          {suggestions.map(s => (
            <button
              key={s}
              onMouseDown={() => submit(s)}
              className="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-blue-50 flex items-center gap-2"
            >
              <span className="text-gray-400">🔍</span> {s}
            </button>
          ))}
        </div>
      )}
    </div>
  );
});

export default SearchBar;
