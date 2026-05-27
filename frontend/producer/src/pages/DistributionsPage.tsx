import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { distributionApi } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import { cn, formatDate } from '../lib/utils';

const FORMAT_COLORS: Record<string, string> = {
  Parquet:   'bg-orange-100 text-orange-700',
  CSV:       'bg-green-100 text-green-700',
  JSON:      'bg-yellow-100 text-yellow-700',
  Avro:      'bg-purple-100 text-purple-700',
  ORC:       'bg-cyan-100 text-cyan-700',
  Kafka:     'bg-pink-100 text-pink-700',
  Delta:     'bg-indigo-100 text-indigo-700',
  Snowflake: 'bg-blue-100 text-blue-700',
};

const PAGE_SIZE = 20;

export default function DistributionsPage() {
  const { tenant } = useParams();
  const [page, setPage] = useState(0);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['distributions', page],
    queryFn: () => distributionApi.list({ page, size: PAGE_SIZE }),
  });

  const distributions = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  return (
    <div>
      <PageHeader
        title="Distributions"
        description={`${totalElements} distributions across all datasets`}
      />

      <div className="p-6">
        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Format</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Title</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Dataset</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Media Type</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading && Array.from({ length: 5 }).map((_, i) => (
                <tr key={i}>
                  <td className="px-4 py-3"><div className="h-4 w-16 bg-gray-200 rounded animate-pulse" /></td>
                  <td className="px-4 py-3"><div className="h-4 w-48 bg-gray-200 rounded animate-pulse" /></td>
                  <td className="px-4 py-3"><div className="h-4 w-24 bg-gray-200 rounded animate-pulse" /></td>
                  <td className="px-4 py-3"><div className="h-4 w-32 bg-gray-200 rounded animate-pulse" /></td>
                  <td className="px-4 py-3"><div className="h-4 w-20 bg-gray-200 rounded animate-pulse" /></td>
                </tr>
              ))}
              {!isLoading && distributions.map(dist => (
                <tr key={dist.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    {dist.format ? (
                      <span className={cn('px-2 py-0.5 text-xs rounded font-medium', FORMAT_COLORS[dist.format] ?? 'bg-gray-100 text-gray-600')}>
                        {dist.format}
                      </span>
                    ) : (
                      <span className="text-gray-400 text-xs">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/${tenant}/datasets/${dist.datasetId}/distributions/${dist.id}`}
                      className="font-medium text-blue-600 hover:underline"
                    >
                      {dist.title ?? dist.id}
                    </Link>
                    {dist.description && (
                      <p className="text-xs text-gray-500 mt-0.5 line-clamp-1">{dist.description}</p>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/${tenant}/datasets/${dist.datasetId}`}
                      className="text-xs text-blue-600 hover:underline font-mono"
                    >
                      {dist.datasetId?.slice(0, 8)}…
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{dist.mediaType ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(dist.updatedAt)}</td>
                </tr>
              ))}
              {!isLoading && distributions.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-sm text-gray-400">
                    No distributions found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 text-sm text-gray-600">
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
