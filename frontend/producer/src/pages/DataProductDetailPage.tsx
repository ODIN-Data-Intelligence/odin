import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import { dataProductApi, lineageApi, PageHeader } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';
import LineageGraph from '../components/lineage/LineageGraph';
import { LIFECYCLE_COLORS, formatDate } from '../lib/utils';

const TABS = ['Overview', 'Ports', 'Datasets', 'Lineage'] as const;
type TabValue = typeof TABS[number];

const LIFECYCLE_STEPS = ['Ideation', 'Design', 'Build', 'Deploy', 'Consume'];

export default function DataProductDetailPage() {
  const { id, tenant } = useParams();
  const [activeTab, setActiveTab] = useState<TabValue>('Overview');
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

  if (isLoading) return <Typography variant="body2" color="text.secondary" sx={{ p: 3 }}>Loading…</Typography>;
  if (!dp) return <Typography variant="body2" color="error" sx={{ p: 3 }}>Not found</Typography>;

  const currentIdx = LIFECYCLE_STEPS.indexOf(dp.lifecycleStatus);
  const nextStatus = LIFECYCLE_STEPS[currentIdx + 1];

  return (
    <Box>
      <PageHeader
        title={dp.title}
        actions={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chip label={dp.lifecycleStatus} size="small" sx={{ height: 20, fontSize: 11 }} />
            {nextStatus && (
              <Button size="small" variant="contained" onClick={() => lifecycleMut.mutate(nextStatus)} disabled={lifecycleMut.isPending} sx={{ textTransform: 'none' }}>
                Advance to {nextStatus}
              </Button>
            )}
          </Box>
        }
      />

      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)} sx={{ px: 2 }}>
          {TABS.map(tab => <Tab key={tab} label={tab} value={tab} sx={{ textTransform: 'none', fontSize: 13 }} />)}
        </Tabs>
      </Box>

      <Box sx={{ p: 3 }}>
        {activeTab === 'Overview' && (
          <Box sx={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 2 }}>
            {dp.description && <Typography variant="body2" color="text.secondary">{dp.description}</Typography>}
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.5 }}>
              <DlItem label="Updated" value={formatDate(dp.updatedAt)} />
              <DlItem label="Sensitivity" value={dp.informationSensitivity ?? '—'} />
              {dp.purpose && <DlItem label="Purpose" value={dp.purpose} />}
            </Box>
            {dp.keywords && dp.keywords.length > 0 && (
              <Box>
                <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>Keywords</Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {dp.keywords.map(kw => <Chip key={kw} label={kw} size="small" sx={{ height: 18, fontSize: 11 }} />)}
                </Box>
              </Box>
            )}
          </Box>
        )}
        {activeTab === 'Ports' && (
          <Typography variant="body2" color="text.secondary">Ports management — connect input/output ports to datasets and services.</Typography>
        )}
        {activeTab === 'Datasets' && <DatasetsTab dataProductId={id!} tenant={tenant!} />}
        {activeTab === 'Lineage' && <DataProductLineageTab dataProductId={id!} tenant={tenant!} />}
      </Box>
    </Box>
  );
}

function DataProductLineageTab({ dataProductId, tenant }: { dataProductId: string; tenant: string }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: datasets = [], isLoading } = useQuery({
    queryKey: ['data-product-datasets', dataProductId],
    queryFn: () => dataProductApi.listDatasets(dataProductId),
  });

  const activeId = selectedId ?? datasets[0]?.id ?? null;

  if (isLoading) return <Typography variant="body2" color="text.secondary">Loading datasets…</Typography>;

  if (datasets.length === 0) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Typography variant="body2" color="text.secondary">No datasets linked to this data product.</Typography>
        <Typography component={Link} to={`/${tenant}/lineage`} variant="body2" color="primary" sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>
          Open lineage explorer →
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {datasets.length > 1 ? (
          <Select
            value={activeId ?? ''}
            onChange={e => setSelectedId(e.target.value)}
            size="small"
            sx={{ fontSize: 13, minWidth: 200 }}
          >
            {datasets.map((ds: Dataset) => <MenuItem key={ds.id} value={ds.id} sx={{ fontSize: 13 }}>{ds.title}</MenuItem>)}
          </Select>
        ) : (
          <Typography variant="caption" color="text.secondary">Lineage for: <Box component="span" fontWeight={600}>{datasets[0].title}</Box></Typography>
        )}
        <Typography component={Link} to={`/${tenant}/lineage`} variant="caption" color="primary" sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}>
          Open full explorer →
        </Typography>
      </Box>
      {activeId && <DatasetLineagePanel datasetId={activeId} />}
    </Box>
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

  if (isLoading) return (
    <Box sx={{ height: 'calc(100vh - 220px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Typography variant="body2" color="text.secondary">Looking up lineage…</Typography>
    </Box>
  );
  if (isError || !identity) return <Typography variant="body2" color="text.secondary">No lineage data linked to this dataset.</Typography>;

  return (
    <Box sx={{ height: 'calc(100vh - 220px)', borderRadius: 2, border: 1, borderColor: 'divider', overflow: 'hidden' }}>
      {graph
        ? <LineageGraph graph={graph} onNavigate={(catalogId) => navigate(`/${tenant}/datasets/${catalogId}`)} />
        : <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">Loading graph…</Typography>
          </Box>
      }
    </Box>
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

  if (isLoading) return <Typography variant="body2" color="text.secondary">Loading…</Typography>;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, maxWidth: 800 }}>
      {datasets.length === 0 && (
        <Typography variant="body2" color="text.secondary">No datasets linked via output ports yet.</Typography>
      )}
      {datasets.map((ds: Dataset) => (
        <Paper key={ds.id} variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography
              component={Link}
              to={`/${tenant}/datasets/${ds.id}`}
              variant="body2"
              fontWeight={600}
              color="primary"
              sx={{ textDecoration: 'none', display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', '&:hover': { textDecoration: 'underline' } }}
            >
              {ds.title}
            </Typography>
            {ds.description && (
              <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                {ds.description}
              </Typography>
            )}
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.75 }}>
              {ds.keywords?.map(kw => <Chip key={kw} label={kw} size="small" sx={{ height: 16, fontSize: 10 }} />)}
            </Box>
          </Box>
          <Button size="small" color="error" variant="text" onClick={() => unlinkMut.mutate(ds.id)} sx={{ textTransform: 'none', fontSize: 11, flexShrink: 0 }}>
            Unlink
          </Button>
        </Paper>
      ))}
    </Box>
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ display: 'block' }}>{label}</Typography>
      <Typography variant="body2" color="text.primary">{value}</Typography>
    </Box>
  );
}
