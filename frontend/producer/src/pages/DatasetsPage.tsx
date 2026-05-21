import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { datasetApi } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import { formatDate } from '../lib/utils';

export default function DatasetsPage() {
  const { tenant } = useParams();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 20;

  const { data: pageData } = useQuery({
    queryKey: ['datasets', page],
    queryFn: () => datasetApi.list({ page, size: PAGE_SIZE }),
  });

  const datasets = pageData?.content ?? [];
  const totalPages = pageData?.totalPages ?? 0;
  const totalElements = pageData?.totalElements ?? 0;

  const filtered = search
    ? datasets.filter(ds =>
        ds.title?.toLowerCase().includes(search.toLowerCase()) ||
        ds.description?.toLowerCase().includes(search.toLowerCase()) ||
        ds.keywords?.some(k => k.toLowerCase().includes(search.toLowerCase()))
      )
    : datasets;

  return (
    <div>
      <PageHeader
        title="Datasets"
        description={`${totalElements} datasets in the catalog`}
      />

      <div className="p-6">
        <input
          type="search"
          placeholder="Filter by title, description, or keyword…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full max-w-md px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 mb-4"
        />

        <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Title</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Keywords</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Frequency</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map(ds => (
                <tr key={ds.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <Link
                      to={`/${tenant}/datasets/${ds.id}`}
                      className="font-medium text-blue-600 hover:underline"
                    >
                      {ds.title}
                    </Link>
                    {ds.description && (
                      <p className="text-xs text-gray-500 mt-0.5 line-clamp-1">{ds.description}</p>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {(ds.keywords ?? []).slice(0, 3).map(kw => (
                        <span key={kw} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">
                          {kw}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{ds.accrualPeriodicity ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(ds.updatedAt)}</td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-10 text-center text-sm text-gray-400">
                    {search ? 'No datasets match your filter.' : 'No datasets found.'}
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
