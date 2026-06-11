import { useState, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { datasetApi, logicalModelApi, logicalElementApi, lineageApi, useIriTranslations, iriFragment } from '@datacatalog/shared';
import type { Dataset, Distribution, OwnershipProposal, LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import DatasetForm from '../components/catalog/DatasetForm';
import LogicalModelEditor from '../components/catalog/LogicalModelEditor';
import PhysicalSchemaSection from '../components/catalog/PhysicalSchemaSection';
import LineageGraph from '../components/lineage/LineageGraph';
import ReactFlow, { Background, Controls, MiniMap, Handle, Position, type Node, type Edge, type NodeMouseHandler, type NodeProps } from 'reactflow';
import OwnershipPanel from '../components/catalog/OwnershipPanel';
import TermsOfUsePanel from '../components/catalog/TermsOfUsePanel';
import PolicyPanel from '../components/catalog/PolicyPanel';
import DatasetHistoryTab from '../components/catalog/DatasetHistoryTab';
import SemanticContextPanel from '../components/catalog/SemanticContextPanel';
import { cn } from '../lib/utils';
import { formatDate } from '../lib/utils';
import { useAuthStore } from '../store/authStore';

const TABS = ['Overview', 'Distributions', 'Model', 'Lineage', 'Governance', 'History'] as const;
type Tab = typeof TABS[number];

export default function DatasetDetailPage() {
  const { id, tenant } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { userId, hasRole, hasAnyRole } = useAuthStore();
  const [activeTab, setActiveTab] = useState<Tab>('Overview');
  const [editing, setEditing] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [ownershipRequested, setOwnershipRequested] = useState(false);

  const { data: dataset, isLoading } = useQuery({
    queryKey: ['dataset', id],
    queryFn: () => datasetApi.get(id!),
    enabled: !!id,
  });

  const { data: logicalModels = [] } = useQuery({
    queryKey: ['logical-models', id],
    queryFn: () => logicalModelApi.list(id!),
    enabled: !!id && activeTab === 'Model',
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

  const requestOwnershipMutation = useMutation<Dataset | OwnershipProposal>({
    mutationFn: () => dataset!.ownerId
      ? datasetApi.proposeTransfer(id!, userId!)
      : datasetApi.assignOwner(id!, userId!),
    onSuccess: (result) => {
      if ('resourceType' in result) {
        qc.setQueryData(['dataset', id], result);
      } else {
        qc.invalidateQueries({ queryKey: ['pending-proposal', id] });
      }
      setOwnershipRequested(true);
    },
  });

  if (isLoading) return <div className="p-6 text-sm text-gray-500">Loading...</div>;
  if (!dataset) return <div className="p-6 text-sm text-red-500">Not found</div>;

  // Only the dataset's assigned Data Owner or an Administrator may edit dataset metadata.
  // AI suggestion actions (classification, description, semantic tags) are owner-only —
  // the data owner is accountable for accepting or rejecting AI-generated metadata.
  const isCurrentOwner  = !!dataset.ownerId && dataset.ownerId === userId;
  const isAdmin         = hasRole('administrator');
  const canEditAny      = hasAnyRole(['administrator', 'data-governance']);
  const canEdit         = isAdmin || isCurrentOwner;
  const canOwnerAction  = isCurrentOwner;
  const canDelete       = canEditAny || isCurrentOwner;
  const canRequestOwnership = hasRole('data-owner') && !isCurrentOwner && !canEditAny;

  const headerActions = editing ? null : (
    <>
      {canEdit && (
        <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
          Edit
        </Button>
      )}
      {canRequestOwnership && !ownershipRequested && (
        <Button
          size="sm"
          variant="secondary"
          disabled={requestOwnershipMutation.isPending}
          onClick={() => requestOwnershipMutation.mutate()}
        >
          {requestOwnershipMutation.isPending
            ? 'Requesting…'
            : dataset.ownerId ? 'Request Ownership' : 'Claim Ownership'}
        </Button>
      )}
      {canRequestOwnership && ownershipRequested && (
        <span className="text-xs text-green-700 font-medium">
          {dataset.ownerId ? 'Request sent — awaiting approval' : 'Ownership claimed'}
        </span>
      )}
      {requestOwnershipMutation.isError && (
        <span className="text-xs text-red-600">Request failed. Please try again.</span>
      )}
      {canDelete && (
        confirmDelete ? (
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
        )
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
                <SemanticContextPanel datasetId={id!} canAction={canOwnerAction} />
              </div>
            </div>
          )
        )}
        {activeTab === 'Distributions' && (
          <DistributionsTab datasetId={id!} tenant={tenant!} canOwnerAction={canOwnerAction} />
        )}
        {activeTab === 'Model' && (
          <LogicalModelEditor datasetId={id!} models={logicalModels} canAction={canOwnerAction} />
        )}
        {activeTab === 'Lineage' && (
          <LineageTab datasetId={id!} tenant={tenant!} />
        )}
        {activeTab === 'Governance' && (
          <div className="max-w-2xl space-y-8">
            <OwnershipPanel dataset={dataset} onUpdated={d => qc.setQueryData(['dataset', id], d)} />
            <div className="border-t pt-6">
              <TermsOfUsePanel datasetId={id!} canAction={canOwnerAction} />
            </div>
            <div className="border-t pt-6">
              <h3 className="text-sm font-semibold text-gray-700 mb-4">Policy Enforcement</h3>
              <PolicyPanel datasetId={id!} />
            </div>
          </div>
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
  d, datasetId, tenant, canOwnerAction,
}: { d: Distribution; datasetId: string; tenant: string; canOwnerAction: boolean }) {
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
      <PhysicalSchemaSection distributionId={d.id} datasetId={datasetId} tenant={tenant} canAction={canOwnerAction} />
    </div>
  );
}

function DistributionsTab({ datasetId, tenant, canOwnerAction }: { datasetId: string; tenant: string; canOwnerAction: boolean }) {
  const [adding, setAdding] = useState(false);

  const { data: distributions = [] } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  return (
    <div className="space-y-6 max-w-4xl">
      {distributions.map(d => (
        <DistributionRow key={d.id} d={d} datasetId={datasetId} tenant={tenant} canOwnerAction={canOwnerAction} />
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

function DatasetNode({ id, data, sourcePosition = Position.Right, targetPosition = Position.Left }: NodeProps) {
  const expanded = !!(data.expanded as boolean);

  const { data: models } = useQuery({
    queryKey: ['lineage-node-models', data.catalogId],
    queryFn: () => logicalModelApi.list(data.catalogId as string),
    enabled: expanded && !!(data.catalogId),
  });

  const publishedModel = models?.find((m: { status: string }) => m.status === 'published') ?? models?.[0];

  const { data: elements = [], isLoading: elementsLoading } = useQuery({
    queryKey: ['lineage-node-elements', publishedModel?.id],
    queryFn: () => logicalElementApi.list(publishedModel!.id),
    enabled: !!publishedModel,
  });

  return (
    <>
      <Handle type="target" position={targetPosition} />
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {data.label as string}
          </span>
          {data.catalogId && (
            <>
              <button
                onClick={(e) => { e.stopPropagation(); (data.onToggleExpand as ((id: string) => void) | undefined)?.(id); }}
                title={expanded ? 'Collapse' : 'Show data elements'}
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}
              >
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  {expanded
                    ? <path d="M2 6.5L5 3.5L8 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    : <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  }
                </svg>
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); (data.onNavigate as ((cid: string) => void) | undefined)?.(data.catalogId as string); }}
                title="Open in catalog"
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}
              >
                <svg width="10" height="10" viewBox="0 0 12 12" fill="none">
                  <path d="M5 2H2a1 1 0 00-1 1v7a1 1 0 001 1h7a1 1 0 001-1V7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M8 1h3m0 0v3m0-3L6 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>
            </>
          )}
        </div>
        {expanded && (
          <div style={{ marginTop: 5, borderTop: '1px solid rgba(0,0,0,0.1)', paddingTop: 4, maxHeight: 160, overflowY: 'auto', minWidth: 140 }}>
            {elementsLoading && <div style={{ fontSize: 9, color: '#94a3b8', textAlign: 'center' }}>…</div>}
            {!elementsLoading && elements.length === 0 && (
              <div style={{ fontSize: 9, color: '#94a3b8', fontStyle: 'italic' }}>No elements</div>
            )}
            {(elements as { id: string; name: string; logicalType?: string }[]).map(el => (
              <div
                key={el.id}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 9, padding: '1.5px 0', gap: 6, cursor: 'default' }}
                onMouseEnter={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(id)}
                onMouseLeave={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(null)}
              >
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{el.name}</span>
                {el.logicalType && <span style={{ color: '#94a3b8', flexShrink: 0, fontSize: 8 }}>{el.logicalType}</span>}
              </div>
            ))}
          </div>
        )}
      </div>
      <Handle type="source" position={sourcePosition} />
    </>
  );
}

const LINEAGE_NODE_TYPES = { dataset: DatasetNode };

type LineageDirection = 'upstream' | 'downstream' | 'both';

const LINEAGE_COL   = 240;
const LINEAGE_ROW   = 100;
const DATASET_BG    = '#dbeafe';
const JOB_BG        = '#fef3c7';
const FOCAL_BG      = '#bfdbfe';
const EXPANDED_EXTRA_HEIGHT = 185;

function resolveHandles(nodes: Node[], edges: Edge[]): Node[] {
  const xOf = new Map(nodes.map(n => [n.id, n.position.x]));
  const sp = new Map<string, Position>();
  const tp = new Map<string, Position>();
  for (const e of edges) {
    const sx = xOf.get(e.source) ?? 0;
    const tx = xOf.get(e.target) ?? 0;
    sp.set(e.source, sx <= tx ? Position.Right : Position.Left);
    tp.set(e.target, sx <= tx ? Position.Left  : Position.Right);
  }
  return nodes.map(n => ({
    ...n,
    sourcePosition: sp.get(n.id) ?? Position.Right,
    targetPosition: tp.get(n.id) ?? Position.Left,
  }));
}

function applyExpansionOffsets(nodes: Node[], expandedNodeIds: Set<string>, extraHeight: number): Node[] {
  if (expandedNodeIds.size === 0) return nodes;
  const byX = new Map<number, Node[]>();
  for (const n of nodes) {
    if (!byX.has(n.position.x)) byX.set(n.position.x, []);
    byX.get(n.position.x)!.push(n);
  }
  const result = [...nodes];
  const idxOf = new Map(result.map((n, i) => [n.id, i]));
  for (const col of byX.values()) {
    col.sort((a, b) => a.position.y - b.position.y);
    let offset = 0;
    for (const node of col) {
      if (offset > 0) {
        const i = idxOf.get(node.id)!;
        result[i] = { ...result[i], position: { ...result[i].position, y: result[i].position.y + offset } };
      }
      if (expandedNodeIds.has(node.id)) offset += extraHeight;
    }
  }
  return result;
}

function makeNode(n: LineageNode, x: number, rowIdx: number, total: number, isRoot: boolean, onNavigate?: (catalogId: string) => void, onHighlightNode?: (id: string | null) => void, expandedNodeIds?: Set<string>, onToggleExpand?: (id: string) => void): Node {
  return {
    id: n.id,
    type: 'dataset',
    data: { label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId, onNavigate, onHighlightNode, expanded: expandedNodeIds?.has(n.id) ?? false, onToggleExpand },
    position: { x, y: (rowIdx - (total - 1) / 2) * LINEAGE_ROW },
    style: {
      background: isRoot ? FOCAL_BG : n.type === 'Job' ? JOB_BG : DATASET_BG,
      border: isRoot ? '2px solid #3b82f6' : '1px solid #cbd5e1',
      borderRadius: 8, padding: '8px 12px', fontSize: 12, maxWidth: 200,
      fontWeight: isRoot ? 600 : 400,
    },
  };
}

function makeEdge(e: LineageEdge, i: number, highlightedNodeId?: string | null): Edge {
  const highlighted = !!highlightedNodeId && e.toId === highlightedNodeId;
  return {
    id: `e-${i}`,
    source: e.fromId,
    target: e.toId,
    label: e.edgeType, labelStyle: { fontSize: 10 },
    style: highlighted ? { stroke: '#3b82f6', strokeWidth: 2.5 } : { stroke: '#94a3b8', strokeWidth: 1.5 },
    animated: e.edgeType === 'DERIVED_FROM',
  };
}

function buildLineageFlow(
  direction: LineageDirection,
  downGraph: LineageGraphData | undefined,
  upGraph: LineageGraphData | undefined,
  onNavigate?: (catalogId: string) => void,
  highlightedNodeId?: string | null,
  onHighlightNode?: (id: string | null) => void,
  expandedNodeIds?: Set<string>,
  onToggleExpand?: (id: string) => void,
): { nodes: Node[]; edges: Edge[] } | null {
  if (direction === 'downstream' && downGraph) {
    const buckets = new Map<number, LineageNode[]>();
    downGraph.nodes.forEach(n => { const d = n.depth ?? 0; if (!buckets.has(d)) buckets.set(d, []); buckets.get(d)!.push(n); });
    const downNodes = downGraph.nodes.map(n => {
      const d = n.depth ?? 0; const bucket = buckets.get(d)!;
      return makeNode(n, d * LINEAGE_COL, bucket.indexOf(n), bucket.length, n.id === downGraph.rootId, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand);
    });
    const downEdges = downGraph.edges.map((e, i) => makeEdge(e, i, highlightedNodeId));
    const withHandles = resolveHandles(downNodes, downEdges);
    return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges: downEdges };
  }
  if (direction === 'upstream' && upGraph) {
    const buckets = new Map<number, LineageNode[]>();
    upGraph.nodes.forEach(n => { const d = n.depth ?? 0; if (!buckets.has(d)) buckets.set(d, []); buckets.get(d)!.push(n); });
    const upNodes = upGraph.nodes.map(n => {
      const d = n.depth ?? 0; const bucket = buckets.get(d)!;
      return makeNode(n, -d * LINEAGE_COL, bucket.indexOf(n), bucket.length, n.id === upGraph.rootId, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand);
    });
    const upEdges = upGraph.edges.map((e, i) => makeEdge(e, i, highlightedNodeId));
    const withHandles = resolveHandles(upNodes, upEdges);
    return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges: upEdges };
  }
  if (direction === 'both' && upGraph && downGraph) {
    const signedDepths = new Map<string, number>();
    const nodeById     = new Map<string, LineageNode>();
    for (const n of downGraph.nodes) { signedDepths.set(n.id, n.depth ?? 0); nodeById.set(n.id, n); }
    for (const n of upGraph.nodes)   { const sd = -(n.depth ?? 0); if (sd === 0) continue; signedDepths.set(n.id, sd); nodeById.set(n.id, n); }
    const cols = new Map<number, string[]>();
    for (const [id, sd] of signedDepths) { if (!cols.has(sd)) cols.set(sd, []); cols.get(sd)!.push(id); }
    const nodes: Node[] = [];
    for (const [id, sd] of signedDepths) {
      const n = nodeById.get(id)!; const col = cols.get(sd)!;
      nodes.push(makeNode(n, sd * LINEAGE_COL, col.indexOf(id), col.length, sd === 0, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand));
    }
    const seen = new Set<string>(); const edges: Edge[] = [];
    for (const e of [...upGraph.edges, ...downGraph.edges]) {
      const eid = `${e.fromId}->${e.toId}`;
      if (!seen.has(eid)) { seen.add(eid); edges.push(makeEdge(e, edges.length, highlightedNodeId)); }
    }
    const withHandles = resolveHandles(nodes, edges);
    return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges };
  }
  return null;
}

