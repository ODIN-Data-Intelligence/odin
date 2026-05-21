import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { datasetApi, lineageApi } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';
import DistributionsTab from './DistributionsTab';
import LogicalSchemaTable from './LogicalSchemaTable';
import MiniLineageGraph from './MiniLineageGraph';

const TABS = [
  { key: 'overview',       label: 'Overview' },
  { key: 'distributions',  label: 'Distributions' },
  { key: 'schema',         label: 'Schema' },
  { key: 'lineage',        label: 'Lineage' },
  { key: 'access',         label: 'Access' },
] as const;

type DrawerTab = typeof TABS[number]['key'];

export default function DatasetDetailDrawer() {
  const { openDatasetId, activeTab, closeDrawer, setTab } = useDrawerStore();
  const [searchParams, setSearchParams] = useSearchParams();

  useEffect(() => {
    if (openDatasetId) {
      setSearchParams(prev => { prev.set('ds', openDatasetId); return prev; }, { replace: true });
    } else {
      setSearchParams(prev => { prev.delete('ds'); return prev; }, { replace: true });
    }
  }, [openDatasetId, setSearchParams]);

  const { data: dataset, isLoading } = useQuery({
    queryKey: ['dataset', openDatasetId],
    queryFn: () => datasetApi.get(openDatasetId!),
    enabled: !!openDatasetId,
  });

  if (!openDatasetId) return null;

  function copyShareLink() {
    navigator.clipboard.writeText(window.location.href);
  }

  return (
    <div className="w-1/2 flex-shrink-0 border-l border-gray-200 bg-white flex flex-col h-full overflow-hidden shadow-lg">
      <div className="px-5 py-4 border-b bg-white sticky top-0 z-10">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            {isLoading ? (
              <div className="h-5 bg-gray-200 rounded animate-pulse w-48" />
            ) : (
              <h2 className="text-base font-semibold text-gray-900 truncate">{dataset?.title}</h2>
            )}
            {dataset && (
              <p className="text-xs text-gray-500 mt-0.5 flex items-center gap-2">
                {dataset.updatedAt && `Updated ${new Intl.RelativeTimeFormat('en', { numeric: 'auto' }).format(
                  Math.round((new Date(dataset.updatedAt).getTime() - Date.now()) / 86400000), 'day'
                )}`}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            <button onClick={copyShareLink} title="Copy share link" className="text-gray-400 hover:text-gray-600 text-sm px-2 py-1 rounded hover:bg-gray-100">
              Share
            </button>
            <button onClick={closeDrawer} className="text-gray-400 hover:text-gray-600 text-xl leading-none">&times;</button>
          </div>
        </div>

        <nav className="flex gap-0 mt-3 border-b -mb-px overflow-x-auto">
          {TABS.map(tab => (
            <button
              key={tab.key}
              onClick={() => setTab(tab.key as DrawerTab)}
              className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${activeTab === tab.key ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {isLoading && (
          <div className="space-y-3">
            {[...Array(4)].map((_, i) => <div key={i} className="h-4 bg-gray-100 rounded animate-pulse" />)}
          </div>
        )}

        {!isLoading && dataset && (
          <>
            {activeTab === 'overview' && (
              <div className="space-y-5">
                {dataset.description && (
                  <p className="text-sm text-gray-700 leading-relaxed">{dataset.description}</p>
                )}

                {dataset.keywords && dataset.keywords.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-gray-500 mb-2">Keywords</p>
                    <div className="flex flex-wrap gap-1.5">
                      {dataset.keywords.map(kw => (
                        <span key={kw} className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded-full">{kw}</span>
                      ))}
                    </div>
                  </div>
                )}

                <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-xs">
                  {dataset.version && <DlItem label="Version" value={dataset.version} />}
                  {dataset.accrualPeriodicity && <DlItem label="Frequency" value={friendlyPeriodicity(dataset.accrualPeriodicity)} />}
                  {dataset.license && <DlItem label="License" value={dataset.license} />}
                  {dataset.themes && dataset.themes.length > 0 && (
                    <div className="col-span-2">
                      <dt className="text-xs font-medium text-gray-500">Themes</dt>
                      <dd className="mt-0.5 flex flex-wrap gap-1">
                        {dataset.themes.map(t => (
                          <span key={t} className="px-1.5 py-0.5 bg-blue-50 text-blue-700 rounded text-xs">{t.split('/').pop()}</span>
                        ))}
                      </dd>
                    </div>
                  )}
                </dl>

                <div className="pt-1">
                  <button
                    onClick={() => setTab('distributions')}
                    className="text-xs text-blue-600 hover:text-blue-700 font-medium"
                  >
                    View distributions & physical schema →
                  </button>
                </div>
              </div>
            )}

            {activeTab === 'distributions' && (
              <DistributionsTab datasetId={dataset.id} />
            )}

            {activeTab === 'schema' && (
              <LogicalSchemaTable datasetId={dataset.id} />
            )}

            {activeTab === 'lineage' && (
              <LineageTab datasetId={dataset.id} />
            )}

            {activeTab === 'access' && (
              <div className="space-y-4">
                <dl className="grid grid-cols-1 gap-3 text-sm">
                  {dataset.license && <DlItem label="License" value={dataset.license} />}
                  {dataset.conformsTo && dataset.conformsTo.length > 0 && (
                    <div>
                      <dt className="text-xs font-medium text-gray-500">Conforms To</dt>
                      <dd className="mt-0.5 space-y-0.5">
                        {dataset.conformsTo.map(c => (
                          <a key={c} href={c} target="_blank" rel="noreferrer" className="block text-xs text-blue-600 hover:underline">{c}</a>
                        ))}
                      </dd>
                    </div>
                  )}
                </dl>
                <div className="flex gap-2 pt-2">
                  <button className="px-4 py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700">
                    Request Access
                  </button>
                  <button className="px-4 py-2 border border-gray-300 text-gray-700 rounded text-sm font-medium hover:bg-gray-50">
                    Contact Owner
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function LineageTab({ datasetId }: { datasetId: string }) {
  const { data: identity, isLoading, isError } = useQuery({
    queryKey: ['lineage-identity', datasetId],
    queryFn: () => lineageApi.getCatalogLineageIdentity(datasetId),
    retry: false,
  });

  if (isLoading) return <div className="text-xs text-gray-400 py-4 text-center">Looking up lineage...</div>;
  if (isError || !identity) return (
    <p className="text-xs text-gray-400 text-center py-6">No lineage data linked to this dataset.</p>
  );

  return (
    <MiniLineageGraph
      namespace={identity.namespace}
      name={identity.name}
    />
  );
}

function DlItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500">{label}</dt>
      <dd className="mt-0.5 text-gray-800">{value}</dd>
    </div>
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
