import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { datasetApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { Distribution, CsvwColumn, LogicalDataElement } from '@datacatalog/shared';

function useDistributionSchema(distId: string) {
  return useQuery({
    queryKey: ['distribution-schema', distId],
    queryFn: () => datasetApi.getDistributionPhysicalSchema(distId),
  });
}

const FORMAT_COLORS: Record<string, string> = {
  Snowflake:  'bg-blue-100 text-blue-800',
  Parquet:    'bg-yellow-100 text-yellow-800',
  CSV:        'bg-green-100 text-green-800',
  JSON:       'bg-purple-100 text-purple-800',
  Kafka:      'bg-orange-100 text-orange-800',
  'ISO 20022 XML': 'bg-red-100 text-red-800',
  XML:        'bg-red-100 text-red-800',
};

const FORMAT_ICON: Record<string, string> = {
  Snowflake: '❄',
  Parquet:   '⬡',
  CSV:       '⊞',
  JSON:      '{}',
  Kafka:     '⚡',
  XML:       '◇',
  'ISO 20022 XML': '◇',
};

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  function copy() {
    navigator.clipboard.writeText(value).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }
  return (
    <button
      onClick={copy}
      title={`Copy ${label}`}
      className="ml-1 px-1.5 py-0.5 text-xs text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded transition-colors"
    >
      {copied ? '✓' : '⎘'}
    </button>
  );
}

function DistributionCard({ dist, elements }: { dist: Distribution; elements: LogicalDataElement[] }) {
  const url = dist.accessUrl ?? dist.downloadUrl;
  const colorClass = FORMAT_COLORS[dist.format ?? ''] ?? 'bg-gray-100 text-gray-700';
  const icon = FORMAT_ICON[dist.format ?? ''] ?? '⊡';
  const { data: columns = [], isLoading: schemaLoading } = useDistributionSchema(dist.id);

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">
          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold flex-shrink-0 ${colorClass}`}>
            <span>{icon}</span>
            {dist.format ?? dist.mediaType ?? 'File'}
          </span>
          <span className="text-sm font-medium text-gray-900 truncate">{dist.title}</span>
        </div>
        {dist.availability && (
          <span className={`text-xs px-2 py-0.5 rounded-full flex-shrink-0 ${dist.availability === 'available' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
            {dist.availability}
          </span>
        )}
      </div>

      <div className="px-4 py-3 space-y-3">
        {dist.description && (
          <p className="text-sm text-gray-600">{dist.description}</p>
        )}

        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-xs">
          {dist.mediaType && (
            <div>
              <dt className="text-gray-400 font-medium">Media type</dt>
              <dd className="text-gray-700 font-mono mt-0.5">{dist.mediaType}</dd>
            </div>
          )}
          {dist.byteSize != null && (
            <div>
              <dt className="text-gray-400 font-medium">Size</dt>
              <dd className="text-gray-700 mt-0.5">{formatBytes(dist.byteSize)}</dd>
            </div>
          )}
          {dist.checksumAlgorithm && dist.checksumValue && (
            <div className="col-span-2">
              <dt className="text-gray-400 font-medium">{dist.checksumAlgorithm} checksum</dt>
              <dd className="text-gray-700 font-mono mt-0.5 break-all text-xs">{dist.checksumValue}</dd>
            </div>
          )}
        </dl>

        {url && (
          <div className="bg-gray-50 rounded px-3 py-2 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="text-xs text-gray-400 mb-0.5">{dist.accessUrl ? 'Access URL' : 'Download URL'}</p>
              <p className="text-xs font-mono text-gray-700 truncate">{url}</p>
            </div>
            <div className="flex items-center gap-1 flex-shrink-0">
              <CopyButton value={url} label="URL" />
              {dist.downloadUrl && (
                <a
                  href={dist.downloadUrl}
                  target="_blank"
                  rel="noreferrer"
                  title="Download"
                  className="px-1.5 py-0.5 text-xs text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded"
                >
                  ↓
                </a>
              )}
              {dist.accessUrl && (
                <a
                  href={dist.accessUrl}
                  target="_blank"
                  rel="noreferrer"
                  title="Open"
                  className="px-1.5 py-0.5 text-xs text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded"
                >
                  ↗
                </a>
              )}
            </div>
          </div>
        )}

        {(dist.databaseName || dist.schemaName || dist.tableName) && (
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
            <p className="text-xs font-medium text-gray-400 mb-2">Table Location</p>
            <code className="text-sm font-mono font-semibold text-gray-900">
              {[dist.databaseName, dist.schemaName, dist.tableName].filter(Boolean).join('.')}
            </code>
            <dl className="mt-2 flex gap-5 text-xs">
              {dist.databaseName && (
                <div>
                  <dt className="text-gray-400 font-medium">Database</dt>
                  <dd className="text-gray-700 font-mono mt-0.5">{dist.databaseName}</dd>
                </div>
              )}
              {dist.schemaName && (
                <div>
                  <dt className="text-gray-400 font-medium">Schema</dt>
                  <dd className="text-gray-700 font-mono mt-0.5">{dist.schemaName}</dd>
                </div>
              )}
              {dist.tableName && (
                <div>
                  <dt className="text-gray-400 font-medium">Table</dt>
                  <dd className="text-gray-700 font-mono mt-0.5">{dist.tableName}</dd>
                </div>
              )}
            </dl>
          </div>
        )}

        <PhysicalSchemaSection columns={columns} isLoading={schemaLoading} elements={elements} />
      </div>
    </div>
  );
}