function LineageTab({ datasetId, tenant }: { datasetId: string; tenant: string }) {
  const [direction, setDirection] = useState<LineageDirection>('downstream');
  const [highlightedNodeId, setHighlightedNodeId] = useState<string | null>(null);
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());

  const handleToggleExpand = useCallback((nodeId: string) => {
    setExpandedNodeIds(prev => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId);
      return next;
    });
  }, []);

  const { data: identity, isLoading, isError } = useQuery({
    queryKey: ['lineage-identity', datasetId],
    queryFn: () => lineageApi.getCatalogLineageIdentity(datasetId),
    retry: false,
  });

  const { data: downGraph, isLoading: downLoading } = useQuery({
    queryKey: ['lineage-tab', identity?.id, 'downstream'],
    queryFn: () => lineageApi.getDatasetLineage(identity!.id, 'downstream', 5),
    enabled: !!identity && (direction === 'downstream' || direction === 'both'),
  });

  const { data: upGraph, isLoading: upLoading } = useQuery({
    queryKey: ['lineage-tab', identity?.id, 'upstream'],
    queryFn: () => lineageApi.getDatasetLineage(identity!.id, 'upstream', 5),
    enabled: !!identity && (direction === 'upstream' || direction === 'both'),
  });

  const navigate = useNavigate();
  const handleNavigate = useCallback((catalogId: string) => {
    navigate(`/${tenant}/datasets/${catalogId}`);
  }, [navigate, tenant]);
  const onNodeDoubleClick: NodeMouseHandler = useCallback((_evt, node) => {
    const { catalogId } = node.data as { catalogId?: string };
    if (catalogId) navigate(`/${tenant}/datasets/${catalogId}`);
  }, [navigate, tenant]);

  if (isLoading) return <div className="text-sm text-gray-400">Looking up lineage...</div>;
  if (isError || !identity) return (
    <div className="text-sm text-gray-500 space-y-2">
      <p>No lineage data linked to this dataset.</p>
      <Link to={`/${tenant}/lineage`} className="text-blue-600 hover:underline text-sm">
        Open lineage explorer →
      </Link>
    </div>
  );

  const graphLoading = direction === 'both' ? (downLoading || upLoading) : direction === 'upstream' ? upLoading : downLoading;
  const flow = buildLineageFlow(direction, downGraph, upGraph, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);

  const DIRS: { value: LineageDirection; label: string }[] = [
    { value: 'upstream',   label: 'Upstream'   },
    { value: 'downstream', label: 'Downstream' },
    { value: 'both',       label: 'Both'       },
  ];

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1">
          {DIRS.map(d => (
            <button
              key={d.value}
              onClick={() => setDirection(d.value)}
              className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${direction === d.value ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
            >
              {d.label}
            </button>
          ))}
        </div>
        <Link
          to={`/${tenant}/lineage?ns=${encodeURIComponent(identity.namespace)}&name=${encodeURIComponent(identity.name)}&direction=${direction}`}
          className="text-xs text-blue-600 hover:underline"
        >
          Open full explorer →
        </Link>
      </div>
      <div className="h-[calc(100vh-240px)] rounded-lg border border-gray-200 overflow-hidden">
        {graphLoading && <div className="h-full flex items-center justify-center text-sm text-gray-400">Loading graph...</div>}
        {!graphLoading && flow && (
          direction === 'both' || direction === 'upstream'
            ? (
              <ReactFlow nodes={flow.nodes} edges={flow.edges} nodeTypes={LINEAGE_NODE_TYPES} fitView onNodeDoubleClick={onNodeDoubleClick} nodesDraggable={false} nodesConnectable={false} elementsSelectable={false} attributionPosition="bottom-right">
                <Background gap={20} color="#e2e8f0" />
                <Controls />
                <MiniMap nodeColor={n => n.style?.background as string ?? DATASET_BG} />
              </ReactFlow>
            )
            : <LineageGraph graph={downGraph!} onNodeDoubleClick={onNodeDoubleClick} onNavigate={handleNavigate} />
        )}
        {!graphLoading && !flow && (
          <div className="h-full flex items-center justify-center text-sm text-gray-400">No lineage data</div>
        )}
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
