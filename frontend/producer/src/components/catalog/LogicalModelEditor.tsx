import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalModelApi, logicalElementApi, preferredLabel, useIriTranslations, resolveLabel } from '@datacatalog/shared';
import type { LogicalModel } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';
import { ClassificationBadge } from '@datacatalog/shared';
import ClassificationRecommendationRow from './ClassificationRecommendationRow';
import DescriptionRecommendationRow from './DescriptionRecommendationRow';
import VocabRecommendationRow from './VocabRecommendationRow';
import PiiRecommendationRow from './PiiRecommendationRow';

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
  canAction: boolean;
}

export default function LogicalModelEditor({ datasetId, models, canAction }: Props) {
  const qc = useQueryClient();
  const [selectedModelId, setSelectedModelId] = useState<string | null>(models[0]?.id ?? null);
  const [recommendingElementId, setRecommendingElementId] = useState<string | null>(null);
  const [recommendError, setRecommendError] = useState<string | null>(null);
  const [elementRecommendError, setElementRecommendError] = useState<string | null>(null);
  const [describingElementId, setDescribingElementId] = useState<string | null>(null);
  const [describeError, setDescribeError] = useState<string | null>(null);
  const [bulkDescJobId, setBulkDescJobId] = useState<string | null>(null);
  // jobId returned by the 202 — non-null while a bulk job is running.
  const [bulkJobId, setBulkJobId] = useState<string | null>(null);
  const [bulkVocabJobId, setBulkVocabJobId] = useState<string | null>(null);
  const [vocabRecommendError, setVocabRecommendError] = useState<string | null>(null);
  const [vocabRecommendingElementId, setVocabRecommendingElementId] = useState<string | null>(null);
  const [piiRecommendingElementId, setPiiRecommendingElementId] = useState<string | null>(null);
  const [piiRecommendError, setPiiRecommendError] = useState<string | null>(null);
  const [bulkPiiJobId, setBulkPiiJobId] = useState<string | null>(null);

  useEffect(() => {
    if (selectedModelId === null && models.length > 0) setSelectedModelId(models[0].id);
  }, [models, selectedModelId]);

  // Clear job state when the user switches models.
  useEffect(() => {
    setBulkJobId(null);
    setBulkDescJobId(null);
    setBulkVocabJobId(null);
    setBulkPiiJobId(null);
  }, [selectedModelId]);

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', selectedModelId],
    queryFn: () => logicalElementApi.list(selectedModelId!),
    enabled: !!selectedModelId,
  });

  // Translate IRIs that have no stored conceptLabel or conceptDefinition
  const unmappedIris = elements
    .flatMap(el => el.vocabMappings ?? [])
    .filter(m => !m.conceptLabel && !m.conceptDefinition)
    .map(m => m.conceptIri);
  const vocabTranslations = useIriTranslations(unmappedIris);

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

  // Poll bulk description job while in flight.
  const { data: bulkDescJob } = useQuery({
    queryKey: ['description-recommendation-job', bulkDescJobId],
    queryFn: () => logicalElementApi.getDescriptionRecommendationJob(bulkDescJobId!),
    enabled: !!bulkDescJobId,
    refetchInterval: bulkDescJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkDescJob) return;
    if (bulkDescJob.status === 'COMPLETED') {
      setBulkDescJobId(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    } else if (bulkDescJob.status === 'FAILED') {
      setBulkDescJobId(null);
      setDescribeError(bulkDescJob.error ?? 'Bulk description recommendation failed.');
    }
  }, [bulkDescJob, selectedModelId, qc]);

  const isDescJobRunning = !!bulkDescJobId && (bulkDescJob?.status === 'PENDING' || bulkDescJob?.status === 'RUNNING');

  // Poll bulk vocab job while in flight.
  const { data: bulkVocabJob } = useQuery({
    queryKey: ['vocab-recommendation-job', bulkVocabJobId],
    queryFn: () => logicalElementApi.getVocabRecommendationJob(bulkVocabJobId!),
    enabled: !!bulkVocabJobId,
    refetchInterval: bulkVocabJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkVocabJob) return;
    if (bulkVocabJob.status === 'COMPLETED') {
      setBulkVocabJobId(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    } else if (bulkVocabJob.status === 'FAILED') {
      setBulkVocabJobId(null);
      setVocabRecommendError(bulkVocabJob.error ?? 'Bulk vocabulary recommendation failed.');
    }
  }, [bulkVocabJob, selectedModelId, qc]);

  const isVocabJobRunning = !!bulkVocabJobId && (bulkVocabJob?.status === 'PENDING' || bulkVocabJob?.status === 'RUNNING');

  // Poll bulk PII job while in flight.
  const { data: bulkPiiJob } = useQuery({
    queryKey: ['pii-recommendation-job', bulkPiiJobId],
    queryFn: () => logicalElementApi.getPiiRecommendationJob(bulkPiiJobId!),
    enabled: !!bulkPiiJobId,
    refetchInterval: bulkPiiJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkPiiJob) return;
    if (bulkPiiJob.status === 'COMPLETED') {
      setBulkPiiJobId(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    } else if (bulkPiiJob.status === 'FAILED') {
      setBulkPiiJobId(null);
      setPiiRecommendError(bulkPiiJob.error ?? 'Bulk PII recommendation failed.');
    }
  }, [bulkPiiJob, selectedModelId, qc]);

  const isPiiJobRunning = !!bulkPiiJobId && (bulkPiiJob?.status === 'PENDING' || bulkPiiJob?.status === 'RUNNING');

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

  const recommendAllDescMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelDescriptions(modelId),
    onSuccess: (job) => {
      setDescribeError(null);
      setBulkDescJobId(job.jobId);
    },
    onError: () => setDescribeError('Description recommendation service is unavailable. Please try again later.'),
  });

  const recommendAllVocabMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelVocabConcepts(modelId),
    onSuccess: (job) => {
      setVocabRecommendError(null);
      setBulkVocabJobId(job.jobId);
    },
    onError: () => setVocabRecommendError('Vocabulary recommendation service is unavailable. Please try again later.'),
  });

  const recommendOneVocabMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendVocabConcepts(elementId),
    onSuccess: () => {
      setVocabRecommendingElementId(null);
      setVocabRecommendError(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    },
    onError: () => {
      setVocabRecommendingElementId(null);
      setVocabRecommendError('Vocabulary recommendation service is unavailable. Please try again later.');
    },
  });

  const describeOneMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendDescription(elementId),
    onSuccess: () => {
      setDescribingElementId(null);
      setDescribeError(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    },
    onError: () => {
      setDescribingElementId(null);
      setDescribeError('Description recommendation service is unavailable. Please try again later.');
    },
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

  const recommendPiiMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendPii(elementId),
    onSuccess: () => {
      setPiiRecommendingElementId(null);
      setPiiRecommendError(null);
      qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] });
    },
    onError: () => {
      setPiiRecommendingElementId(null);
      setPiiRecommendError('PII recommendation service is unavailable. Please try again later.');
    },
  });

  const recommendAllPiiMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelPii(modelId),
    onSuccess: (job) => {
      setPiiRecommendError(null);
      setBulkPiiJobId(job.jobId);
    },
    onError: () => setPiiRecommendError('PII recommendation service is unavailable. Please try again later.'),
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

      {describeError && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {describeError}
          <button onClick={() => setDescribeError(null)} className="ml-auto text-red-500 hover:text-red-700">✕</button>
        </div>
      )}

      {isVocabJobRunning && (
        <div className="flex items-center gap-3 px-4 py-3 bg-violet-50 border border-violet-200 rounded-lg text-sm text-violet-700">
          <svg className="h-4 w-4 shrink-0 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span>
            <span className="font-medium">{bulkVocabJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}</span> — AI is suggesting vocabulary concepts.
            Results appear row-by-row as they arrive.
          </span>
          <button onClick={() => setBulkVocabJobId(null)} className="ml-auto text-violet-500 hover:text-violet-700" title="Dismiss">✕</button>
        </div>
      )}

      {vocabRecommendError && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {vocabRecommendError}
          <button onClick={() => setVocabRecommendError(null)} className="ml-auto text-red-500 hover:text-red-700">✕</button>
        </div>
      )}

      {isPiiJobRunning && (
        <div className="flex items-center gap-3 px-4 py-3 bg-rose-50 border border-rose-200 rounded-lg text-sm text-rose-700">
          <svg className="h-4 w-4 shrink-0 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <span>
            <span className="font-medium">{bulkPiiJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}</span> — AI is assessing PII and direct-identifier indicators.
            Results appear row-by-row as they arrive.
          </span>
          <button onClick={() => setBulkPiiJobId(null)} className="ml-auto text-rose-500 hover:text-rose-700" title="Dismiss">✕</button>
        </div>
      )}

      {piiRecommendError && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {piiRecommendError}
          <button onClick={() => setPiiRecommendError(null)} className="ml-auto text-red-500 hover:text-red-700">✕</button>
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-8">#</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Business Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                <div className="flex items-center gap-1.5">
                  Description
                  {selectedModelId && canAction && (
                    <button
                      onClick={() => { setDescribeError(null); recommendAllDescMut.mutate(selectedModelId); }}
                      disabled={recommendAllDescMut.isPending || isDescJobRunning}
                      title={isDescJobRunning
                        ? `${bulkDescJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}…`
                        : 'AI-suggest descriptions for all elements from vocabulary concepts'}
                      className="text-blue-500 hover:text-blue-700 disabled:opacity-40 disabled:cursor-not-allowed normal-case font-normal"
                    >
                      {recommendAllDescMut.isPending || isDescJobRunning
                        ? (
                          <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                          </svg>
                        ) : (
                          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                          </svg>
                        )}
                    </button>
                  )}
                </div>
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Logical Type</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                <div className="flex items-center gap-1.5">
                  Classification
                  {selectedModelId && canAction && (
                    <button
                      onClick={() => { setRecommendError(null); recommendAllMut.mutate(selectedModelId); }}
                      disabled={recommendAllMut.isPending || isJobRunning}
                      title={isJobRunning ? `${jobStatusLabel}…` : 'AI-recommend classification for all elements'}
                      className="text-blue-500 hover:text-blue-700 disabled:opacity-40 disabled:cursor-not-allowed normal-case font-normal"
                    >
                      {recommendAllMut.isPending || isJobRunning
                        ? (
                          <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                          </svg>
                        ) : (
                          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                          </svg>
                        )}
                    </button>
                  )}
                </div>
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                <div className="flex items-center gap-1.5">
                  Vocabulary Concepts
                  {selectedModelId && canAction && (
                    <button
                      onClick={() => { setVocabRecommendError(null); recommendAllVocabMut.mutate(selectedModelId); }}
                      disabled={recommendAllVocabMut.isPending || isVocabJobRunning}
                      title={isVocabJobRunning ? 'Analyzing…' : 'AI-suggest vocabulary concepts for all elements'}
                      className="text-blue-500 hover:text-blue-700 disabled:opacity-40 disabled:cursor-not-allowed normal-case font-normal"
                    >
                      {recommendAllVocabMut.isPending || isVocabJobRunning
                        ? (
                          <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                          </svg>
                        ) : (
                          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                          </svg>
                        )}
                    </button>
                  )}
                </div>
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                <div className="flex items-center gap-1.5">
                  PII / ID
                  {selectedModelId && canAction && (
                    <button
                      onClick={() => { setPiiRecommendError(null); recommendAllPiiMut.mutate(selectedModelId); }}
                      disabled={recommendAllPiiMut.isPending || isPiiJobRunning}
                      title={isPiiJobRunning ? 'Analyzing…' : 'AI-recommend PII and direct-identifier indicators for all elements'}
                      className="text-rose-400 hover:text-rose-600 disabled:opacity-40 disabled:cursor-not-allowed normal-case font-normal"
                    >
                      {recommendAllPiiMut.isPending || isPiiJobRunning
                        ? (
                          <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                          </svg>
                        ) : (
                          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                          </svg>
                        )}
                    </button>
                  )}
                </div>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {elements.map(el => (
              <>
                <tr key={el.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-gray-400 text-xs">{el.ordinal}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-900">{el.label ?? el.name}</p>
                  </td>
                  <td className="px-4 py-3 max-w-xs">
                    <div className="flex items-start gap-1.5">
                      <span className="text-xs text-gray-600 leading-relaxed">{el.description ?? ''}</span>
                      {!el.recommendedDescription && canAction && (
                        <button
                          onClick={() => {
                            setDescribeError(null);
                            setDescribingElementId(el.id);
                            describeOneMut.mutate(el.id);
                          }}
                          disabled={describingElementId === el.id}
                          title="AI-suggest description from vocabulary concepts"
                          className="shrink-0 text-blue-400 hover:text-blue-600 disabled:opacity-40 disabled:cursor-not-allowed mt-0.5"
                        >
                          {describingElementId === el.id
                            ? (
                              <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                              </svg>
                            ) : (
                              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                              </svg>
                            )}
                        </button>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {el.logicalType
                      ? <Badge label={el.logicalType} className="bg-purple-100 text-purple-700" />
                      : <span className="text-gray-400">—</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <ClassificationBadge level={el.classification} />
                      {!el.recommendedClassification && !isJobRunning && canAction && (
                        <button
                          onClick={() => {
                            setElementRecommendError(null);
                            setRecommendingElementId(el.id);
                            recommendOneMut.mutate(el.id);
                          }}
                          disabled={recommendingElementId === el.id}
                          title="AI-recommend classification for this element"
                          className="text-blue-400 hover:text-blue-600 disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {recommendingElementId === el.id
                            ? (
                              <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                              </svg>
                            ) : (
                              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                              </svg>
                            )}
                        </button>
                      )}
                      {!el.recommendedClassification && isJobRunning && (
                        <svg className="h-3.5 w-3.5 text-blue-300 animate-pulse" viewBox="0 0 24 24" fill="currentColor">
                          <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
                        </svg>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1 items-center">
                      {el.vocabMappings?.map(m => (
                        <span
                          key={m.id}
                          title={m.conceptIri}
                          className={`px-2 py-0.5 rounded text-xs font-medium cursor-default ${MATCH_TYPE_COLORS[m.matchType]}`}
                        >
                          {resolveLabel(vocabTranslations, m.conceptIri, m.conceptLabel, m.conceptDefinition)}
                        </span>
                      ))}
                      {!el.recommendedVocabMappings && !isVocabJobRunning && canAction && (
                        <button
                          onClick={() => {
                            setVocabRecommendError(null);
                            setVocabRecommendingElementId(el.id);
                            recommendOneVocabMut.mutate(el.id);
                          }}
                          disabled={vocabRecommendingElementId === el.id}
                          title="AI-suggest vocabulary concepts for this element"
                          className="text-blue-400 hover:text-blue-600 disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {vocabRecommendingElementId === el.id
                            ? (
                              <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                              </svg>
                            ) : (
                              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                              </svg>
                            )}
                        </button>
                      )}
                      {!el.recommendedVocabMappings && isVocabJobRunning && (
                        <svg className="h-3.5 w-3.5 text-violet-300 animate-pulse" viewBox="0 0 24 24" fill="currentColor">
                          <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
                        </svg>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap items-center gap-1">
                      {el.isPersonalInformation && (
                        <span
                          title="Personal Information"
                          className="px-2 py-0.5 rounded text-xs font-semibold bg-rose-600 text-white"
                        >
                          PII
                        </span>
                      )}
                      {el.isDirectIdentifier && (
                        <span
                          title="Direct Identifier"
                          className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-500 text-white"
                        >
                          ID
                        </span>
                      )}
                      {el.recommendedIsPersonalInformation != null && (
                        <span title={el.piiRecommendationReasoning ?? 'Pending AI recommendation'} className="h-2 w-2 rounded-full bg-amber-400 inline-block" />
                      )}
                      {el.recommendedIsPersonalInformation == null && !isPiiJobRunning && canAction && (
                        <button
                          onClick={() => {
                            setPiiRecommendError(null);
                            setPiiRecommendingElementId(el.id);
                            recommendPiiMut.mutate(el.id);
                          }}
                          disabled={piiRecommendingElementId === el.id}
                          title="AI-recommend PII indicators for this element"
                          className="text-rose-400 hover:text-rose-600 disabled:opacity-40 disabled:cursor-not-allowed"
                        >
                          {piiRecommendingElementId === el.id
                            ? (
                              <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                              </svg>
                            ) : (
                              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
                              </svg>
                            )}
                        </button>
                      )}
                      {el.recommendedIsPersonalInformation == null && isPiiJobRunning && (
                        <svg className="h-3.5 w-3.5 text-rose-300 animate-pulse" viewBox="0 0 24 24" fill="currentColor">
                          <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
                        </svg>
                      )}
                    </div>
                  </td>
                </tr>
                {el.recommendedVocabMappings && el.recommendedVocabMappings.length > 0 && (
                  <VocabRecommendationRow
                    key={`vocab-rec-${el.id}`}
                    element={el}
                    modelId={selectedModelId!}
                    canAction={canAction}
                  />
                )}
                {el.recommendedDescription && (
                  <DescriptionRecommendationRow
                    key={`desc-rec-${el.id}`}
                    element={el}
                    modelId={selectedModelId!}
                    canAction={canAction}
                  />
                )}
                {el.recommendedClassification && (
                  <ClassificationRecommendationRow
                    key={`rec-${el.id}`}
                    element={el}
                    modelId={selectedModelId!}
                    canAction={canAction}
                  />
                )}
                {el.recommendedIsPersonalInformation != null && (
                  <PiiRecommendationRow
                    key={`pii-rec-${el.id}`}
                    element={el}
                    modelId={selectedModelId!}
                    canAction={canAction}
                  />
                )}
              </>
            ))}
            {elements.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400">No elements in this model</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
