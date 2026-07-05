import { useState, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, Controller } from 'react-hook-form';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Alert from '@mui/material/Alert';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import { datasetApi, logicalModelApi, logicalElementApi, lineageApi, useIriTranslations, iriFragment, PageHeader } from '@datacatalog/shared';
import type { Dataset, Distribution, OwnershipProposal, LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
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
import { formatDate } from '../lib/utils';
import { useAuthStore } from '../store/authStore';

const TABS = ['Overview', 'Distributions', 'Model', 'Lineage', 'Governance', 'History'] as const;
type TabValue = typeof TABS[number];

export default function DatasetDetailPage() {
  const { id, tenant } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { userId, hasRole, hasAnyRole } = useAuthStore();
  const [activeTab, setActiveTab] = useState<TabValue>('Overview');
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
      qc.invalidateQueries({ queryKey: ['dataset-terms', id] });
      setEditing(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => datasetApi.delete(id!),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['datasets'] }); navigate(`/${tenant}/datasets`); },
  });

  const requestOwnershipMutation = useMutation<Dataset | OwnershipProposal>({
    mutationFn: () => dataset!.ownerId
      ? datasetApi.proposeTransfer(id!, userId!)
      : datasetApi.assignOwner(id!, userId!),
    onSuccess: (result) => {
      if ('resourceType' in result) { qc.setQueryData(['dataset', id], result); }
      else { qc.invalidateQueries({ queryKey: ['pending-proposal', id] }); }
      setOwnershipRequested(true);
    },
  });

  if (isLoading) return <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>Loading…</Typography>;
  if (!dataset) return <Typography variant="body2" color="error" sx={{ p: 3 }}>Not found</Typography>;

  const isCurrentOwner  = !!dataset.ownerId && dataset.ownerId === userId;
  const isAdmin         = hasRole('administrator');
  const canEditAny      = hasAnyRole(['administrator', 'data-governance']);
  const canEdit         = isAdmin || isCurrentOwner;
  const canOwnerAction  = isCurrentOwner && editing;
  const canDelete       = canEditAny || isCurrentOwner;
  const canRequestOwnership = hasRole('data-owner') && !isCurrentOwner && !canEditAny;

  const headerActions = editing ? null : (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      {canEdit && (
        <Button size="small" variant="outlined" onClick={() => setEditing(true)} sx={{ textTransform: 'none' }}>Edit</Button>
      )}
      {canRequestOwnership && !ownershipRequested && (
        <Button size="small" variant="outlined" disabled={requestOwnershipMutation.isPending} onClick={() => requestOwnershipMutation.mutate()} sx={{ textTransform: 'none' }}>
          {requestOwnershipMutation.isPending ? 'Requesting…' : dataset.ownerId ? 'Request Ownership' : 'Claim Ownership'}
        </Button>
      )}
      {canRequestOwnership && ownershipRequested && (
        <Typography variant="caption" color="success.main" fontWeight={600}>
          {dataset.ownerId ? 'Request sent — awaiting approval' : 'Ownership claimed'}
        </Typography>
      )}
      {requestOwnershipMutation.isError && (
        <Typography variant="caption" color="error">Request failed. Please try again.</Typography>
      )}
      {canDelete && (
        confirmDelete ? (
          <>
            <Typography variant="caption" color="error" fontWeight={600}>Delete this dataset?</Typography>
            <Button size="small" variant="contained" color="error" disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate()} sx={{ textTransform: 'none' }}>
              {deleteMutation.isPending ? 'Deleting…' : 'Confirm'}
            </Button>
            <Button size="small" variant="text" onClick={() => setConfirmDelete(false)} sx={{ textTransform: 'none' }}>Cancel</Button>
          </>
        ) : (
          <Button size="small" variant="outlined" color="error" onClick={() => setConfirmDelete(true)} sx={{ textTransform: 'none' }}>Delete</Button>
        )
      )}
    </Box>
  );

  return (
    <Box>
      <PageHeader title={dataset.title} description={dataset.description} actions={headerActions} />

      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Tabs
          value={activeTab}
          onChange={(_, v) => { setActiveTab(v); setEditing(false); setConfirmDelete(false); }}
          sx={{ px: 2 }}
        >
          {TABS.map(tab => <Tab key={tab} label={tab} value={tab} sx={{ textTransform: 'none', fontSize: 13 }} />)}
        </Tabs>
      </Box>

      <Box sx={{ p: 3 }}>
        {activeTab === 'Overview' && (
          editing ? (
            <Box sx={{ maxWidth: 640 }}>
              {updateMutation.isError && (
                <Alert severity="error" sx={{ mb: 2 }}>Failed to save. Please try again.</Alert>
              )}
              <DatasetForm
                defaultValues={dataset}
                onSubmit={data => updateMutation.mutate(data)}
                isSubmitting={updateMutation.isPending}
                submitLabel="Save Changes"
                onCancel={() => { setEditing(false); setConfirmDelete(false); }}
              />
            </Box>
          ) : (
            <Box sx={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 4 }}>
              <OverviewTab dataset={dataset} />
              <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 3 }}>
                <SemanticContextPanel datasetId={id!} canAction={canOwnerAction} />
              </Box>
            </Box>
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
          <Box sx={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <OwnershipPanel dataset={dataset} onUpdated={d => qc.setQueryData(['dataset', id], d)} />
            <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 3 }}>
              <TermsOfUsePanel datasetId={id!} canAction={canOwnerAction} />
            </Box>
            <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 3 }}>
              <Typography variant="body2" fontWeight={600} sx={{ mb: 2 }}>Policy Enforcement</Typography>
              <PolicyPanel datasetId={id!} />
            </Box>
          </Box>
        )}
        {activeTab === 'History' && <DatasetHistoryTab datasetId={id!} />}
      </Box>
    </Box>
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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
        <DlItem label="Updated" value={formatDate(dataset.updatedAt)} />
        {dataset.version && <DlItem label="Version" value={dataset.version} />}
        {dataset.accrualPeriodicity && <DlItem label="Accrual Periodicity" value={t(dataset.accrualPeriodicity)} />}
        {dataset.license && <DlItem label="License" value={t(dataset.license)} />}
      </Box>
      {dataset.keywords && dataset.keywords.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>Keywords</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
            {dataset.keywords.map(kw => <Chip key={kw} label={kw} size="small" sx={{ height: 18, fontSize: 11 }} />)}
          </Box>
        </Box>
      )}
      {dataset.themes && dataset.themes.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>Themes</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
            {dataset.themes.map(theme => <Chip key={theme} label={t(theme)} title={theme} size="small" color="primary" variant="outlined" sx={{ height: 18, fontSize: 11 }} />)}
          </Box>
        </Box>
      )}
      {dataset.language && dataset.language.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>Language</Typography>
          <Typography variant="body2">{dataset.language.join(', ')}</Typography>
        </Box>
      )}
    </Box>
  );
}

