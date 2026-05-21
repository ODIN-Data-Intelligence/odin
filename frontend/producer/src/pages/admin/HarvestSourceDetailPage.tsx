import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { harvestSourceApi, harvestJobApi } from '@datacatalog/shared';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';

export default function HarvestSourceDetailPage() {
  const { id, tenant } = useParams();

  const { data: source } = useQuery({
    queryKey: ['harvest-source', id],
    queryFn: () => harvestSourceApi.get(id!),
    enabled: !!id,
  });

  const { data: jobs = [] } = useQuery({
    queryKey: ['harvest-jobs'],
    queryFn: () => harvestJobApi.list(),
  });

  const testMut = useMutation({
    mutationFn: () => harvestSourceApi.test(id!),
  });

  const sourceJobs = jobs.filter(j => j.sourceId === id);

  if (!source) return <div className="p-6 text-sm text-gray-500">Loading...</div>;

  return (
    <div>
      <PageHeader
        title={source.name}
        description={`Source type: ${source.sourceType}`}
        actions={
          <div className="flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => testMut.mutate()}>
              {testMut.isPending ? 'Testing...' : 'Test Connection'}
            </Button>
          </div>
        }
      />

      {testMut.data && (
        <div className={`mx-6 mt-4 p-3 rounded text-sm ${testMut.data.success ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
          {testMut.data.success ? 'Connection successful' : testMut.data.message ?? 'Connection failed'}
        </div>
      )}

      <div className="p-6 space-y-6">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <dl className="grid grid-cols-2 gap-4 text-sm">
            {source.baseUrl && (
              <div><dt className="text-xs text-gray-500">Base URL</dt><dd className="font-mono">{source.baseUrl}</dd></div>
            )}
            {source.region && (
              <div><dt className="text-xs text-gray-500">Region</dt><dd>{source.region}</dd></div>
            )}
            {source.databaseName && (
              <div><dt className="text-xs text-gray-500">Database</dt><dd>{source.databaseName}</dd></div>
            )}
            {source.schemaFilter && source.schemaFilter.length > 0 && (
              <div><dt className="text-xs text-gray-500">Schema Filter</dt><dd>{source.schemaFilter.join(', ')}</dd></div>
            )}
          </dl>
        </div>

        <section>
          <h2 className="text-base font-semibold text-gray-800 mb-3">Jobs</h2>
          <div className="space-y-2">
            {sourceJobs.map(job => (
              <div key={job.id} className="bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between">
                <div>
                  <p className="font-medium text-gray-900">{job.name}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{job.scheduleCron ?? 'Manual trigger'}</p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge label={job.enabled ? 'Enabled' : 'Disabled'} className={job.enabled ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'} />
                </div>
              </div>
            ))}
            {sourceJobs.length === 0 && <p className="text-sm text-gray-500">No jobs for this source.</p>}
          </div>
        </section>
      </div>
    </div>
  );
}
