import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { LogicalModel } from '@datacatalog/shared';
import Button from '../ui/Button';
import Badge from '../ui/Badge';
import ClassificationBadge from './ClassificationBadge';
import ClassificationRecommendationRow from './ClassificationRecommendationRow';

function humanizeIri(iri: string): string {
  const fragment = iri.split(/[/#]/).pop() ?? iri;
  return fragment.replace(/([A-Z])/g, ' $1').trim();
}

const MATCH_TYPE_COLORS: Record<string, string> = {
  exactMatch: 'bg-green-100 text-green-700',
  closeMatch: 'bg-blue-100 text-blue-700',
  relatedMatch: 'bg-purple-100 text-purple-700',
  broadMatch: 'bg-orange-100 text-orange-700',
  narrowMatch: 'bg-yellow-100 text-yellow-700',
};

const JOB_POLL_INTERVAL_MS = 3_000;

interface Props {
  datasetId: string;
  models: LogicalModel[];
}

export default function LogicalModelEditor({ datasetId, models }: Props) {
  const qc = useQueryClient();
  const [selectedModelId, setSelectedModelId] = useState<string | null>(models[0]?.id ?? null);
  const [recommendingElementId, setRecommendingElementId] = useState<string | null>(null);
  const [recommendError, setRecommendError] = useState<string | null>(null);
  const [elementRecommendError, setElementRecommendError] = useState<string | null>(null);
  // jobId returned by the 202 — non-null while a bulk job is running.
  const [bulkJobId, setBulkJobId] = useState<string | null>(null);

  useEffect(() => {
    if (selectedModelId === null && models.length > 0) setSelectedModelId(models[0].id);
  }, [models, selectedModelId]);

  // Clear job state when the user switches models.
  useEffect(() => {
    setBulkJobId(null);
  }, [selectedModelId]);

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', selectedModelId],
    queryFn: () => logicalElementApi.list(selectedModelId!),
    enabled: !!selectedModelId,
  });

  // Poll the job status endpoint while a bulk job is in flight.
  const { data: bulkJob } = useQuery({
    queryKey: ['recommendation-job', bulkJobId],
    queryFn: () => logicalElementApi.getRecommendationJob(bulkJobId!),
    enabled: !!bulkJobId,
    refetchInterval: bulkJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  // React to job status transitions.
  useEffect(() => {
    if (!bulkJob) return;
    if (bulkJob.status === 'COMPLETED') {
      setBulkJobId(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    } else if (bulkJob.status === 'FAILED') {
      setBulkJobId(null);
      setRecommendError(bulkJob.error ?? 'Bulk recommendation failed.');
    }
  }, [bulkJob, selectedModelId, qc]);

  const isJobRunning = !!bulkJobId && (bulkJob?.status === 'PENDING' || bulkJob?.status === 'RUNNING');

  const createModelMut = useMutation({
    mutationFn: () => logicalModelApi.create(datasetId, { name: 'Model v1', version: '1.0', status: 'draft' }),
    onSuccess: (m) => { qc.invalidateQueries({ queryKey: ['logical-models', datasetId] }); setSelectedModelId(m.id); },
  });

  const publishMut = useMutation({
    mutationFn: (id: string) => logicalModelApi.patchStatus(id, 'published'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['logical-models', datasetId] }),
  });

  const recommendAllMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelClassifications(modelId),
    onSuccess: (job) => {
      setRecommendError(null);
      setBulkJobId(job.jobId);
    },
    onError: () => setRecommendError('Recommendation service is unavailable. Please try again later.'),
  });

  const recommendOneMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendClassification(elementId),
    onSuccess: () => {
      setRecommendingElementId(null);
      setElementRecommendError(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    },
    onError: () => {
      setRecommendingElementId(null);
      setElementRecommendError('Recommendation service is unavailable. Please try again later.');
    },
  });

  const selectedModel = models.find(m => m.id === selectedModelId);

  if (models.length === 0) {
    return (
      <div className="text-center py-10">
        <p className="text-sm text-gray-500 mb-3">No logical model yet.</p>
        <Button onClick={() => createModelMut.mutate()} disabled={createModelMut.isPending}>
          Create Draft Model
        </Button>
      </div>
    );
  }

  const jobStatusLabel = bulkJob?.status === 'PENDING' ? 'Queued' : 'Analyzing';

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <select
            value={selectedModelId ?? ''}
            onChange={e => setSelectedModelId(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            {models.map(m => <option key={m.id} value={m.id}>{m.name} v{m.version} ({m.status})</option>)}
          </select>
          {selectedModel && (
            <Badge
              label={selectedModel.status}
              className={selectedModel.status === 'published' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'}
            />
          )}
        </div>
        <div className="flex gap-2">
          {selectedModelId && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => { setRecommendError(null); recommendAllMut.mutate(selectedModelId); }}
              disabled={recommendAllMut.isPending || isJobRunning}
            >
              {recommendAllMut.isPending
                ? 'Submitting…'
                : isJobRunning
                ? `${jobStatusLabel}…`
                : 'Recommend All'}
            </Button>
          )}
          {selectedModel?.status === 'draft' && (
            <Button size="sm" variant="secondary" onClick={() => publishMut.mutate(selectedModel.id)}>
              Publish
            </Button>
          )}
        </div>
      </div>

      {isJobRunning && (
        <div className="flex items-center gap-3 px-4 py-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
          <svg className="h-4 w-4 shrink-0 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span>
            <span className="font-medium">{jobStatusLabel}</span> — AI is classifying elements.
            Results appear row-by-row as they arrive.
          </span>
          <button
            onClick={() => setBulkJobId(null)}
            className="ml-auto text-blue-500 hover:text-blue-700"
            title="Dismiss (job continues in background)"
          >
            ✕
          </button>
        </div>
      )}

      {recommendError && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {recommendError}
          <button onClick={() => setRecommendError(null)} className="ml-auto text-red-500 hover:text-red-700">✕</button>
        </div>
      )}

      {elementRecommendError && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {elementRecommendError}
          <button onClick={() => setElementRecommendError(null)} className="ml-auto text-red-500 hover:text-red-700">✕</button>
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-8">#</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Business Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Logical Type</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Classification</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Vocabulary Concepts</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {elements.map(el => (
              <>
                <tr key={el.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-400 text-xs">{el.ordinal}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-900">{el.name}</p>
                    {el.description && <p className="text-xs text-gray-500">{el.description}</p>}
                  </td>
                  <td className="px-4 py-3">
                    {el.logicalType
                      ? <Badge label={el.logicalType} className="bg-purple-100 text-purple-700" />
                      : <span className="text-gray-400">—</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <ClassificationBadge level={el.classification} />
                      {!el.recommendedClassification && !isJobRunning && (
                        <button
                          onClick={() => {
                            setElementRecommendError(null);
                            setRecommendingElementId(el.id);
                            recommendOneMut.mutate(el.id);
                          }}
                          disabled={recommendingElementId === el.id}
                          className="text-xs text-blue-600 hover:text-blue-800 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                          {recommendingElementId === el.id ? 'Recommending…' : 'Recommend'}
                        </button>
                      )}
                      {!el.recommendedClassification && isJobRunning && (
                        <span className="text-xs text-blue-400">Pending…</span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1">
                      {el.vocabMappings?.map(m => (
                        <span
                          key={m.id}
                          title={m.conceptIri}
                          className={`px-2 py-0.5 rounded text-xs font-medium cursor-default ${MATCH_TYPE_COLORS[m.matchType]}`}
                        >
                          {m.conceptLabel || humanizeIri(m.conceptIri)}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
                {el.recommendedClassification && (
                  <ClassificationRecommendationRow
                    key={`rec-${el.id}`}
                    element={el}
                    modelId={selectedModelId!}
                  />
                )}
              </>
            ))}
            {elements.length === 0 && (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">No elements in this model</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
