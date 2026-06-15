import { useEffect, SyntheticEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Drawer from '@mui/material/Drawer';
import Typography from '@mui/material/Typography';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Divider from '@mui/material/Divider';
import CloseIcon from '@mui/icons-material/Close';
import ShareIcon from '@mui/icons-material/Share';
import { datasetApi, dataProductApi, lineageApi, userApi, iriFragment, useIriTranslations } from '@datacatalog/shared';
import type { DataProduct } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';
import DistributionsTab from './DistributionsTab';
import LogicalSchemaTable from './LogicalSchemaTable';
import MiniLineageGraph from './MiniLineageGraph';
import TermsOfUseTab from './TermsOfUseTab';
import BookmarkButton from './BookmarkButton';

const DATASET_TABS = [
  { key: 'overview',       label: 'Overview' },
  { key: 'distributions',  label: 'Distributions' },
  { key: 'schema',         label: 'Model' },
  { key: 'lineage',        label: 'Lineage' },
  { key: 'terms',          label: 'Terms' },
  { key: 'access',         label: 'Access' },
] as const;

const DATA_PRODUCT_TABS = [
  { key: 'overview',  label: 'Overview' },
  { key: 'datasets',  label: 'Datasets' },
  { key: 'lineage',   label: 'Lineage' },
  { key: 'terms',     label: 'Terms' },
] as const;

type DrawerTab = 'overview' | 'distributions' | 'schema' | 'lineage' | 'terms' | 'access' | 'datasets';

export default function DatasetDetailDrawer() {
  const { openDatasetId, openEntityType, activeTab, openDataset, closeDrawer, setTab } = useDrawerStore();
  const [searchParams, setSearchParams] = useSearchParams();

  const isDataProduct = openEntityType === 'DATA_PRODUCT';
  const TABS = isDataProduct ? DATA_PRODUCT_TABS : DATASET_TABS;

  useEffect(() => {
    if (openDatasetId) {
      setSearchParams(prev => {
        prev.set('ds', openDatasetId);
        prev.set('et', openEntityType);
        return prev;
      }, { replace: true });
    } else {
      setSearchParams(prev => {
        prev.delete('ds');
        prev.delete('et');
        return prev;
      }, { replace: true });
    }
  }, [openDatasetId, openEntityType, setSearchParams]);

  // Reset to overview if current tab isn't available for this entity type
  useEffect(() => {
    const validKeys = TABS.map(t => t.key);
    if (!validKeys.includes(activeTab as typeof TABS[number]['key'])) {
      setTab('overview');
    }
  }, [openEntityType]); // eslint-disable-line react-hooks/exhaustive-deps

  const { data: dataset, isLoading: datasetLoading } = useQuery({
    queryKey: ['dataset', openDatasetId],
    queryFn: () => datasetApi.get(openDatasetId!),
    enabled: !!openDatasetId && !isDataProduct,
  });

  const { data: dataProduct, isLoading: dataProductLoading } = useQuery({
    queryKey: ['data-product', openDatasetId],
    queryFn: () => dataProductApi.get(openDatasetId!),
    enabled: !!openDatasetId && isDataProduct,
  });

  const entity = isDataProduct ? dataProduct : dataset;
  const isLoading = isDataProduct ? dataProductLoading : datasetLoading;

  const { data: linkedDatasets = [], isLoading: linkedDatasetsLoading } = useQuery({
    queryKey: ['data-product-datasets', openDatasetId],
    queryFn: () => dataProductApi.listDatasets(openDatasetId!),
    enabled: !!openDatasetId && isDataProduct && activeTab === 'datasets',
    staleTime: 60_000,
  });

  const { data: semanticContext } = useQuery({
    queryKey: ['dataset-semantic-context', openDatasetId],
    queryFn: () => datasetApi.getSemanticContext(openDatasetId!),
    enabled: !!openDatasetId && !isDataProduct && activeTab === 'overview',
    staleTime: 60_000,
  });

  const { data: owner } = useQuery({
    queryKey: ['user-by-keycloak', entity?.ownerId],
    queryFn: () => userApi.getByKeycloakId(entity!.ownerId!),
    enabled: !!entity?.ownerId && activeTab === 'overview',
    staleTime: 300_000,
    retry: false,
  });

  const iriList = [
    ...(entity?.themes ?? []),
    ...(!isDataProduct ? (dataset?.conformsTo ?? []) : []),
    ...(entity?.license ? [entity.license] : []),
  ];
  const translations = useIriTranslations(iriList);
  const t = (iri: string) => translations[iri] ?? iriFragment(iri);

  const tabIndex = TABS.findIndex(tb => tb.key === activeTab);

  function handleTabChange(_: SyntheticEvent, newIndex: number) {
    setTab(TABS[newIndex].key as DrawerTab);
  }

  return (
    <Drawer
      anchor="right"
      open={!!openDatasetId}
      onClose={closeDrawer}
      variant="persistent"
      PaperProps={{ sx: { width: '45%', minWidth: 360, display: 'flex', flexDirection: 'column' } }}
    >
      {/* Header */}
      <Box sx={{ px: 2.5, pt: 2, pb: 0, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper', position: 'sticky', top: 0, zIndex: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 1 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            {isLoading ? (
              <Skeleton variant="text" width={200} height={24} />
            ) : (
              <>
                <Typography variant="subtitle1" fontWeight={700} noWrap>{entity?.title}</Typography>
                {isDataProduct && dataProduct && (
                  <Chip label={dataProduct.lifecycleStatus} size="small" sx={{ height: 18, fontSize: 10, mt: 0.25 }} />
                )}
              </>
            )}
            {entity?.updatedAt && (
              <Typography variant="caption" color="text.secondary">
                Updated {new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(
                  Math.round((new Date(entity.updatedAt).getTime() - Date.now()) / 86400000), 'day'
                )}
              </Typography>
            )}
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0 }}>
            {entity && <BookmarkButton datasetId={entity.id} datasetTitle={entity.title} />}
            <IconButton size="small" onClick={() => navigator.clipboard.writeText(window.location.href)} title="Copy share link">
              <ShareIcon fontSize="small" />
            </IconButton>
            <IconButton size="small" onClick={closeDrawer}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        <Tabs
          value={tabIndex >= 0 ? tabIndex : 0}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
          sx={{ minHeight: 36, '& .MuiTab-root': { minHeight: 36, py: 0.5, fontSize: 13 } }}
        >
          {TABS.map(tab => <Tab key={tab.key} label={tab.label} />)}
        </Tabs>
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, overflowY: 'auto', px: 2.5, py: 2 }}>
        {isLoading && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {[...Array(4)].map((_, i) => <Skeleton key={i} variant="text" height={16} />)}
          </Box>
        )}

        {!isLoading && entity && (
          <>
            {activeTab === 'overview' && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                {entity.description && (
                  <Typography variant="body2" color="text.primary" sx={{ lineHeight: 1.7 }}>
                    {entity.description}
                  </Typography>
                )}

                {isDataProduct && (dataProduct as DataProduct).purpose && (
                  <Box>
                    <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.75, display: 'block' }}>Purpose</Typography>
                    <Typography variant="body2">{(dataProduct as DataProduct).purpose}</Typography>
                  </Box>
                )}

                {entity.keywords && entity.keywords.length > 0 && (
                  <Box>
                    <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.75, display: 'block' }}>Keywords</Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                      {entity.keywords.map(kw => (
                        <Chip key={kw} label={kw} size="small" variant="outlined" />
                      ))}
                    </Box>
                  </Box>
                )}

                {!isDataProduct && semanticContext && semanticContext.semanticTypes.length > 0 && (
                  <Box>
                    <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.75, display: 'block' }}>Semantic Types</Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                      {semanticContext.semanticTypes.map(type => {
                        const isFibo = (semanticContext.vocabConceptIris ?? []).some(
                          iri => iri.includes('edmcouncil.org/fibo') && iri.endsWith(type)
                        );
                        const isSchema = (semanticContext.vocabConceptIris ?? []).some(
                          iri => iri.includes('schema.org') && iri.endsWith(type)
                        );
                        const color = isFibo ? 'primary' : isSchema ? 'success' : 'default';
                        const prefix = isFibo ? 'FIBO' : isSchema ? 'schema' : null;
                        return (
                          <Chip key={type} label={prefix ? `${prefix}: ${type}` : type} color={color} size="small" />
                        );
                      })}
                    </Box>
                  </Box>
                )}

                <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5 }}>
                  {entity.ownerId && (
                    <Box sx={{ gridColumn: '1 / -1', display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Box sx={{ width: 32, height: 32, borderRadius: '50%', bgcolor: 'primary.100', color: 'primary.main', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>
                        {owner ? (owner.firstName?.[0] ?? owner.email[0]).toUpperCase() : '?'}
                      </Box>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="caption" color="text.secondary" display="block">Data Owner</Typography>
                        <Typography variant="body2" noWrap>
                          {owner
                            ? (owner.firstName || owner.lastName ? `${owner.firstName ?? ''} ${owner.lastName ?? ''}`.trim() : owner.email)
                            : <Typography component="span" variant="body2" color="text.disabled" fontStyle="italic">Loading…</Typography>}
                        </Typography>
                      </Box>
                    </Box>
                  )}
                  {!isDataProduct && dataset?.version && <MetaItem label="Version" value={dataset.version} />}
                  {!isDataProduct && dataset?.accrualPeriodicity && <MetaItem label="Frequency" value={friendlyPeriodicity(dataset.accrualPeriodicity)} />}
                  {entity.license && <MetaItem label="License" value={t(entity.license)} />}
                  {entity.themes && entity.themes.length > 0 && (
                    <Box sx={{ gridColumn: '1 / -1' }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>Themes</Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                        {entity.themes.map(theme => (
                          <Chip key={theme} label={t(theme)} size="small" color="primary" variant="outlined" title={theme} />
                        ))}
                      </Box>
                    </Box>
                  )}
                </Box>

                {!isDataProduct && (
                  <Button size="small" onClick={() => setTab('distributions')} sx={{ alignSelf: 'flex-start', textTransform: 'none', fontSize: 13 }}>
                    View distributions & physical schema →
                  </Button>
                )}
              </Box>
            )}

            {!isDataProduct && activeTab === 'distributions' && <DistributionsTab datasetId={entity.id} />}
            {!isDataProduct && activeTab === 'schema' && <LogicalSchemaTable datasetId={entity.id} />}
            {isDataProduct && activeTab === 'datasets' && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {linkedDatasetsLoading && (
                  [...Array(3)].map((_, i) => <Skeleton key={i} variant="rounded" height={60} />)
                )}
                {!linkedDatasetsLoading && linkedDatasets.length === 0 && (
                  <Typography variant="body2" color="text.disabled" sx={{ py: 4, textAlign: 'center' }}>
                    No datasets linked to this data product.
                  </Typography>
                )}
                {!linkedDatasetsLoading && linkedDatasets.map(ds => (
                  <Card
                    key={ds.id}
                    variant="outlined"
                    sx={{ cursor: 'pointer', '&:hover': { borderColor: 'primary.light', boxShadow: 1 } }}
                    onClick={() => openDataset(ds.id, 'DATASET')}
                  >
                    <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
                      <Typography variant="body2" fontWeight={600} noWrap>{ds.title}</Typography>
                      {ds.description && (
                        <Typography variant="caption" color="text.secondary"
                          sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {ds.description}
                        </Typography>
                      )}
                    </CardContent>
                  </Card>
                ))}
              </Box>
            )}
            {activeTab === 'lineage' && <LineageTab datasetId={entity.id} />}
            {activeTab === 'terms' && <TermsOfUseTab datasetId={entity.id} />}

            {!isDataProduct && activeTab === 'access' && (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {entity.license && <MetaItem label="License" value={t(entity.license)} />}
                {dataset?.conformsTo && dataset.conformsTo.length > 0 && (
                  <Box>
                    <Typography variant="caption" color="text.secondary" fontWeight={600} display="block" sx={{ mb: 0.5 }}>Conforms To</Typography>
                    {dataset.conformsTo.map(c => (
                      <Typography key={c} variant="body2" component="a" href={c} target="_blank" rel="noreferrer" display="block" color="primary" title={c} sx={{ '&:hover': { textDecoration: 'underline' } }}>
                        {t(c)}
                      </Typography>
                    ))}
                  </Box>
                )}
                <Divider />
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button variant="contained" size="small">Request Access</Button>
                  <Button variant="outlined" size="small">Contact Owner</Button>
                </Box>
              </Box>
            )}
          </>
        )}
      </Box>
    </Drawer>
  );
}

