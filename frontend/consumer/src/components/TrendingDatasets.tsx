import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { searchApi } from '@datacatalog/shared';

const TYPE_COLORS: Record<string, string> = {
  DATASET:      'bg-blue-100 text-blue-700',
  DATA_PRODUCT: 'bg-purple-100 text-purple-700',
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
    <div>
      <h2 className="text-base font-semibold text-gray-800 mb-3">Trending Datasets</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        {results.map(r => (
          <button
            key={r.id}
            onClick={() => navigate(`/search?ds=${r.id}`)}
            className="text-left p-3 bg-white border border-gray-200 rounded-lg hover:border-blue-300 hover:shadow-sm transition-all"
          >
            <div className="flex items-start justify-between gap-2 mb-1">
              <p className="font-medium text-sm text-gray-900 truncate">{r.title}</p>
              <span className={`text-xs px-1.5 py-0.5 rounded flex-shrink-0 ${TYPE_COLORS[r.entityType] ?? 'bg-gray-100 text-gray-600'}`}>
                {r.entityType === 'DATA_PRODUCT' ? 'product' : 'dataset'}
              </span>
            </div>
            {r.description && <p className="text-xs text-gray-500 line-clamp-2">{r.description}</p>}
          </button>
        ))}
      </div>
    </div>
  );
}
