import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Tooltip from '@mui/material/Tooltip';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import EditIcon from '@mui/icons-material/Edit';
import { logicalModelApi, logicalElementApi, preferredLabel, useIriTranslations, resolveLabel, ClassificationBadge } from '@datacatalog/shared';
import type { LogicalModel, LogicalDataElement } from '@datacatalog/shared';
import ClassificationRecommendationRow from './ClassificationRecommendationRow';
import DescriptionRecommendationRow from './DescriptionRecommendationRow';
import VocabRecommendationRow from './VocabRecommendationRow';
import PiiRecommendationRow from './PiiRecommendationRow';
import ElementEditRow from './ElementEditRow';
import AgenticReviewDialog from './AgenticReviewDialog';

const MATCH_TYPE_COLORS: Record<string, 'success' | 'primary' | 'secondary' | 'warning' | 'info'> = {
  exactMatch: 'success',
  closeMatch: 'primary',
  relatedMatch: 'secondary',
  broadMatch: 'warning',
  narrowMatch: 'info',
};

const JOB_POLL_INTERVAL_MS = 3_000;

interface Props {
  datasetId: string;
  models: LogicalModel[];
  canAction: boolean;
}

function SparkButton({ onClick, disabled, loading, title, color = 'primary' }: {
  onClick: () => void; disabled?: boolean; loading?: boolean; title: string;
  color?: 'primary' | 'error' | 'secondary';
}) {
  return (
    <Tooltip title={title}>
      <span>
        <IconButton size="small" onClick={onClick} disabled={disabled || loading} color={color} sx={{ p: 0.25 }}>
          {loading ? <CircularProgress size={12} color="inherit" /> : <AutoAwesomeIcon sx={{ fontSize: 13 }} />}
        </IconButton>
      </span>
    </Tooltip>
  );
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
  const [bulkJobId, setBulkJobId] = useState<string | null>(null);
  const [bulkVocabJobId, setBulkVocabJobId] = useState<string | null>(null);
  const [vocabRecommendError, setVocabRecommendError] = useState<string | null>(null);
  const [vocabRecommendingElementId, setVocabRecommendingElementId] = useState<string | null>(null);
  const [piiRecommendingElementId, setPiiRecommendingElementId] = useState<string | null>(null);
  const [piiRecommendError, setPiiRecommendError] = useState<string | null>(null);
  const [bulkPiiJobId, setBulkPiiJobId] = useState<string | null>(null);
  const [editingElementId, setEditingElementId] = useState<string | null>(null);
  const [agenticOpen, setAgenticOpen] = useState(false);

  useEffect(() => {
    if (selectedModelId === null && models.length > 0) setSelectedModelId(models[0].id);
  }, [models, selectedModelId]);

  useEffect(() => {
    setBulkJobId(null); setBulkDescJobId(null); setBulkVocabJobId(null); setBulkPiiJobId(null);
  }, [selectedModelId]);

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', selectedModelId],
    queryFn: () => logicalElementApi.list(selectedModelId!),
    enabled: !!selectedModelId,
  });

  const unmappedIris = elements.flatMap(el => el.vocabMappings ?? []).filter(m => !m.conceptLabel && !m.conceptDefinition).map(m => m.conceptIri);
  const vocabTranslations = useIriTranslations(unmappedIris);

  const { data: bulkJob } = useQuery({
    queryKey: ['recommendation-job', bulkJobId],
    queryFn: () => logicalElementApi.getRecommendationJob(bulkJobId!),
    enabled: !!bulkJobId,
    refetchInterval: bulkJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkJob) return;
    if (bulkJob.status === 'COMPLETED') { setBulkJobId(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); }
    else if (bulkJob.status === 'FAILED') { setBulkJobId(null); setRecommendError(bulkJob.error ?? 'Bulk recommendation failed.'); }
  }, [bulkJob, selectedModelId, qc]);

  const isJobRunning = !!bulkJobId && (bulkJob?.status === 'PENDING' || bulkJob?.status === 'RUNNING');

  const { data: bulkDescJob } = useQuery({
    queryKey: ['description-recommendation-job', bulkDescJobId],
    queryFn: () => logicalElementApi.getDescriptionRecommendationJob(bulkDescJobId!),
    enabled: !!bulkDescJobId,
    refetchInterval: bulkDescJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkDescJob) return;
    if (bulkDescJob.status === 'COMPLETED') { setBulkDescJobId(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); }
    else if (bulkDescJob.status === 'FAILED') { setBulkDescJobId(null); setDescribeError(bulkDescJob.error ?? 'Bulk description recommendation failed.'); }
  }, [bulkDescJob, selectedModelId, qc]);

  const isDescJobRunning = !!bulkDescJobId && (bulkDescJob?.status === 'PENDING' || bulkDescJob?.status === 'RUNNING');

  const { data: bulkVocabJob } = useQuery({
    queryKey: ['vocab-recommendation-job', bulkVocabJobId],
    queryFn: () => logicalElementApi.getVocabRecommendationJob(bulkVocabJobId!),
    enabled: !!bulkVocabJobId,
    refetchInterval: bulkVocabJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkVocabJob) return;
    if (bulkVocabJob.status === 'COMPLETED') { setBulkVocabJobId(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); }
    else if (bulkVocabJob.status === 'FAILED') { setBulkVocabJobId(null); setVocabRecommendError(bulkVocabJob.error ?? 'Bulk vocabulary recommendation failed.'); }
  }, [bulkVocabJob, selectedModelId, qc]);

  const isVocabJobRunning = !!bulkVocabJobId && (bulkVocabJob?.status === 'PENDING' || bulkVocabJob?.status === 'RUNNING');

  const { data: bulkPiiJob } = useQuery({
    queryKey: ['pii-recommendation-job', bulkPiiJobId],
    queryFn: () => logicalElementApi.getPiiRecommendationJob(bulkPiiJobId!),
    enabled: !!bulkPiiJobId,
    refetchInterval: bulkPiiJobId ? JOB_POLL_INTERVAL_MS : false,
  });

  useEffect(() => {
    if (!bulkPiiJob) return;
    if (bulkPiiJob.status === 'COMPLETED') { setBulkPiiJobId(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); }
    else if (bulkPiiJob.status === 'FAILED') { setBulkPiiJobId(null); setPiiRecommendError(bulkPiiJob.error ?? 'Bulk PII recommendation failed.'); }
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
    onSuccess: (job) => { setRecommendError(null); setBulkJobId(job.jobId); },
    onError: () => setRecommendError('Recommendation service is unavailable. Please try again later.'),
  });

  const recommendAllDescMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelDescriptions(modelId),
    onSuccess: (job) => { setDescribeError(null); setBulkDescJobId(job.jobId); },
    onError: () => setDescribeError('Description recommendation service is unavailable. Please try again later.'),
  });

  const recommendAllVocabMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelVocabConcepts(modelId),
    onSuccess: (job) => { setVocabRecommendError(null); setBulkVocabJobId(job.jobId); },
    onError: () => setVocabRecommendError('Vocabulary recommendation service is unavailable. Please try again later.'),
  });

  const recommendOneVocabMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendVocabConcepts(elementId),
    onSuccess: () => { setVocabRecommendingElementId(null); setVocabRecommendError(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); },
    onError: () => { setVocabRecommendingElementId(null); setVocabRecommendError('Vocabulary recommendation service is unavailable. Please try again later.'); },
  });

  const describeOneMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendDescription(elementId),
    onSuccess: () => { setDescribingElementId(null); setDescribeError(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); },
    onError: () => { setDescribingElementId(null); setDescribeError('Description recommendation service is unavailable. Please try again later.'); },
  });

  const recommendOneMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendClassification(elementId),
    onSuccess: () => { setRecommendingElementId(null); setElementRecommendError(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); },
    onError: () => { setRecommendingElementId(null); setElementRecommendError('Recommendation service is unavailable. Please try again later.'); },
  });

  const recommendPiiMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.recommendPii(elementId),
    onSuccess: () => { setPiiRecommendingElementId(null); setPiiRecommendError(null); qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] }); },
    onError: () => { setPiiRecommendingElementId(null); setPiiRecommendError('PII recommendation service is unavailable. Please try again later.'); },
  });

  const recommendAllPiiMut = useMutation({
    mutationFn: (modelId: string) => logicalElementApi.recommendModelPii(modelId),
    onSuccess: (job) => { setPiiRecommendError(null); setBulkPiiJobId(job.jobId); },
    onError: () => setPiiRecommendError('PII recommendation service is unavailable. Please try again later.'),
  });

  const updateMut = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Partial<LogicalDataElement> }) =>
      logicalElementApi.update(id, body),
    onSuccess: updated => {
      qc.setQueryData<LogicalDataElement[]>(['logical-elements', selectedModelId], prev =>
        prev?.map(el => el.id === updated.id ? { ...el, ...updated } : el) ?? []
      );
      setEditingElementId(null);
    },
  });

  const selectedModel = models.find(m => m.id === selectedModelId);

  if (models.length === 0) {
    return (
      <Box sx={{ textAlign: 'center', py: 8 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>No logical model yet.</Typography>
        <Button variant="contained" onClick={() => createModelMut.mutate()} disabled={createModelMut.isPending} sx={{ textTransform: 'none' }}>
          Create Draft Model
        </Button>
      </Box>
    );
  }

  const jobStatusLabel = bulkJob?.status === 'PENDING' ? 'Queued' : 'Analyzing';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {/* Model selector + actions */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Select
            value={selectedModelId ?? ''}
            onChange={e => setSelectedModelId(e.target.value)}
            size="small"
            sx={{ fontSize: 13, minWidth: 220 }}
          >
            {models.map(m => <MenuItem key={m.id} value={m.id} sx={{ fontSize: 13 }}>{m.name} v{m.version} ({m.status})</MenuItem>)}
          </Select>
          {selectedModel && (
            <Chip label={selectedModel.status} color={selectedModel.status === 'published' ? 'success' : 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />
          )}
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {selectedModelId && canAction && (
            <Button
              variant="contained"
              size="small"
              startIcon={<AutoAwesomeIcon sx={{ fontSize: 16 }} />}
              onClick={() => setAgenticOpen(true)}
              sx={{ textTransform: 'none' }}
            >
              AI Agent Review
            </Button>
          )}
          {selectedModel?.status === 'draft' && (
            <Button variant="outlined" size="small" onClick={() => publishMut.mutate(selectedModel.id)} disabled={publishMut.isPending} sx={{ textTransform: 'none' }}>
              Publish
            </Button>
          )}
        </Box>
      </Box>

      {/* Job running banners */}
      {isJobRunning && (
        <Alert severity="info" icon={<CircularProgress size={16} color="inherit" />} onClose={() => setBulkJobId(null)}>
          <strong>{jobStatusLabel}</strong> — AI is classifying elements. Results appear row-by-row as they arrive.
        </Alert>
      )}
      {recommendError && <Alert severity="error" onClose={() => setRecommendError(null)}>{recommendError}</Alert>}
      {elementRecommendError && <Alert severity="error" onClose={() => setElementRecommendError(null)}>{elementRecommendError}</Alert>}
      {describeError && <Alert severity="error" onClose={() => setDescribeError(null)}>{describeError}</Alert>}
      {isDescJobRunning && (
        <Alert severity="info" icon={<CircularProgress size={16} color="inherit" />} onClose={() => setBulkDescJobId(null)}>
          <strong>{bulkDescJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}</strong> — AI is suggesting descriptions. Results appear row-by-row as they arrive.
        </Alert>
      )}
      {isVocabJobRunning && (
        <Alert severity="info" icon={<CircularProgress size={16} color="inherit" />} onClose={() => setBulkVocabJobId(null)} sx={{ '& .MuiAlert-icon': { color: 'secondary.main' } }}>
          <strong>{bulkVocabJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}</strong> — AI is suggesting vocabulary concepts. Results appear row-by-row as they arrive.
        </Alert>
      )}
      {vocabRecommendError && <Alert severity="error" onClose={() => setVocabRecommendError(null)}>{vocabRecommendError}</Alert>}
      {isPiiJobRunning && (
        <Alert severity="warning" icon={<CircularProgress size={16} color="inherit" />} onClose={() => setBulkPiiJobId(null)}>
          <strong>{bulkPiiJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}</strong> — AI is assessing PII and direct-identifier indicators. Results appear row-by-row as they arrive.
        </Alert>
      )}
      {piiRecommendError && <Alert severity="error" onClose={() => setPiiRecommendError(null)}>{piiRecommendError}</Alert>}

      {/* Elements table */}
      <Paper variant="outlined" sx={{ overflow: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: 'grey.50' }}>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase', width: 32 }}>#</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Business Name</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  Description
                  {selectedModelId && canAction && (
                    <SparkButton
                      onClick={() => { setDescribeError(null); recommendAllDescMut.mutate(selectedModelId); }}
                      disabled={recommendAllDescMut.isPending || isDescJobRunning}
                      loading={recommendAllDescMut.isPending || isDescJobRunning}
                      title={isDescJobRunning ? `${bulkDescJob?.status === 'PENDING' ? 'Queued' : 'Analyzing'}…` : 'AI-suggest descriptions for all elements'}
                    />
                  )}
                </Box>
              </TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>Logical Type</TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  Classification
                  {selectedModelId && canAction && (
                    <SparkButton
                      onClick={() => { setRecommendError(null); recommendAllMut.mutate(selectedModelId); }}
                      disabled={recommendAllMut.isPending || isJobRunning}
                      loading={recommendAllMut.isPending || isJobRunning}
                      title={isJobRunning ? `${jobStatusLabel}…` : 'AI-recommend classification for all elements'}
                    />
                  )}
                </Box>
              </TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  Vocabulary Concepts
                  {selectedModelId && canAction && (
                    <SparkButton
                      onClick={() => { setVocabRecommendError(null); recommendAllVocabMut.mutate(selectedModelId); }}
                      disabled={recommendAllVocabMut.isPending || isVocabJobRunning}
                      loading={recommendAllVocabMut.isPending || isVocabJobRunning}
                      title={isVocabJobRunning ? 'Analyzing…' : 'AI-suggest vocabulary concepts for all elements'}
                    />
                  )}
                </Box>
              </TableCell>
              <TableCell sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  PII / ID
                  {selectedModelId && canAction && (
                    <SparkButton
                      onClick={() => { setPiiRecommendError(null); recommendAllPiiMut.mutate(selectedModelId); }}
                      disabled={recommendAllPiiMut.isPending || isPiiJobRunning}
                      loading={recommendAllPiiMut.isPending || isPiiJobRunning}
                      title={isPiiJobRunning ? 'Analyzing…' : 'AI-recommend PII and direct-identifier indicators for all elements'}
                      color="error"
                    />
                  )}
                </Box>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {elements.map(el => (
              <>
                {editingElementId === el.id ? (
                  <ElementEditRow
                    key={`edit-${el.id}`}
                    element={el}
                    onSave={(id, updates) => updateMut.mutate({ id, body: updates })}
                    onCancel={() => setEditingElementId(null)}
                    saving={updateMut.isPending}
                  />
                ) : (
                <>
                <TableRow key={el.id} hover sx={{ verticalAlign: 'top' }}>
                  <TableCell sx={{ position: 'relative' }}>
                    <Typography variant="caption" color="text.disabled">{el.ordinal}</Typography>
                    {canAction && editingElementId === null && (
                      <Tooltip title="Edit this element">
                        <IconButton
                          size="small"
                          onClick={() => setEditingElementId(el.id)}
                          sx={{
                            position: 'absolute', top: 2, left: 2,
                            opacity: 0, p: 0.25, color: 'text.secondary',
                            '.MuiTableRow-root:hover &': { opacity: 1 },
                          }}
                        >
                          <EditIcon sx={{ fontSize: 12 }} />
                        </IconButton>
                      </Tooltip>
                    )}
                  </TableCell>
                  <TableCell><Typography variant="body2" fontWeight={600}>{el.label ?? el.name}</Typography></TableCell>
                  <TableCell sx={{ maxWidth: 200 }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.5 }}>{el.description ?? ''}</Typography>
                      {!el.recommendedDescription && canAction && (
                        <SparkButton
                          onClick={() => { setDescribeError(null); setDescribingElementId(el.id); describeOneMut.mutate(el.id); }}
                          disabled={describingElementId === el.id}
                          loading={describingElementId === el.id}
                          title="AI-suggest description from vocabulary concepts"
                        />
                      )}
                    </Box>
                  </TableCell>
                  <TableCell>
                    {el.logicalType
                      ? <Chip label={el.logicalType} color="secondary" size="small" sx={{ height: 18, fontSize: 11 }} />
                      : <Typography variant="caption" color="text.disabled">—</Typography>}
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                      <ClassificationBadge level={el.classification} />
                      {!el.recommendedClassification && !isJobRunning && canAction && (
                        <SparkButton
                          onClick={() => { setElementRecommendError(null); setRecommendingElementId(el.id); recommendOneMut.mutate(el.id); }}
                          disabled={recommendingElementId === el.id}
                          loading={recommendingElementId === el.id}
                          title="AI-recommend classification for this element"
                        />
                      )}
                      {!el.recommendedClassification && isJobRunning && <CircularProgress size={12} sx={{ color: 'primary.light' }} />}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
                      {el.vocabMappings?.map(m => (
                        <Chip
                          key={m.id}
                          label={resolveLabel(vocabTranslations, m.conceptIri, m.conceptLabel, m.conceptDefinition)}
                          color={MATCH_TYPE_COLORS[m.matchType] ?? 'default'}
                          size="small"
                          title={m.conceptIri}
                          sx={{ height: 18, fontSize: 11 }}
                        />
                      ))}
                      {!el.recommendedVocabMappings && !isVocabJobRunning && canAction && (
                        <SparkButton
                          onClick={() => { setVocabRecommendError(null); setVocabRecommendingElementId(el.id); recommendOneVocabMut.mutate(el.id); }}
                          disabled={vocabRecommendingElementId === el.id}
                          loading={vocabRecommendingElementId === el.id}
                          title="AI-suggest vocabulary concepts for this element"
                        />
                      )}
                      {!el.recommendedVocabMappings && isVocabJobRunning && <CircularProgress size={12} sx={{ color: 'secondary.light' }} />}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5 }}>
                      {el.isPersonalInformation && (
                        <Chip label="PII" size="small" color="error" sx={{ height: 18, fontSize: 11, fontWeight: 700 }} title="Personal Information" />
                      )}
                      {el.isDirectIdentifier && (
                        <Chip label="ID" size="small" color="warning" sx={{ height: 18, fontSize: 11, fontWeight: 700 }} title="Direct Identifier" />
                      )}
                      {el.recommendedIsPersonalInformation != null && (
                        <Box sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'warning.main' }} title={el.piiRecommendationReasoning ?? 'Pending AI recommendation'} />
                      )}
                      {el.recommendedIsPersonalInformation == null && !isPiiJobRunning && canAction && (
                        <SparkButton
                          onClick={() => { setPiiRecommendError(null); setPiiRecommendingElementId(el.id); recommendPiiMut.mutate(el.id); }}
                          disabled={piiRecommendingElementId === el.id}
                          loading={piiRecommendingElementId === el.id}
                          title="AI-recommend PII indicators for this element"
                          color="error"
                        />
                      )}
                      {el.recommendedIsPersonalInformation == null && isPiiJobRunning && <CircularProgress size={12} sx={{ color: 'error.light' }} />}
                    </Box>
                  </TableCell>
                </TableRow>

                {el.recommendedVocabMappings && el.recommendedVocabMappings.length > 0 && (
                  <VocabRecommendationRow key={`vocab-rec-${el.id}`} element={el} modelId={selectedModelId!} canAction={canAction} />
                )}
                {el.recommendedDescription && (
                  <DescriptionRecommendationRow key={`desc-rec-${el.id}`} element={el} modelId={selectedModelId!} canAction={canAction} />
                )}
                {el.recommendedClassification && (
                  <ClassificationRecommendationRow key={`rec-${el.id}`} element={el} modelId={selectedModelId!} canAction={canAction} />
                )}
                {el.recommendedIsPersonalInformation != null && (
                  <PiiRecommendationRow key={`pii-rec-${el.id}`} element={el} modelId={selectedModelId!} canAction={canAction} />
                )}
                </>
                )}
              </>
            ))}
            {elements.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} sx={{ textAlign: 'center', py: 6, color: 'text.disabled' }}>No elements in this model</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>

      <AgenticReviewDialog
        open={agenticOpen}
        datasetId={datasetId}
        modelId={selectedModelId}
        onClose={() => setAgenticOpen(false)}
        onApplied={() => qc.invalidateQueries({ queryKey: ['logical-elements', selectedModelId] })}
      />
    </Box>
  );
}
