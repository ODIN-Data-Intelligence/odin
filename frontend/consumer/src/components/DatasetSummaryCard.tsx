import type { SearchResult } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';

interface DatasetSummaryCardProps {
  result: SearchResult;
  isActive: boolean;
}

const FORMAT_COLORS: Record<string, string> = {
  'text/csv': 'bg-green-100 text-green-700',
  'application/parquet': 'bg-blue-100 text-blue-700',
  'application/json': 'bg-yellow-100 text-yellow-700',
  SNOWFLAKE: 'bg-cyan-100 text-cyan-700',
  GLUE: 'bg-orange-100 text-orange-700',
};

export default function DatasetSummaryCard({ result, isActive }: DatasetSummaryCardProps) {
  const { openDataset } = useDrawerStore();

  function highlight(text: string): string {
    return text;
  }

  const formatLabel = result.format ?? result.mediaType;
  const formatColor = formatLabel ? (FORMAT_COLORS[formatLabel] ?? 'bg-gray-100 text-gray-600') : '';

  return (
    <button
      onClick={() => openDataset(result.id)}
      className={`w-full text-left p-4 rounded-lg border transition-all ${isActive ? 'border-blue-400 bg-blue-50 shadow-sm' : 'border-gray-200 bg-white hover:border-blue-300 hover:shadow-sm'}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="font-medium text-gray-900 text-sm truncate">{result.title}</h3>
            {formatLabel && (
              <span className={`px-1.5 py-0.5 rounded text-xs font-medium flex-shrink-0 ${formatColor}`}>{formatLabel}</span>
            )}
            {result.hasLineage && (
              <span className="px-1.5 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-700 flex-shrink-0">lineage</span>
            )}
          </div>
          {result.description && (
            <p className="mt-1 text-xs text-gray-500 line-clamp-2">{result.description}</p>
          )}
          {result.vocabConceptLabels && result.vocabConceptLabels.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {result.vocabConceptLabels.slice(0, 3).map(label => (
                <span key={label} className="px-1.5 py-0.5 bg-indigo-50 text-indigo-600 border border-indigo-100 rounded text-xs">{label}</span>
              ))}
            </div>
          )}
        </div>
        <div className="flex-shrink-0 text-right">
          <span className="text-xs text-gray-400 capitalize">{result.entityType.replace('_', ' ').toLowerCase()}</span>
          {result.lifecycleStatus && (
            <p className="text-xs text-gray-400 mt-0.5">{result.lifecycleStatus}</p>
          )}
        </div>
      </div>
    </button>
  );
}
