import { useState, useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { searchApi } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
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
  const suggestionsRef = useRef<HTMLDivElement>(null);

  const debouncedInput = useDebounce(inputValue, 250);

  const { data: searchData, isLoading } = useQuery({
    queryKey: ['search', query, type, page],
    queryFn: () => searchApi.search({
      q: query || undefined,
      type: type || undefined,
      page,
      size: PAGE_SIZE,
    }),
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

  function handleTypeChange(value: TypeFilter) {
    setType(value);
    setPage(0);
  }

  // Close suggestions on outside click
  useEffect(() => {
    function onMouseDown(e: MouseEvent) {
      if (
        suggestionsRef.current && !suggestionsRef.current.contains(e.target as Node) &&
        inputRef.current && !inputRef.current.contains(e.target as Node)
      ) {
        setShowSuggestions(false);
      }
    }
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, []);

  const showDropdown = showSuggestions && suggestions.length > 0;

  return (
    <div>
      <PageHeader title="Search" description="Find datasets, data products, and distributions across the catalog" />

      <div className="p-6 space-y-4">
        {/* Search input */}
        <div className="flex gap-2 max-w-2xl">
          <div className="relative flex-1">
            <div className="pointer-events-none absolute inset-y-0 left-3 flex items-center">
              <svg className="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
              </svg>
            </div>
            <input
              ref={inputRef}
              type="search"
              value={inputValue}
              onChange={e => {
                setInputValue(e.target.value);
                setShowSuggestions(true);
              }}
              onKeyDown={e => {
                if (e.key === 'Enter') handleSearch();
                if (e.key === 'Escape') setShowSuggestions(false);
              }}
              placeholder="Search the catalog…"
              className="w-full pl-9 pr-4 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {showDropdown && (
              <div
                ref={suggestionsRef}
                className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-10 overflow-hidden"
              >
                {suggestions.map(s => (
                  <button
                    key={s}
                    type="button"
                    onMouseDown={() => handleSuggestionSelect(s)}
                    className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-blue-50 hover:text-blue-700"
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>
          <button
            onClick={handleSearch}
            className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700"
          >
            Search
          </button>
        </div>

        {/* Type filter pills */}
        <div className="flex gap-2">
          {TYPE_PILLS.map(pill => (
            <button
              key={pill.value}
              onClick={() => handleTypeChange(pill.value)}
              className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
                type === pill.value
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {pill.label}
            </button>
          ))}
          {total > 0 && (
            <span className="ml-auto text-sm text-gray-500 self-center">
              {total.toLocaleString()} {total === 1 ? 'result' : 'results'}
            </span>
          )}
        </div>

        {/* Results */}
        {isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="bg-white border border-gray-200 rounded-lg px-5 py-4 animate-pulse">
                <div className="h-4 bg-gray-200 rounded w-1/3 mb-2" />
                <div className="h-3 bg-gray-100 rounded w-2/3" />
              </div>
            ))}
          </div>
        ) : results.length > 0 ? (
          <div className="space-y-3">
            {results.map(r => <SearchResultCard key={r.id} result={r} />)}
          </div>
        ) : (
          <div className="py-16 text-center text-gray-400">
            {query
              ? `No results for "${query}"${type ? ` in ${type.replace('_', ' ').toLowerCase()}s` : ''}.`
              : 'Enter a search term or browse by asset type above.'}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-2 text-sm text-gray-600">
            <span>Page {page + 1} of {totalPages}</span>
            <div className="flex gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 border border-gray-300 rounded disabled:opacity-40 hover:bg-gray-50"
              >
                Previous
              </button>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 border border-gray-300 rounded disabled:opacity-40 hover:bg-gray-50"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
