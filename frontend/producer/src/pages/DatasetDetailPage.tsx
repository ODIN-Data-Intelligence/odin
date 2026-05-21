import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { datasetApi, logicalModelApi, lineageApi } from '@datacatalog/shared';
import type { Distribution } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import LogicalModelEditor from '../components/catalog/LogicalModelEditor';
import PhysicalSchemaSection from '../components/catalog/PhysicalSchemaSection';
import LineageGraph from '../components/lineage/LineageGraph';
import { formatDate } from '../lib/utils';

const TABS = ['Overview', 'Distributions', 'Schema', 'Lineage'] as const;
type Tab = typeof TABS[number];

export default function DatasetDetailPage() {
  const { id, tenant } = useParams();
  const [activeTab, setActiveTab] = useState<Tab>('Overview');

  const { data: dataset, isLoading } = useQuery({
    queryKey: ['dataset', id],
    queryFn: () => datasetApi.get(id!),
    enabled: !!id,
  });

  const { data: logicalModels = [] } = useQuery({
    queryKey: ['logical-models', id],
    queryFn: () => logicalModelApi.list(id!),
    enabled: !!id && activeTab === 'Schema',
  });

  if (isLoading) return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  if (!dataset) return <div className="p-6 text-sm text-red-500">Not found</div>;

  return (
    <div>
      <PageHeader title={dataset.title} description={dataset.description} />

      <div className="border-b bg-white">
        <nav className="flex px-6 gap-1">
          {TABS.map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
            >
              {tab}
            </button>
          ))}
        </nav>
      </div>

      <div className="p-6">
        {activeTab === 'Overview' && (
          <div className="max-w-2xl space-y-4">
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <DlItem label="Updated" value={formatDate(dataset.updatedAt)} />
              {dataset.version && <DlItem label="Version" value={dataset.version} />}
              {dataset.accrualPeriodicity && <DlItem label="Accrual Periodicity" value={dataset.accrualPeriodicity} />}
            </dl>
            {dataset.keywords && dataset.keywords.length > 0 && (
              <div>
                <p className="text-xs font-medium text-gray-500 mb-1">Keywords</p>
                <div className="flex flex-wrap gap-1">
                  {dataset.keywords.map(kw => (
                    <span key={kw} className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded">{kw}</span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
        {activeTab === 'Distributions' && (
          <DistributionsTab datasetId={id!} tenant={tenant!} />
        )}
        {activeTab === 'Schema' && (
          <LogicalModelEditor datasetId={id!} models={logicalModels} />
        )}
        {activeTab === 'Lineage' && (
          <LineageTab datasetId={id!} tenant={tenant!} />
        )}
      </div>
    </div>
  );
}

function LineageTab({ datasetId, tenant }: { datasetId: string; tenant: string }) {
  const { data: identity, isLoading, isError } = useQuery({
    queryKey: ['lineage-identity', datasetId],
    queryFn: () => lineageApi.getCatalogLineageIdentity(datasetId),
    retry: false,
  });

  const { data: graph } = useQuery({
    queryKey: ['lineage-graph', identity?.namespace, identity?.name],
    queryFn: () => lineageApi.getDatasetLineage(identity!.namespace, identity!.name, 'downstream', 5),
    enabled: !!identity,
  });

  if (isLoading) return <div className="text-sm text-gray-400">Looking up lineage...</div>;
  if (isError || !identity) return (
    <div className="text-sm text-gray-500 space-y-2">
      <p>No lineage data linked to this dataset.</p>
      <Link to={`/${tenant}/lineage`} className="text-blue-600 hover:underline text-sm">
        Open lineage explorer →
      </Link>
    </div>
  );

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs text-gray-500">
          Lineage identity: <span className="font-mono">{identity.namespace}/{identity.name}</span>
        </p>
        <Link to={`/${tenant}/lineage`} className="text-xs text-blue-600 hover:underline">
          Open full explorer →
        </Link>
      </div>
      <div className="h-[calc(100vh-200px)] rounded-lg border border-gray-200 overflow-hidden">
        {graph ? <LineageGraph graph={graph} /> : <div className="h-full flex items-center justify-center text-sm text-gray-400">Loading graph...</div>}
      </div>
    </div>
  );
}

const FORMAT_COLORS: Record<string, string> = {
  Parquet: 'bg-orange-100 text-orange-700',
  CSV:     'bg-green-100 text-green-700',
  JSON:    'bg-yellow-100 text-yellow-700',
  Avro:    'bg-purple-100 text-purple-700',
  ORC:     'bg-cyan-100 text-cyan-700',
  Kafka:   'bg-pink-100 text-pink-700',
  Delta:   'bg-indigo-100 text-indigo-700',
};

function formatBytes(bytes?: number) {
  if (!bytes) return null;
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(1)} KB`;
  return `${bytes} B`;
}

function DistributionRow({ d, datasetId, tenant }: { d: Distribution; datasetId: string; tenant: string }) {
  return (
    <div className="space-y-3">
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            {d.format && (
              <span className={`px-2 py-0.5 text-xs rounded font-medium ${FORMAT_COLORS[d.format] ?? 'bg-gray-100 text-gray-600'}`}>
                {d.format}
              </span>
            )}
            {d.mediaType && <span className="text-xs text-gray-500">{d.mediaType}</span>}
            {d.byteSize && <span className="text-xs text-gray-400">{formatBytes(d.byteSize)}</span>}
          </div>
          <Link
            to={`/${tenant}/datasets/${datasetId}/distributions/${d.id}`}
            className="text-xs text-blue-600 hover:underline flex-shrink-0"
          >
            View details →
          </Link>
        </div>
        {d.title && <p className="text-sm font-medium text-gray-900 mb-1">{d.title}</p>}
        {d.description && <p className="text-xs text-gray-500 mb-2">{d.description}</p>}
        {d.accessUrl && (
          <div className="flex items-center gap-1 bg-gray-50 rounded px-2 py-1.5">
            <span className="text-xs text-gray-600 font-mono truncate flex-1">{d.accessUrl}</span>
            <a href={d.accessUrl} target="_blank" rel="noreferrer"
              className="text-xs text-blue-600 hover:underline flex-shrink-0">Open ↗</a>
          </div>
        )}
        {d.downloadUrl && !d.accessUrl && (
          <div className="flex items-center gap-1 bg-gray-50 rounded px-2 py-1.5">
            <span className="text-xs text-gray-600 font-mono truncate flex-1">{d.downloadUrl}</span>
            <a href={d.downloadUrl} target="_blank" rel="noreferrer"
              className="text-xs text-blue-600 hover:underline flex-shrink-0">Download ↓</a>
          </div>
        )}
        {d.checksumValue && (
          <p className="text-xs text-gray-400 mt-1">
            {d.checksumAlgorithm ?? 'checksum'}: <span className="font-mono">{d.checksumValue}</span>
          </p>
        )}
      </div>

      <PhysicalSchemaSection
        distributionId={d.id}
        datasetId={datasetId}
        tenant={tenant}
      />
    </div>
  );
}

function DistributionsTab({ datasetId, tenant }: { datasetId: string; tenant: string }) {
  const { data: distributions = [] } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  if (distributions.length === 0) {
    return <p className="text-sm text-gray-500">No distributions found.</p>;
  }

  return (
    <div className="space-y-6 max-w-4xl">
      {distributions.map(d => (
        <DistributionRow key={d.id} d={d} datasetId={datasetId} tenant={tenant} />
      ))}
    </div>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-900 text-sm">{value}</dd>
    </div>
  );
}
