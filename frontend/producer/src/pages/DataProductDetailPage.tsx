import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dataProductApi, lineageApi } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';
import LineageGraph from '../components/lineage/LineageGraph';
import { LIFECYCLE_COLORS, formatDate } from '../lib/utils';

const TABS = ['Overview', 'Ports', 'Datasets', 'Lineage'] as const;
type Tab = typeof TABS[number];

const LIFECYCLE_STEPS = ['Ideation', 'Design', 'Build', 'Deploy', 'Consume'];

export default function DataProductDetailPage() {
  const { id, tenant } = useParams();
  const [activeTab, setActiveTab] = useState<Tab>('Overview');
  const qc = useQueryClient();

  const { data: dp, isLoading } = useQuery({
    queryKey: ['data-product', id],
    queryFn: () => dataProductApi.get(id!),
    enabled: !!id,
  });

  const lifecycleMut = useMutation({
    mutationFn: (status: string) => dataProductApi.patchLifecycle(id!, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['data-product', id] }),
  });

  if (isLoading) return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  if (!dp) return <div className="p-6 text-sm text-red-500">Not found</div>;

  const currentIdx = LIFECYCLE_STEPS.indexOf(dp.lifecycleStatus);
  const nextStatus = LIFECYCLE_STEPS[currentIdx + 1];

  return (
    <div>
      <PageHeader
        title={dp.title}
        actions={
          <div className="flex items-center gap-2">
            <Badge label={dp.lifecycleStatus} className={LIFECYCLE_COLORS[dp.lifecycleStatus]} />
            {nextStatus && (
              <Button size="sm" onClick={() => lifecycleMut.mutate(nextStatus)}>
                Advance to {nextStatus}
              </Button>
            )}
          </div>
        }
      />

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
            {dp.description && <p className="text-sm text-gray-700">{dp.description}</p>}
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <DlItem label="Updated" value={formatDate(dp.updatedAt)} />
              <DlItem label="Sensitivity" value={dp.informationSensitivity ?? '—'} />
              {dp.purpose && <DlItem label="Purpose" value={dp.purpose} />}
            </dl>
            {dp.keywords && dp.keywords.length > 0 && (
              <div>
                <p className="text-xs font-medium text-gray-500 mb-1">Keywords</p>
                <div className="flex flex-wrap gap-1">
                  {dp.keywords.map(kw => (
                    <span key={kw} className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded">{kw}</span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
        {activeTab === 'Ports' && (
          <div className="text-sm text-gray-500">Ports management — connect input/output ports to datasets and services.</div>
        )}
        {activeTab === 'Datasets' && (
          <DatasetsTab dataProductId={id!} tenant={tenant!} />
        )}
        {activeTab === 'Lineage' && (
          <DataProductLineageTab dataProductId={id!} tenant={tenant!} />
        )}
      </div>
    </div>
  );
}

function DataProductLineageTab({ dataProductId, tenant }: { dataProductId: string; tenant: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: datasets = [], isLoading } = useQuery({
    queryKey: ['data-product-datasets', dataProductId],
    queryFn: () => dataProductApi.listDatasets(dataProductId),
  });

  const activeId = selectedId ?? datasets[0]?.id ?? null;

  if (isLoading) return <div className="text-sm text-gray-400 py-4">Loading datasets...</div>;

  if (datasets.length === 0) {
    return (
      <div className="text-sm text-gray-500 space-y-2">
        <p>No datasets linked to this data product.</p>
        <Link to={`/${tenant}/lineage`} className="text-blue-600 hover:underline text-sm">
          Open lineage explorer →
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        {datasets.length > 1 ? (
          <select
            value={activeId ?? ''}
            onChange={e => setSelectedId(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {datasets.map((ds: Dataset) => (
              <option key={ds.id} value={ds.id}>{ds.title}</option>
            ))}
          </select>
        ) : (
          <p className="text-xs text-gray-500">Lineage for: <span className="font-medium">{datasets[0].title}</span></p>
        )}
        <Link to={`/${tenant}/lineage`} className="text-xs text-blue-600 hover:underline">
          Open full explorer →
        </Link>
      </div>
      {activeId && <DatasetLineagePanel datasetId={activeId} />}
    </div>
  );
}

function DatasetLineagePanel({ datasetId }: { datasetId: string }) {
  const { tenant } = useParams<{ tenant: string }>();
  const navigate = useNavigate();
  const { data: identity, isLoading, isError } = useQuery({
    queryKey: ['lineage-identity', datasetId],
    queryFn: () => lineageApi.getCatalogLineageIdentity(datasetId),
    retry: false,
  });

  const { data: graph } = useQuery({
    queryKey: ['lineage-graph', identity?.id],
    queryFn: () => lineageApi.getDatasetLineage(identity!.id, 'downstream', 5),
    enabled: !!identity,
  });

  if (isLoading) return <div className="h-[calc(100vh-220px)] flex items-center justify-center text-sm text-gray-400">Looking up lineage...</div>;
  if (isError || !identity) return <p className="text-sm text-gray-400 py-4">No lineage data linked to this dataset.</p>;

  return (
    <div className="h-[calc(100vh-220px)] rounded-lg border border-gray-200 overflow-hidden">
      {graph
        ? <LineageGraph graph={graph} onNavigate={(catalogId) => navigate(`/${tenant}/datasets/${catalogId}`)} />
        : <div className="h-full flex items-center justify-center text-sm text-gray-400">Loading graph...</div>
      }
    </div>
  );
}

function DatasetsTab({ dataProductId, tenant }: { dataProductId: string; tenant: string }) {
  const qc = useQueryClient();

  const { data: datasets = [], isLoading } = useQuery({
    queryKey: ['data-product-datasets', dataProductId],
    queryFn: () => dataProductApi.listDatasets(dataProductId),
  });

  const unlinkMut = useMutation({
    mutationFn: (dsId: string) => dataProductApi.unlinkDataset(dataProductId, dsId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['data-product-datasets', dataProductId] }),
  });

  if (isLoading) return <div className="text-sm text-gray-400">Loading...</div>;

  return (
    <div className="space-y-3 max-w-3xl">
      {datasets.length === 0 && (
        <p className="text-sm text-gray-500">No datasets linked via output ports yet.</p>
      )}
      {datasets.map((ds: Dataset) => (
        <div key={ds.id} className="bg-white border border-gray-200 rounded-lg p-4 flex items-start justify-between gap-4">
          <div className="min-w-0">
            <Link
              to={`/${tenant}/datasets/${ds.id}`}
              className="text-sm font-medium text-blue-600 hover:underline truncate block"
            >
              {ds.title}
            </Link>
            {ds.description && (
              <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{ds.description}</p>
            )}
            <div className="flex flex-wrap gap-1 mt-1.5">
              {ds.keywords?.map(kw => (
                <span key={kw} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">{kw}</span>
              ))}
            </div>
          </div>
          <button
            onClick={() => unlinkMut.mutate(ds.id)}
            className="text-xs text-red-500 hover:text-red-700 flex-shrink-0"
          >
            Unlink
          </button>
        </div>
      ))}
    </div>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-900">{value}</dd>
    </div>
  );
}
