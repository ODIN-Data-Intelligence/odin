import { useRef, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { searchApi } from '@datacatalog/shared';
import SearchBar from '../components/SearchBar';
import FacetPanel from '../components/FacetPanel';
import DatasetSummaryCard from '../components/DatasetSummaryCard';
import DatasetDetailDrawer from '../components/DatasetDetailDrawer';
import { useSearchStore } from '../store/searchStore';
import { useDrawerStore } from '../store/drawerStore';

export default function SearchPage() {
  const searchBarRef = useRef<HTMLInputElement>(null);
  const [searchParams] = useSearchParams();
  const { query, filters, page, setQuery } = useSearchStore();
  const { openDatasetId, openDataset } = useDrawerStore();

  // Sync URL query param → store on mount
  useEffect(() => {
    const urlQ = searchParams.get('q');
    if (urlQ && urlQ !== query) setQuery(urlQ);

    const dsId = searchParams.get('ds');
    if (dsId) openDataset(dsId);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['search', query, filters, page],
    queryFn: () => searchApi.search({ q: query, ...filters, page, size: 20 }),
    enabled: true,
  });

  const results = data?.results ?? [];
  const facets = data?.facets ?? {};
  const total = data?.total ?? 0;

  return (
    <div className="flex h-screen overflow-hidden flex-col">
      {/* Top bar */}
      <div className="bg-white border-b px-6 py-3 flex items-center gap-4">
        <div className="w-full max-w-2xl">
          <SearchBar ref={searchBarRef} />
        </div>
        <p className="text-sm text-gray-500 flex-shrink-0">
          {isLoading ? 'Searching...' : total > 0 ? `${total.toLocaleString()} results` : ''}
        </p>
      </div>

      {/* Body */}
      <div className="flex flex-1 overflow-hidden">
        {/* Facets + Results */}
        <div className={`flex flex-1 overflow-hidden ${openDatasetId ? 'w-1/2' : 'w-full'}`}>
          {/* Facets sidebar */}
          <div className="w-56 flex-shrink-0 border-r bg-gray-50 overflow-y-auto px-4 py-5">
            <FacetPanel facets={facets} />
          </div>

          {/* Results list */}
          <div className="flex-1 overflow-y-auto px-5 py-4 space-y-2">
            {isLoading ? (
              [...Array(5)].map((_, i) => (
                <div key={i} className="h-20 bg-gray-100 rounded-lg animate-pulse" />
              ))
            ) : results.length === 0 ? (
              <div className="text-center py-16">
                <p className="text-gray-400 text-lg">No results found</p>
                <p className="text-sm text-gray-400 mt-1">Try different keywords or clear some filters</p>
              </div>
            ) : (
              results.map(result => (
                <DatasetSummaryCard
                  key={result.id}
                  result={result}
                  isActive={openDatasetId === result.id}
                />
              ))
            )}

            {!isLoading && results.length > 0 && (
              <p className="text-xs text-gray-400 text-center pt-2 pb-4">
                Showing {results.length} of {total.toLocaleString()} results
              </p>
            )}
          </div>
        </div>

        {/* Drawer */}
        {openDatasetId && <DatasetDetailDrawer />}
      </div>
    </div>
  );
}
