import { Link, useParams } from 'react-router-dom';
import type { SearchResult } from '@datacatalog/shared';
import { formatDate, LIFECYCLE_COLORS } from '../../lib/utils';

const TYPE_BADGE: Record<string, { label: string; cls: string }> = {
  DATASET:      { label: 'Dataset',      cls: 'bg-blue-100 text-blue-700' },
  DATA_PRODUCT: { label: 'Data Product', cls: 'bg-purple-100 text-purple-700' },
  DISTRIBUTION: { label: 'Distribution', cls: 'bg-gray-100 text-gray-600' },
};

interface Props {
  result: SearchResult;
}

export default function SearchResultCard({ result }: Props) {
  const { tenant } = useParams();
  const badge = TYPE_BADGE[result.entityType] ?? TYPE_BADGE.DATASET;

  const href = (() => {
    if (result.entityType === 'DATASET') return `/${tenant}/datasets/${result.id}`;
    if (result.entityType === 'DATA_PRODUCT') return `/${tenant}/data-products/${result.id}`;
    // Distributions have no datasetId in the search result — link to datasets list
    return `/${tenant}/datasets`;
  })();

  const lifecycle = result.lifecycleStatus;

  return (
    <div className="bg-white border border-gray-200 rounded-lg px-5 py-4 hover:border-blue-300 hover:shadow-sm transition-all">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          {/* Row 1: title + type badge */}
          <div className="flex items-center gap-2 mb-1">
            <Link
              to={href}
              className="text-sm font-semibold text-blue-600 hover:underline truncate"
            >
              {result.title}
            </Link>
            <span className={`shrink-0 px-2 py-0.5 rounded text-xs font-medium ${badge.cls}`}>
              {badge.label}
            </span>
            {result.format && (
              <span className="shrink-0 px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-600">
                {result.format}
              </span>
            )}
          </div>

          {/* Row 2: description */}
          {result.description && (
            <p className="text-xs text-gray-500 line-clamp-1 mb-2">{result.description}</p>
          )}

          {/* Row 3: keywords + lifecycle + date */}
          <div className="flex items-center gap-2 flex-wrap">
            {(result.keywords ?? []).slice(0, 4).map(kw => (
              <span key={kw} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                {kw}
              </span>
            ))}
            {lifecycle && (
              <span className={`px-2 py-0.5 rounded text-xs font-medium ${LIFECYCLE_COLORS[lifecycle] ?? 'bg-gray-100 text-gray-600'}`}>
                {lifecycle}
              </span>
            )}
            {result.updatedAt && (
              <span className="text-xs text-gray-400 ml-auto">{formatDate(result.updatedAt)}</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
