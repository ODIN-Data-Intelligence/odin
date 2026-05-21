import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { harvestSourceApi, harvestJobApi } from '@datacatalog/shared';
import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';
import Badge from '../../components/ui/Badge';
import HarvestSourceForm from '../../components/admin/HarvestSourceForm';

export default function HarvestPage() {
  const { tenant } = useParams();
  const [showForm, setShowForm] = useState(false);
  const qc = useQueryClient();

  const { data: sources = [] } = useQuery({
    queryKey: ['harvest-sources'],
    queryFn: () => harvestSourceApi.list(),
  });

  const { data: jobs = [] } = useQuery({
    queryKey: ['harvest-jobs'],
    queryFn: () => harvestJobApi.list(),
  });

  const triggerMut = useMutation({
    mutationFn: (jobId: string) => harvestJobApi.trigger(jobId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['harvest-jobs'] }),
  });

  const SOURCE_TYPE_LABELS: Record<string, string> = {
    dcat_http: 'DCAT HTTP',
    aws_glue: 'AWS Glue',
    snowflake: 'Snowflake',
    teradata: 'Teradata',
  };

  return (
    <div>
      <PageHeader
        title="Harvest"
        description="Configure sources and schedule metadata harvests"
        actions={<Button onClick={() => setShowForm(true)}>+ Add Source</Button>}
      />

      <div className="p-6 space-y-8">
        <section>
          <h2 className="text-base font-semibold text-gray-800 mb-3">Sources</h2>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Endpoint</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {sources.map(src => (
                  <tr key={src.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">
                      <Link to={`/${tenant}/admin/harvest/sources/${src.id}`} className="hover:text-blue-600">
                        {src.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <Badge label={SOURCE_TYPE_LABELS[src.sourceType] ?? src.sourceType} className="bg-blue-50 text-blue-700" />
                    </td>
                    <td className="px-4 py-3 text-gray-500 truncate max-w-xs">{src.baseUrl ?? src.databaseName ?? '—'}</td>
                    <td className="px-4 py-3 text-right" />
                  </tr>
                ))}
                {sources.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">No sources configured</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2 className="text-base font-semibold text-gray-800 mb-3">Jobs</h2>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Schedule</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {jobs.map(job => (
                  <tr key={job.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">{job.name}</td>
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">{job.scheduleCron ?? 'Manual'}</td>
                    <td className="px-4 py-3">
                      <Badge label={job.enabled ? 'Enabled' : 'Disabled'} className={job.enabled ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" variant="secondary" onClick={() => triggerMut.mutate(job.id)}>
                        Run Now
                      </Button>
                    </td>
                  </tr>
                ))}
                {jobs.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">No jobs configured</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>

      {showForm && <HarvestSourceForm onClose={() => { setShowForm(false); qc.invalidateQueries({ queryKey: ['harvest-sources'] }); }} />}
    </div>
  );
}
