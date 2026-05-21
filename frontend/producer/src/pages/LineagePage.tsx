import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { lineageApi } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import LineageGraph from '../components/lineage/LineageGraph';

export default function LineagePage() {
  const [namespace, setNamespace] = useState('');
  const [name, setName] = useState('');
  const [submitted, setSubmitted] = useState<{ ns: string; name: string } | null>(null);
  const [direction, setDirection] = useState<'upstream' | 'downstream'>('downstream');

  const { data: graph, isLoading } = useQuery({
    queryKey: ['lineage', submitted?.ns, submitted?.name, direction],
    queryFn: () => lineageApi.getDatasetLineage(submitted!.ns, submitted!.name, direction, 5),
    enabled: !!submitted,
  });

  return (
    <div className="flex flex-col h-full">
      <PageHeader title="Lineage Explorer" description="Explore data lineage across your catalog" />
      <div className="p-4 bg-white border-b flex items-end gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Namespace</label>
          <input
            value={namespace}
            onChange={e => setNamespace(e.target.value)}
            placeholder="e.g. snowflake://my-account/mydb"
            className="border border-gray-300 rounded px-3 py-1.5 text-sm w-64 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Dataset name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="e.g. trades"
            className="border border-gray-300 rounded px-3 py-1.5 text-sm w-48 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Direction</label>
          <select
            value={direction}
            onChange={e => setDirection(e.target.value as 'upstream' | 'downstream')}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="downstream">Downstream</option>
            <option value="upstream">Upstream</option>
          </select>
        </div>
        <button
          onClick={() => setSubmitted({ ns: namespace, name })}
          disabled={!namespace || !name}
          className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          Explore
        </button>
      </div>
      <div className="flex-1 relative">
        {isLoading && <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">Loading lineage...</div>}
        {graph && <LineageGraph graph={graph} />}
        {!graph && !isLoading && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
            Enter a dataset namespace and name to explore its lineage
          </div>
        )}
      </div>
    </div>
  );
}