// ─── Distribution form ─────────────────────────────────────────────────────────

const DISTRIBUTION_FORMATS = ['Parquet', 'CSV', 'JSON', 'Avro', 'ORC', 'Kafka', 'Delta', 'XML', 'REST', 'Other'] as const;

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
  title: string; description: string; format: string;
  mediaType: string; accessUrl: string; downloadUrl: string;
}

function DistributionForm({ datasetId, onSuccess, onCancel }: { datasetId: string; onSuccess: () => void; onCancel: () => void }) {
  const { register, handleSubmit, control, watch, setValue } = useForm<DistFormValues>({
    defaultValues: { title: '', description: '', format: '', mediaType: '', accessUrl: '', downloadUrl: '' },
  });
  const qc = useQueryClient();

  const mutation = useMutation({
    mutationFn: (data: Partial<Distribution>) => datasetApi.createDistribution(datasetId, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['distributions', datasetId] }); onSuccess(); },
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

  return (
    <Paper variant="outlined" sx={{ p: 2, bgcolor: 'grey.50', display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="body2" fontWeight={600}>Add Distribution</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        <TextField {...register('title')} label="Title" size="small" fullWidth placeholder="Snowflake table endpoint" />
        <Controller
          name="format"
          control={control}
          render={({ field }) => (
            <FormControl size="small" fullWidth>
              <InputLabel>Format</InputLabel>
              <Select label="Format" value={field.value} onChange={e => { field.onChange(e.target.value); onFormatChange(e.target.value); }}>
                <MenuItem value=""><em>— select —</em></MenuItem>
                {DISTRIBUTION_FORMATS.map(f => <MenuItem key={f} value={f}>{f}</MenuItem>)}
              </Select>
            </FormControl>
          )}
        />
      </Box>
      <TextField {...register('description')} label="Description" size="small" fullWidth multiline rows={2} placeholder="Optional description…" />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        <TextField {...register('mediaType')} label="Media Type" size="small" fullWidth placeholder="application/json" />
        <TextField {...register('accessUrl')} label="Access URL" size="small" fullWidth placeholder="https://…" />
      </Box>
      <TextField {...register('downloadUrl')} label="Download URL" size="small" fullWidth placeholder="https://…" />
      {mutation.isError && <Alert severity="error">Failed to add distribution. Please try again.</Alert>}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button type="submit" size="small" variant="contained" disabled={mutation.isPending} onClick={handleSubmit(submit)} sx={{ textTransform: 'none' }}>
          {mutation.isPending ? 'Adding…' : 'Add Distribution'}
        </Button>
        <Button size="small" variant="outlined" onClick={onCancel} sx={{ textTransform: 'none' }}>Cancel</Button>
      </Box>
    </Paper>
  );
}

// ─── Distributions tab ─────────────────────────────────────────────────────────

const FORMAT_CHIP_COLORS: Record<string, 'default' | 'warning' | 'success' | 'info' | 'secondary' | 'error' | 'primary'> = {
  Parquet: 'warning',
  CSV:     'success',
  JSON:    'info',
  Avro:    'secondary',
};

function formatBytes(bytes?: number) {
  if (!bytes) return null;
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(1)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(1)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(1)} KB`;
  return `${bytes} B`;
}

function DistributionRow({ d, datasetId, tenant, canOwnerAction }: { d: Distribution; datasetId: string; tenant: string; canOwnerAction: boolean }) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            {d.format && (
              <Chip label={d.format} size="small" color={FORMAT_CHIP_COLORS[d.format] ?? 'default'} sx={{ height: 18, fontSize: 10 }} />
            )}
            {d.mediaType && <Typography variant="caption" color="text.secondary">{d.mediaType}</Typography>}
            {d.byteSize && <Typography variant="caption" color="text.disabled">{formatBytes(d.byteSize)}</Typography>}
          </Box>
          <Typography
            component={Link}
            to={`/${tenant}/datasets/${datasetId}/distributions/${d.id}`}
            variant="caption"
            color="primary"
            sx={{ textDecoration: 'none', flexShrink: 0, '&:hover': { textDecoration: 'underline' } }}
          >
            View details →
          </Typography>
        </Box>
        {d.title && <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>{d.title}</Typography>}
        {d.description && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>{d.description}</Typography>}
        {d.accessUrl && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, bgcolor: 'grey.50', borderRadius: 1, px: 1.5, py: 0.75 }}>
            <Typography variant="caption" fontFamily="monospace" color="text.secondary" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {d.accessUrl}
            </Typography>
            <Typography component="a" href={d.accessUrl} target="_blank" rel="noreferrer" variant="caption" color="primary" sx={{ textDecoration: 'none', flexShrink: 0, '&:hover': { textDecoration: 'underline' } }}>
              Open ↗
            </Typography>
          </Box>
        )}
        {d.downloadUrl && !d.accessUrl && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, bgcolor: 'grey.50', borderRadius: 1, px: 1.5, py: 0.75 }}>
            <Typography variant="caption" fontFamily="monospace" color="text.secondary" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {d.downloadUrl}
            </Typography>
            <Typography component="a" href={d.downloadUrl} target="_blank" rel="noreferrer" variant="caption" color="primary" sx={{ textDecoration: 'none', flexShrink: 0, '&:hover': { textDecoration: 'underline' } }}>
              Download ↓
            </Typography>
          </Box>
        )}
        {d.checksumValue && (
          <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.5 }}>
            {d.checksumAlgorithm ?? 'checksum'}: <Box component="code" sx={{ fontFamily: 'monospace' }}>{d.checksumValue}</Box>
          </Typography>
        )}
      </Paper>
      <PhysicalSchemaSection distributionId={d.id} datasetId={datasetId} tenant={tenant} canAction={canOwnerAction} />
    </Box>
  );
}

function DistributionsTab({ datasetId, tenant, canOwnerAction }: { datasetId: string; tenant: string; canOwnerAction: boolean }) {
  const [adding, setAdding] = useState(false);

  const { data: distributions = [] } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId),
  });

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, maxWidth: 900 }}>
      {distributions.map(d => (
        <DistributionRow key={d.id} d={d} datasetId={datasetId} tenant={tenant} canOwnerAction={canOwnerAction} />
      ))}
      {distributions.length === 0 && !adding && (
        <Typography variant="body2" color="text.secondary">No distributions yet.</Typography>
      )}
      {adding ? (
        <DistributionForm datasetId={datasetId} onSuccess={() => setAdding(false)} onCancel={() => setAdding(false)} />
      ) : (
        <Button size="small" variant="outlined" onClick={() => setAdding(true)} sx={{ textTransform: 'none', alignSelf: 'flex-start' }}>
          + Add Distribution
        </Button>
      )}
    </Box>
  );
}

// ─── Lineage tab ───────────────────────────────────────────────────────────────

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
              <button onClick={(e) => { e.stopPropagation(); (data.onToggleExpand as ((id: string) => void) | undefined)?.(id); }} title={expanded ? 'Collapse' : 'Show data elements'}
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }} onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}>
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  {expanded ? <path d="M2 6.5L5 3.5L8 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                            : <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>}
                </svg>
              </button>
              <button onClick={(e) => { e.stopPropagation(); (data.onNavigate as ((cid: string) => void) | undefined)?.(data.catalogId as string); }} title="Open in catalog"
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }} onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}>
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
            {!elementsLoading && elements.length === 0 && <div style={{ fontSize: 9, color: '#94a3b8', fontStyle: 'italic' }}>No elements</div>}
            {(elements as { id: string; name: string; logicalType?: string }[]).map(el => (
              <div key={el.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 9, padding: '1.5px 0', gap: 6, cursor: 'default' }}
                onMouseEnter={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(id)}
                onMouseLeave={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(null)}>
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
const LINEAGE_COL = 240; const LINEAGE_ROW = 100;
const DATASET_BG = '#dbeafe'; const JOB_BG = '#fef3c7'; const FOCAL_BG = '#bfdbfe';
const EXPANDED_EXTRA_HEIGHT = 185;

function resolveHandles(nodes: Node[], edges: Edge[]): Node[] {
  const xOf = new Map(nodes.map(n => [n.id, n.position.x]));
  const sp = new Map<string, Position>(); const tp = new Map<string, Position>();
  for (const e of edges) {
    const sx = xOf.get(e.source) ?? 0; const tx = xOf.get(e.target) ?? 0;
    sp.set(e.source, sx <= tx ? Position.Right : Position.Left);
    tp.set(e.target, sx <= tx ? Position.Left  : Position.Right);
  }
  return nodes.map(n => ({ ...n, sourcePosition: sp.get(n.id) ?? Position.Right, targetPosition: tp.get(n.id) ?? Position.Left }));
}

function applyExpansionOffsets(nodes: Node[], expandedNodeIds: Set<string>, extraHeight: number): Node[] {
  if (expandedNodeIds.size === 0) return nodes;
  const byX = new Map<number, Node[]>();
  for (const n of nodes) { if (!byX.has(n.position.x)) byX.set(n.position.x, []); byX.get(n.position.x)!.push(n); }
  const result = [...nodes]; const idxOf = new Map(result.map((n, i) => [n.id, i]));
  for (const col of byX.values()) {
    col.sort((a, b) => a.position.y - b.position.y); let offset = 0;
    for (const node of col) {
      if (offset > 0) { const i = idxOf.get(node.id)!; result[i] = { ...result[i], position: { ...result[i].position, y: result[i].position.y + offset } }; }
      if (expandedNodeIds.has(node.id)) offset += extraHeight;
    }
  }
  return result;
}

function makeNode(n: LineageNode, x: number, rowIdx: number, total: number, isRoot: boolean, onNavigate?: (catalogId: string) => void, onHighlightNode?: (id: string | null) => void, expandedNodeIds?: Set<string>, onToggleExpand?: (id: string) => void): Node {
  return { id: n.id, type: 'dataset', data: { label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId, onNavigate, onHighlightNode, expanded: expandedNodeIds?.has(n.id) ?? false, onToggleExpand }, position: { x, y: (rowIdx - (total - 1) / 2) * LINEAGE_ROW }, style: { background: isRoot ? FOCAL_BG : n.type === 'Job' ? JOB_BG : DATASET_BG, border: isRoot ? '2px solid #3b82f6' : '1px solid #cbd5e1', borderRadius: 8, padding: '8px 12px', fontSize: 12, maxWidth: 200, fontWeight: isRoot ? 600 : 400 } };
}

function makeEdge(e: LineageEdge, i: number, highlightedNodeId?: string | null): Edge {
  const highlighted = !!highlightedNodeId && e.toId === highlightedNodeId;
  return { id: `e-${i}`, source: e.fromId, target: e.toId, label: e.edgeType, labelStyle: { fontSize: 10 }, style: highlighted ? { stroke: '#3b82f6', strokeWidth: 2.5 } : { stroke: '#94a3b8', strokeWidth: 1.5 }, animated: e.edgeType === 'DERIVED_FROM' };
}

function buildLineageFlow(direction: LineageDirection, downGraph: LineageGraphData | undefined, upGraph: LineageGraphData | undefined, onNavigate?: (catalogId: string) => void, highlightedNodeId?: string | null, onHighlightNode?: (id: string | null) => void, expandedNodeIds?: Set<string>, onToggleExpand?: (id: string) => void): { nodes: Node[]; edges: Edge[] } | null {
  if (direction === 'downstream' && downGraph) {
    const buckets = new Map<number, LineageNode[]>();
    downGraph.nodes.forEach(n => { const d = n.depth ?? 0; if (!buckets.has(d)) buckets.set(d, []); buckets.get(d)!.push(n); });
    const downNodes = downGraph.nodes.map(n => { const d = n.depth ?? 0; const bucket = buckets.get(d)!; return makeNode(n, d * LINEAGE_COL, bucket.indexOf(n), bucket.length, n.id === downGraph.rootId, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand); });
    const downEdges = downGraph.edges.map((e, i) => makeEdge(e, i, highlightedNodeId));
    const withHandles = resolveHandles(downNodes, downEdges);
    return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges: downEdges };
  }
  if (direction === 'upstream' && upGraph) {
    const buckets = new Map<number, LineageNode[]>();
    upGraph.nodes.forEach(n => { const d = n.depth ?? 0; if (!buckets.has(d)) buckets.set(d, []); buckets.get(d)!.push(n); });
    const upNodes = upGraph.nodes.map(n => { const d = n.depth ?? 0; const bucket = buckets.get(d)!; return makeNode(n, -d * LINEAGE_COL, bucket.indexOf(n), bucket.length, n.id === upGraph.rootId, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand); });
    const upEdges = upGraph.edges.map((e, i) => makeEdge(e, i, highlightedNodeId));
    const withHandles = resolveHandles(upNodes, upEdges);
    return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges: upEdges };
  }
  if (direction === 'both' && upGraph && downGraph) {
    const signedDepths = new Map<string, number>(); const nodeById = new Map<string, LineageNode>();
    for (const n of downGraph.nodes) { signedDepths.set(n.id, n.depth ?? 0); nodeById.set(n.id, n); }
    for (const n of upGraph.nodes) { const sd = -(n.depth ?? 0); if (sd === 0) continue; signedDepths.set(n.id, sd); nodeById.set(n.id, n); }
    const cols = new Map<number, string[]>();
    for (const [id, sd] of signedDepths) { if (!cols.has(sd)) cols.set(sd, []); cols.get(sd)!.push(id); }
    const nodes: Node[] = [];
    for (const [id, sd] of signedDepths) { const n = nodeById.get(id)!; const col = cols.get(sd)!; nodes.push(makeNode(n, sd * LINEAGE_COL, col.indexOf(id), col.length, sd === 0, onNavigate, onHighlightNode, expandedNodeIds, onToggleExpand)); }
    const seen = new Set<string>(); const edges: Edge[] = [];
    for (const e of [...upGraph.edges, ...downGraph.edges]) { const eid = `${e.fromId}->${e.toId}`; if (!seen.has(eid)) { seen.add(eid); edges.push(makeEdge(e, edges.length, highlightedNodeId)); } }
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
    setExpandedNodeIds(prev => { const next = new Set(prev); if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId); return next; });
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
  const handleNavigate = useCallback((catalogId: string) => { navigate(`/${tenant}/datasets/${catalogId}`); }, [navigate, tenant]);
  const onNodeDoubleClick: NodeMouseHandler = useCallback((_evt, node) => {
    const { catalogId } = node.data as { catalogId?: string };
    if (catalogId) navigate(`/${tenant}/datasets/${catalogId}`);
  }, [navigate, tenant]);

  if (isLoading) return <Typography variant="body2" color="text.secondary">Looking up lineage…</Typography>;
  if (isError || !identity) return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Typography variant="body2" color="text.secondary">No lineage data linked to this dataset.</Typography>
      <Typography component={Link} to={`/${tenant}/lineage`} variant="body2" color="primary" sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>
        Open lineage explorer →
      </Typography>
    </Box>
  );

  const graphLoading = direction === 'both' ? (downLoading || upLoading) : direction === 'upstream' ? upLoading : downLoading;
  const flow = buildLineageFlow(direction, downGraph, upGraph, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <ToggleButtonGroup value={direction} exclusive onChange={(_, v) => v && setDirection(v)} size="small">
          <ToggleButton value="upstream" sx={{ textTransform: 'none', fontSize: 12 }}>Upstream</ToggleButton>
          <ToggleButton value="downstream" sx={{ textTransform: 'none', fontSize: 12 }}>Downstream</ToggleButton>
          <ToggleButton value="both" sx={{ textTransform: 'none', fontSize: 12 }}>Both</ToggleButton>
        </ToggleButtonGroup>
        <Typography
          component={Link}
          to={`/${tenant}/lineage?ns=${encodeURIComponent(identity.namespace)}&name=${encodeURIComponent(identity.name)}&direction=${direction}`}
          variant="caption"
          color="primary"
          sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
        >
          Open full explorer →
        </Typography>
      </Box>
      <Box sx={{ height: 'calc(100vh - 240px)', borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
        {graphLoading && (
          <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">Loading graph…</Typography>
          </Box>
        )}
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
          <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">No lineage data</Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ display: 'block' }}>{label}</Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  );
}
