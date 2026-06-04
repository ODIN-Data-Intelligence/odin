import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { harvestRunApi } from '@datacatalog/shared';
import type { HarvestRun } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';
import { formatDateTime, RUN_STATUS_COLORS } from '../../lib/utils';

export default function HarvestRunDetailPage() {
  const { id } = useParams();

  const { data: run, refetch } = useQuery({
    queryKey: ['harvest-run', id],
    queryFn: () => harvestRunApi.get(id!),
    enabled: !!id,
    refetchInterval: (query) => {
      const status = (query.state.data as HarvestRun | undefined)?.status;
      return status === 'running' || status === 'pending' ? 3000 : false;
    },
  });

  const { data: items = [] } = useQuery({
    queryKey: ['harvest-run-items', id],
    queryFn: () => harvestRunApi.listItems(id!),
    enabled: !!id && run?.status === 'completed',
  });

  if (!run) return <div className="p-6 text-sm text-gray-500">Loading...</div>;

  const progress = run.entitiesDiscovered
    ? Math.round(((run.entitiesCreated ?? 0) + (run.entitiesUpdated ?? 0)) / run.entitiesDiscovered * 100)
    : 0;

  return (
    <div>
      <PageHeader
        title={`Run ${run.id.slice(0, 8)}…`}
        description={`Triggered by: ${run.triggeredBy ?? 'system'}`}
        actions={<Badge label={run.status} className={RUN_STATUS_COLORS[run.status]} />}
      />

      <div className="p-6 space-y-6">
        {run.status === 'running' && (
          <div>
            <div className="flex justify-between text-xs text-gray-500 mb-1">
              <span>Progress</span>
              <span>{progress}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div className="bg-blue-600 h-2 rounded-full transition-all" style={{ width: `${progress}%` }} />
            </div>
          </div>
        )}

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <StatCard label="Discovered" value={run.entitiesDiscovered ?? 0} color="text-gray-900" />
          <StatCard label="Created" value={run.entitiesCreated ?? 0} color="text-green-600" />
          <StatCard label="Updated" value={run.entitiesUpdated ?? 0} color="text-blue-600" />
          <StatCard label="Failed" value={run.entitiesFailed ?? 0} color="text-red-600" />
        </div>

        <dl className="grid grid-cols-2 gap-3 text-sm">
          <DlItem label="Started" value={formatDateTime(run.startedAt)} />
          <DlItem label="Completed" value={formatDateTime(run.completedAt)} />
        </dl>

        {run.errorMessage && (
          <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700">
            {run.errorMessage}
          </div>
        )}

        {items.length > 0 && (
          <section>
            <h2 className="text-base font-semibold text-gray-800 mb-3">Items ({items.length})</h2>
            <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entity</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Action</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Error</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {items.map(item => (
                    <tr key={item.id} className={item.errorDetail ? 'bg-red-50' : 'hover:bg-gray-50'}>
                      <td className="px-4 py-2 font-mono text-xs truncate max-w-xs">{item.sourceKey}</td>
                      <td className="px-4 py-2 text-gray-600">{item.entityType}</td>
                      <td className="px-4 py-2">
                        {item.action && <Badge label={item.action} className="bg-gray-100 text-gray-700" />}
                      </td>
                      <td className="px-4 py-2 text-red-600 text-xs truncate max-w-xs">{item.errorDetail ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-4">
      <p className="text-xs text-gray-500">{label}</p>
      <p className={`text-2xl font-semibold mt-1 ${color}`}>{value}</p>
    </div>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-900 text-sm">{value}</dd>
    </div>
  );
}