function LineageTab({ datasetId }: { datasetId: string }) {
  const navigate = useNavigate();
  const { data: identity, isLoading, isError } = useQuery({
    queryKey: ['lineage-identity', datasetId],
    queryFn: () => lineageApi.getCatalogLineageIdentity(datasetId),
    retry: false,
  });

  if (isLoading) return <Typography variant="caption" color="text.secondary" sx={{ py: 2, display: 'block', textAlign: 'center' }}>Looking up lineage...</Typography>;
  if (isError || !identity) return (
    <Typography variant="caption" color="text.secondary" sx={{ py: 4, display: 'block', textAlign: 'center' }}>No lineage data linked to this dataset.</Typography>
  );

  return (
    <MiniLineageGraph
      lineageId={identity.id}
      onOpenFull={() => navigate(`/lineage?ns=${encodeURIComponent(identity.namespace)}&name=${encodeURIComponent(identity.name)}`)}
    />
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" fontWeight={600} display="block">{label}</Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  );
}

function friendlyPeriodicity(iri: string): string {
  const map: Record<string, string> = {
    'http://purl.org/cld/freq/daily':      'Daily',
    'http://purl.org/cld/freq/weekly':     'Weekly',
    'http://purl.org/cld/freq/monthly':    'Monthly',
    'http://purl.org/cld/freq/quarterly':  'Quarterly',
    'http://purl.org/cld/freq/annual':     'Annual',
    'http://purl.org/cld/freq/continuous': 'Continuous',
  };
  return map[iri] ?? iri.split('/').pop() ?? iri;
}