function PhysicalSchemaSection({ columns, isLoading, elements = [] }: {
  columns: CsvwColumn[];
  isLoading: boolean;
  elements?: LogicalDataElement[];
}) {
  const [expanded, setExpanded] = useState(false);

  if (isLoading) {
    return (
      <div className="space-y-2">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-8 bg-gray-100 rounded animate-pulse" />
        ))}
      </div>
    );
  }

  if (columns.length === 0) {
    return (
      <div className="border border-dashed border-gray-200 rounded-lg px-4 py-6 text-center">
        <p className="text-sm text-gray-400">No physical schema harvested yet.</p>
        <p className="text-xs text-gray-300 mt-1">
          Configure a harvest source and trigger a run to populate column-level details.
        </p>
      </div>
    );
  }

  const preview = expanded ? columns : columns.slice(0, 8);

  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <div className="px-4 py-2.5 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
        <p className="text-xs font-medium text-gray-600">Physical columns · {columns.length} total</p>
        <span className="text-xs text-gray-400 font-mono">CSV-W</span>
      </div>
      <table className="min-w-full text-xs">
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide w-6">#</th>
            <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide">Column</th>
            <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide">Datatype</th>
            <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide">Nullable</th>
            <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide">Description</th>
            {elements.length > 0 && (
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-400 uppercase tracking-wide">Logical Element</th>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100 bg-white">
          {preview.map(col => (
            <tr key={col.id} className="hover:bg-gray-50">
              <td className="px-3 py-2 text-gray-300 tabular-nums">{col.ordinal + 1}</td>
              <td className="px-3 py-2">
                <span className="font-mono font-medium text-gray-800">{col.name}</span>
                {col.titles && col.titles.length > 0 && col.titles[0] !== col.name && (
                  <span className="ml-1.5 text-gray-400">({col.titles[0]})</span>
                )}
              </td>
              <td className="px-3 py-2">
                {col.datatype
                  ? <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 rounded font-mono">{col.datatype}</span>
                  : <span className="text-gray-300">—</span>
                }
              </td>
              <td className="px-3 py-2">
                {col.required
                  ? <span className="text-red-500 font-medium">NOT NULL</span>
                  : <span className="text-gray-400">nullable</span>
                }
              </td>
              <td className="px-3 py-2 text-gray-500 max-w-[160px] truncate" title={col.description ?? ''}>
                {col.description ?? <span className="text-gray-300">—</span>}
              </td>
              {elements.length > 0 && (
                <td className="px-3 py-2">
                  {col.logicalDataElementId
                    ? (() => {
                        const el = elements.find(e => e.id === col.logicalDataElementId);
                        return el
                          ? (
                            <Link
                              to={`/search?q=${encodeURIComponent(el.name)}`}
                              title={`Search all datasets with element "${el.name}"`}
                              className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded text-xs font-medium hover:bg-indigo-100 hover:text-indigo-900 transition-colors"
                            >
                              {el.name}
                            </Link>
                          )
                          : <span className="text-gray-300">—</span>;
                      })()
                    : <span className="text-gray-300">—</span>
                  }
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      {columns.length > 8 && (
        <div className="px-4 py-2 border-t border-gray-100 bg-gray-50 text-center">
          <button
            onClick={() => setExpanded(e => !e)}
            className="text-xs text-blue-600 hover:text-blue-700"
          >
            {expanded ? `Show fewer ▲` : `Show all ${columns.length} columns ▼`}
          </button>
        </div>
      )}
    </div>
  );
}

export default function DistributionsTab({ datasetId }: { datasetId: string }) {
  const { data: distributions = [], isLoading: distLoading } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  const { data: logicalModels = [] } = useQuery({
    queryKey: ['logical-models', datasetId],
    queryFn: () => logicalModelApi.list(datasetId),
    enabled: distributions.length > 0,
  });

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', logicalModels[0]?.id],
    queryFn: () => logicalElementApi.list(logicalModels[0]!.id),
    enabled: logicalModels.length > 0,
  });

  if (distLoading) {
    return (
      <div className="space-y-3">
        {[...Array(2)].map((_, i) => <div key={i} className="h-24 bg-gray-100 rounded-lg animate-pulse" />)}
      </div>
    );
  }

  if (distributions.length === 0) {
    return (
      <div className="text-center py-10">
        <p className="text-sm text-gray-400">No distributions registered for this dataset.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {distributions.map(dist => (
        <DistributionCard key={dist.id} dist={dist} elements={elements} />
      ))}
    </div>
  );
}
