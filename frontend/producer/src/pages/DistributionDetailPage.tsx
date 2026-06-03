import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { datasetApi } from '@datacatalog/shared';
import type { Distribution } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import PhysicalSchemaSection from '../components/catalog/PhysicalSchemaSection';
import { formatDate } from '../lib/utils';
import { useAuthStore } from '../store/authStore';

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

export default function DistributionDetailPage() {
  const { datasetId, id, tenant } = useParams();
  const { userId } = useAuthStore();

  const { data: distributions = [], isLoading } = useQuery({
    queryKey: ['distributions', datasetId],
    queryFn: () => datasetApi.listDistributions(datasetId!),
    enabled: !!datasetId,
  });

  const { data: dataset } = useQuery({
    queryKey: ['dataset', datasetId],
    queryFn: () => datasetApi.get(datasetId!),
    enabled: !!datasetId,
  });

  const dist: Distribution | undefined = distributions.find(d => d.id === id);
  const canOwnerAction = !!dataset?.ownerId && dataset.ownerId === userId;

  if (isLoading) return <div className="p-6 text-sm text-gray-400">Loading...</div>;
  if (!dist) return <div className="p-6 text-sm text-red-500">Distribution not found</div>;

  return (
    <div>
      <PageHeader
        title={dist.title ?? dist.format ?? 'Distribution'}
        description={dist.description}
        actions={
          <Link
            to={`/${tenant}/datasets/${datasetId}`}
            className="text-sm text-blue-600 hover:underline"
          >
            ← Back to dataset
          </Link>
        }
      />

      <div className="p-6 space-y-6 max-w-5xl">
        {/* Metadata */}
        <section className="bg-white border border-gray-200 rounded-lg p-4">
          <h2 className="text-sm font-semibold text-gray-800 mb-3">Details</h2>
          <dl className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-sm">
            {dist.format && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Format</dt>
                <dd className="mt-0.5">
                  <span className={`px-2 py-0.5 text-xs rounded font-medium ${FORMAT_COLORS[dist.format] ?? 'bg-gray-100 text-gray-600'}`}>
                    {dist.format}
                  </span>
                </dd>
              </div>
            )}
            {dist.mediaType && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Media Type</dt>
                <dd className="mt-0.5 text-gray-700 font-mono text-xs">{dist.mediaType}</dd>
              </div>
            )}
            {dist.byteSize && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Size</dt>
                <dd className="mt-0.5 text-gray-700">{formatBytes(dist.byteSize)}</dd>
              </div>
            )}
            {dist.compressFormat && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Compression</dt>
                <dd className="mt-0.5 text-gray-700">{dist.compressFormat}</dd>
              </div>
            )}
            {dist.availability && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Availability</dt>
                <dd className="mt-0.5 text-gray-700">{dist.availability}</dd>
              </div>
            )}
            {dist.createdAt && (
              <div>
                <dt className="text-xs font-medium text-gray-500">Created</dt>
                <dd className="mt-0.5 text-gray-700">{formatDate(dist.createdAt)}</dd>
              </div>
            )}
          </dl>
        </section>

        {/* Access */}
        {(dist.accessUrl || dist.downloadUrl) && (
          <section className="bg-white border border-gray-200 rounded-lg p-4">
            <h2 className="text-sm font-semibold text-gray-800 mb-3">Access</h2>
            <div className="space-y-2">
              {dist.accessUrl && (
                <div>
                  <p className="text-xs font-medium text-gray-500 mb-1">Access URL</p>
                  <div className="flex items-center gap-2 bg-gray-50 rounded px-3 py-2">
                    <span className="text-xs font-mono text-gray-700 truncate flex-1">{dist.accessUrl}</span>
                    <button
                      onClick={() => navigator.clipboard.writeText(dist.accessUrl!)}
                      className="text-xs text-gray-500 hover:text-gray-700 flex-shrink-0"
                    >
                      Copy
                    </button>
                    <a href={dist.accessUrl} target="_blank" rel="noreferrer"
                      className="text-xs text-blue-600 hover:underline flex-shrink-0">Open ↗</a>
                  </div>
                </div>
              )}
              {dist.downloadUrl && (
                <div>
                  <p className="text-xs font-medium text-gray-500 mb-1">Download URL</p>
                  <div className="flex items-center gap-2 bg-gray-50 rounded px-3 py-2">
                    <span className="text-xs font-mono text-gray-700 truncate flex-1">{dist.downloadUrl}</span>
                    <a href={dist.downloadUrl} download
                      className="text-xs text-blue-600 hover:underline flex-shrink-0">Download ↓</a>
                  </div>
                </div>
              )}
            </div>
          </section>
        )}

        {/* Checksum */}
        {dist.checksumValue && (
          <section className="bg-white border border-gray-200 rounded-lg p-4">
            <h2 className="text-sm font-semibold text-gray-800 mb-2">Integrity</h2>
            <p className="text-xs text-gray-500">
              <span className="font-medium">{dist.checksumAlgorithm ?? 'Checksum'}:</span>{' '}
              <span className="font-mono">{dist.checksumValue}</span>
            </p>
          </section>
        )}

        <PhysicalSchemaSection
          distributionId={id!}
          datasetId={datasetId!}
          tenant={tenant!}
          canAction={canOwnerAction}
        />
      </div>
    </div>
  );
}
