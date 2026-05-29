import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { datasetApi, logicalModelApi, lineageApi, useIriTranslations, iriFragment } from '@datacatalog/shared';
import type { Dataset, Distribution } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import Button from '../components/ui/Button';
import DatasetForm from '../components/catalog/DatasetForm';
import LogicalModelEditor from '../components/catalog/LogicalModelEditor';
import PhysicalSchemaSection from '../components/catalog/PhysicalSchemaSection';
import LineageGraph from '../components/lineage/LineageGraph';
import OwnershipPanel from '../components/catalog/OwnershipPanel';
import DatasetHistoryTab from '../components/catalog/DatasetHistoryTab';
import SemanticContextPanel from '../components/catalog/SemanticContextPanel';
import { cn } from '../lib/utils';
import { formatDate } from '../lib/utils';

const TABS = ['Overview', 'Distributions', 'Schema', 'Lineage', 'Governance', 'History'] as const;
type Tab = typeof TABS[number];

export default function DatasetDetailPage() {
  const { id, tenant } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [activeTab, setActiveTab] = useState<Tab>('Overview');
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

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

  const updateMutation = useMutation({
    mutationFn: (data: Partial<Dataset>) => datasetApi.update(id!, data),
    onSuccess: (updated) => {
      qc.setQueryData(['dataset', id], updated);
      setEditing(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => datasetApi.delete(id!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['datasets'] });
      navigate(`/${tenant}/datasets`);
    },
  });

  if (isLoading) return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  if (!dataset) return <div className="p-6 text-sm text-red-500">Not found</div>;

  const headerActions = editing ? null : (
    <>
      <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
        Edit
      </Button>
      {confirmDelete ? (
        <>
          <span className="text-xs text-red-600 font-medium">Delete this dataset?</span>
          <Button
            size="sm"
            variant="danger"
            disabled={deleteMutation.isPending}
            onClick={() => deleteMutation.mutate()}
          >
            {deleteMutation.isPending ? 'Deleting…' : 'Confirm'}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setConfirmDelete(false)}>
            Cancel
          </Button>
        </>
      ) : (
        <Button size="sm" variant="danger" onClick={() => setConfirmDelete(true)}>
          Delete
        </Button>
      )}
    </>
  );

  return (
    <div>
      <PageHeader title={dataset.title} description={dataset.description} actions={headerActions} />

      <div className="border-b bg-white">
        <nav className="flex px-6 gap-1">
          {TABS.map(tab => (
            <button
              key={tab}
              onClick={() => { setActiveTab(tab); setEditing(false); setConfirmDelete(false); }}
              className={cn(
                'px-4 py-3 text-sm font-medium border-b-2 transition-colors',
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700',
              )}
            >
              {tab}
            </button>
          ))}
        </nav>
      </div>

      <div className="p-6">
        {activeTab === 'Overview' && (
          editing ? (
            <div className="max-w-2xl">
              {updateMutation.isError && (
                <p className="mb-4 text-sm text-red-600">Failed to save. Please try again.</p>
              )}
              <DatasetForm
                defaultValues={dataset}
                onSubmit={data => updateMutation.mutate(data)}
                isSubmitting={updateMutation.isPending}
                submitLabel="Save Changes"
                onCancel={() => { setEditing(false); setConfirmDelete(false); }}
              />
            </div>
          ) : (
            <div className="max-w-2xl space-y-8">
              <OverviewTab dataset={dataset} />
              <div className="border-t pt-6">
                <SemanticContextPanel datasetId={id!} />
              </div>
            </div>
          )
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
        {activeTab === 'Governance' && (
          <OwnershipPanel dataset={dataset} onUpdated={d => qc.setQueryData(['dataset', id], d)} />
        )}
        {activeTab === 'History' && (
          <DatasetHistoryTab datasetId={id!} />
        )}
      </div>
    </div>
  );
}

function OverviewTab({ dataset }: { dataset: Dataset }) {
  const iriList = [
    ...(dataset.themes ?? []),
    ...(dataset.license ? [dataset.license] : []),
    ...(dataset.accrualPeriodicity ? [dataset.accrualPeriodicity] : []),
  ];
  const translations = useIriTranslations(iriList);
  const t = (iri: string) => translations[iri] ?? iriFragment(iri);

  return (
    <div className="max-w-2xl space-y-4">
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <DlItem label="Updated" value={formatDate(dataset.updatedAt)} />
        {dataset.version && <DlItem label="Version" value={dataset.version} />}
        {dataset.accrualPeriodicity && (
          <DlItem label="Accrual Periodicity" value={t(dataset.accrualPeriodicity)} />
        )}
        {dataset.license && <DlItem label="License" value={t(dataset.license)} />}
      </dl>
      {dataset.keywords && dataset.keywords.length > 0 && (
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Keywords</p>
          <div className="flex flex-wrap gap-1">
            {dataset.keywords.map(kw => (
              <span key={kw} className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded">
                {kw}
              </span>
            ))}
          </div>
        </div>
      )}
      {dataset.themes && dataset.themes.length > 0 && (
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Themes</p>
          <div className="flex flex-wrap gap-1">
            {dataset.themes.map(theme => (
              <span key={theme} title={theme} className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded">
                {t(theme)}
              </span>
            ))}
          </div>
        </div>
      )}
      {dataset.language && dataset.language.length > 0 && (
        <div>
          <p className="text-xs font-medium text-gray-500 mb-1">Language</p>
          <p className="text-sm text-gray-700">{dataset.language.join(', ')}</p>
        </div>
      )}
    </div>
  );
}

// ─── Distribution form ────────────────────────────────────────────────────────

const DISTRIBUTION_FORMATS = [
  'Parquet', 'CSV', 'JSON', 'Avro', 'ORC', 'Kafka', 'Delta', 'XML', 'REST', 'Other',
] as const;

const FORMAT_MEDIA_TYPE: Record<string, string> = {
  Parquet: 'application/vnd.apache.parquet',
  CSV:     'text/csv',
  JSON:    'application/json',
  Avro:    'application/avro',
  ORC:     'application/x-orc',
  Kafka:   'application/kafka',
  Delta:   'application/vnd.delta',
  XML:     'application/xml',
  REST:    'application/json',
};

interface DistFormValues {
  title: string;
  description: string;
  format: string;
  mediaType: string;
  accessUrl: string;
  downloadUrl: string;
}

function DistributionForm({
  datasetId,
  onSuccess,
  onCancel,
}: {
  datasetId: string;
  onSuccess: () => void;
  onCancel: () => void;
}) {
  const { register, handleSubmit, watch, setValue } = useForm<DistFormValues>({
    defaultValues: { title: '', description: '', format: '', mediaType: '', accessUrl: '', downloadUrl: '' },
  });
  const qc = useQueryClient();

  const mutation = useMutation({
    mutationFn: (data: Partial<Distribution>) => datasetApi.createDistribution(datasetId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['distributions', datasetId] });
      onSuccess();
    },
  });

  const selectedFormat = watch('format');

  function onFormatChange(fmt: string) {
    setValue('format', fmt);
    if (FORMAT_MEDIA_TYPE[fmt]) setValue('mediaType', FORMAT_MEDIA_TYPE[fmt]);
  }

  function submit(values: DistFormValues) {
    mutation.mutate({
      title:       values.title || undefined,
      description: values.description || undefined,
      format:      values.format || undefined,
      mediaType:   values.mediaType || undefined,
      accessUrl:   values.accessUrl || undefined,
      downloadUrl: values.downloadUrl || undefined,
    });
  }

  const cls = 'w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500';

  return (
    <form
      onSubmit={handleSubmit(submit)}
      className="border border-gray-200 rounded-lg p-4 bg-gray-50 space-y-4"
    >
      <h3 className="text-sm font-semibold text-gray-800">Add Distribution</h3>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Title</label>
          <input {...register('title')} className={cls} placeholder="Snowflake table endpoint" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Format</label>
          <select
            value={selectedFormat}
            onChange={e => onFormatChange(e.target.value)}
            className={cls}
          >
            <option value="">— select —</option>
            {DISTRIBUTION_FORMATS.map(f => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Description</label>
        <textarea {...register('description')} rows={2} className={cls} placeholder="Optional description…" />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Media Type</label>
          <input {...register('mediaType')} className={cls} placeholder="application/json" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Access URL</label>
          <input {...register('accessUrl')} className={cls} placeholder="https://…" />
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Download URL</label>
        <input {...register('downloadUrl')} className={cls} placeholder="https://…" />
      </div>

      {mutation.isError && (
        <p className="text-xs text-red-600">Failed to add distribution. Please try again.</p>
      )}

      <div className="flex items-center gap-3">
        <Button type="submit" size="sm" disabled={mutation.isPending}>
          {mutation.isPending ? 'Adding…' : 'Add Distribution'}
        </Button>
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

// ─── Distributions tab ────────────────────────────────────────────────────────

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

function DistributionRow({
  d, datasetId, tenant,
}: { d: Distribution; datasetId: string; tenant: string }) {
  return (
    <div className="space-y-3">
      <div className="bg-white border border-gray-200 rounded-lg p-4">
        <div className="flex items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            {d.format && (
              <span className={cn('px-2 py-0.5 text-xs rounded font-medium', FORMAT_COLORS[d.format] ?? 'bg-gray-100 text-gray-600')}>
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
      <PhysicalSchemaSection distributionId={d.id} datasetId={datasetId} tenant={tenant} />
    </div>
  );
}

function DistributionsTab({ datasetId, tenant }: { datasetId: string; tenant: string }) {
  const [adding, setAdding] = useState(false);

  const { data: distributions = [] } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  return (
    <div className="space-y-6 max-w-4xl">
      {distributions.map(d => (
        <DistributionRow key={d.id} d={d} datasetId={datasetId} tenant={tenant} />
      ))}

      {distributions.length === 0 && !adding && (
        <p className="text-sm text-gray-500">No distributions yet.</p>
      )}

      {adding ? (
        <DistributionForm
          datasetId={datasetId}
          onSuccess={() => setAdding(false)}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
          + Add Distribution
        </Button>
      )}
    </div>
  );
}

// ─── Lineage tab ──────────────────────────────────────────────────────────────

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
        {graph
          ? <LineageGraph graph={graph} />
          : <div className="h-full flex items-center justify-center text-sm text-gray-400">Loading graph...</div>}
      </div>
    </div>
  );
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-900 text-sm">{value}</dd>
    </div>
  );
}
