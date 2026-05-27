import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { datasetApi } from '@datacatalog/shared';
import type { DatasetAuditEntry } from '@datacatalog/shared';
import JsonDiffView from './JsonDiffView';
import Button from '../ui/Button';

const EVENT_COLORS: Record<string, string> = {
  CREATED:                   'bg-green-100 text-green-700',
  UPDATED:                   'bg-blue-100 text-blue-700',
  DELETED:                   'bg-red-100 text-red-700',
  OWNER_ASSIGNED:            'bg-purple-100 text-purple-700',
  OWNER_TRANSFER_PROPOSED:   'bg-amber-100 text-amber-700',
  OWNER_TRANSFER_APPROVED:   'bg-teal-100 text-teal-700',
  OWNER_TRANSFER_REJECTED:   'bg-gray-100 text-gray-700',
};

function formatTs(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: 'medium', timeStyle: 'short',
  });
}

function AuditEntryRow({ entry }: { entry: DatasetAuditEntry }) {
  const [expanded, setExpanded] = useState(false);
  const colorClass = EVENT_COLORS[entry.eventType] ?? 'bg-gray-100 text-gray-600';
  const hasDiff = entry.payloadBefore != null || entry.payloadAfter != null;

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <button
        className="w-full text-left px-4 py-3 flex items-center gap-3 hover:bg-gray-50 transition-colors"
        onClick={() => hasDiff && setExpanded(e => !e)}
        disabled={!hasDiff}
      >
        <span className={`px-2 py-0.5 text-xs font-medium rounded ${colorClass}`}>
          {entry.eventType.replace(/_/g, ' ')}
        </span>
        <span className="text-xs text-gray-500 flex-1 text-left">
          {entry.changedByEmail ?? entry.changedById ?? 'system'}
        </span>
        <span className="text-xs text-gray-400">{formatTs(entry.createdAt)}</span>
        {hasDiff && (
          <span className="text-gray-400 text-xs">{expanded ? '▲' : '▼'}</span>
        )}
      </button>
      {expanded && hasDiff && (
        <div className="px-4 pb-4 border-t border-gray-100">
          <JsonDiffView before={entry.payloadBefore} after={entry.payloadAfter} />
        </div>
      )}
    </div>
  );
}

interface DatasetHistoryTabProps {
  datasetId: string;
}

export default function DatasetHistoryTab({ datasetId }: DatasetHistoryTabProps) {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['dataset-history', datasetId, page],
    queryFn: () => datasetApi.getHistory(datasetId, page, 20),
  });

  if (isLoading) return <div className="text-sm text-gray-400">Loading history...</div>;
  if (!data || data.content.length === 0) {
    return <p className="text-sm text-gray-500">No audit history yet.</p>;
  }

  return (
    <div className="max-w-4xl space-y-3">
      <p className="text-xs text-gray-400">{data.totalElements} total entries</p>
      {data.content.map(entry => (
        <AuditEntryRow key={entry.id} entry={entry} />
      ))}
      {data.totalPages > 1 && (
        <div className="flex items-center gap-3 pt-2">
          <Button
            size="sm"
            variant="secondary"
            onClick={() => setPage(p => p - 1)}
            disabled={page === 0}
          >
            ← Newer
          </Button>
          <span className="text-xs text-gray-500">
            Page {page + 1} of {data.totalPages}
          </span>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => setPage(p => p + 1)}
            disabled={page >= data.totalPages - 1}
          >
            Older →
          </Button>
        </div>
      )}
    </div>
  );
}
