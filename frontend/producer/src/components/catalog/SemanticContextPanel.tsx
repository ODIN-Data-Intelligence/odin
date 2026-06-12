import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CloseIcon from '@mui/icons-material/Close';
import { datasetApi, preferredLabel, useIriTranslations } from '@datacatalog/shared';
import type { AcceptedSemanticTag, RecommendedSemanticType } from '@datacatalog/shared';

interface Props {
  datasetId: string;
  canAction: boolean;
}

export default function SemanticContextPanel({ datasetId, canAction }: Props) {
  const qc = useQueryClient();
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  const { data: ctx, isLoading: ctxLoading } = useQuery({
    queryKey: ['semantic-context', datasetId],
    queryFn: () => datasetApi.getSemanticContext(datasetId),
  });

  const { mutate: getRecommendations, data: recommendation, isPending: recommending, isError, reset } = useMutation({
    mutationFn: () => datasetApi.recommendSemanticContext(datasetId),
  });

  const acceptMutation = useMutation({
    mutationFn: (rec: RecommendedSemanticType) =>
      datasetApi.acceptSemanticTag(datasetId, { type: rec.type, vocabularyIri: rec.vocabularyHint }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['semantic-context', datasetId] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (tagId: string) => datasetApi.deleteSemanticTag(datasetId, tagId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['semantic-context', datasetId] }),
  });

  function dismiss(type: string) {
    setDismissed(prev => new Set(prev).add(type));
  }

  const acceptedTypes = new Set(ctx?.acceptedTags.map(t => t.type) ?? []);
  const visibleRecs = recommendation?.types.filter(
    r => !dismissed.has(r.type) && !acceptedTypes.has(r.type)
  ) ?? [];

  const hintIris = [
    ...(recommendation?.types ?? []).map(r => r.vocabularyHint).filter(Boolean) as string[],
    ...(ctx?.acceptedTags ?? []).map(t => t.vocabularyIri).filter(Boolean) as string[],
  ];
  const hintTranslations = useIriTranslations(hintIris);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.08em' }}>
          Semantic Types
        </Typography>
        {recommending ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={14} color="primary" />
            <Typography variant="caption" color="text.secondary">Analysing dataset…</Typography>
          </Box>
        ) : canAction ? (
          <Button
            size="small"
            variant="outlined"
            startIcon={<AutoAwesomeIcon sx={{ fontSize: '14px !important' }} />}
            onClick={() => { reset(); setDismissed(new Set()); getRecommendations(); }}
            sx={{ textTransform: 'none', fontSize: 11 }}
          >
            {recommendation ? 'Refresh AI Recommendations' : 'Get AI Recommendations'}
          </Button>
        ) : null}
      </Box>

      {ctxLoading && <Typography variant="caption" color="text.disabled">Loading…</Typography>}

      {ctx && ctx.semanticTypes.length === 0 && ctx.acceptedTags.length === 0 && !ctxLoading && (
        <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No semantic types yet — publish a logical model with exactMatch or closeMatch vocabulary mappings,
          or accept an AI recommendation below.
        </Typography>
      )}

      {ctx && ctx.semanticTypes.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mb: 0.75 }}>From vocabulary mappings</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
            {ctx.semanticTypes.map(t => (
              <Chip key={t} label={t} size="small" color="secondary" sx={{ height: 20, fontSize: 11 }} />
            ))}
          </Box>
        </Box>
      )}

      {ctx && ctx.acceptedTags.length > 0 && (
        <Box>
          <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mb: 0.75 }}>Accepted AI recommendations</Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
            {ctx.acceptedTags.map(tag => (
              <AcceptedTagBadge
                key={tag.id}
                tag={tag}
                canAction={canAction}
                onDelete={() => deleteMutation.mutate(tag.id)}
                deleting={deleteMutation.isPending}
              />
            ))}
          </Box>
        </Box>
      )}

      {ctx && (ctx.logicalElementNames.length > 0 || ctx.vocabConceptLabels.length > 0) && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
          {ctx.logicalElementNames.length > 0 && (
            <Typography variant="caption" color="text.disabled">
              {ctx.logicalElementNames.length} logical element{ctx.logicalElementNames.length !== 1 ? 's' : ''} across published models
            </Typography>
          )}
          {ctx.vocabConceptLabels.length > 0 && (
            <Typography variant="caption" color="text.disabled">
              {ctx.vocabConceptLabels.length} vocabulary concept{ctx.vocabConceptLabels.length !== 1 ? 's' : ''} mapped
            </Typography>
          )}
        </Box>
      )}

      {isError && <Alert severity="error">AI service is unavailable. Please try again later.</Alert>}

      {recommendation && visibleRecs.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: '0.08em', display: 'block', mb: 1 }}>
            AI Recommendations
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {visibleRecs.map(rec => (
              <RecommendationCard
                key={rec.type}
                rec={rec}
                hintLabel={rec.vocabularyHint ? (hintTranslations[rec.vocabularyHint] ?? preferredLabel(rec.vocabularyHint)) : undefined}
                canAction={canAction}
                accepting={acceptMutation.isPending}
                onAccept={() => acceptMutation.mutate(rec)}
                onDismiss={() => dismiss(rec.type)}
              />
            ))}
          </Box>
        </Box>
      )}

      {recommendation && visibleRecs.length === 0 && !isError && (
        <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>All recommendations reviewed.</Typography>
      )}
    </Box>
  );
}

function AcceptedTagBadge({ tag, canAction, onDelete, deleting }: {
  tag: AcceptedSemanticTag; canAction: boolean; onDelete: () => void; deleting: boolean;
}) {
  const color = tag.vocabularyIri?.includes('edmcouncil.org/fibo')
    ? 'primary' as const
    : tag.vocabularyIri?.includes('schema.org')
    ? 'success' as const
    : 'warning' as const;

  return (
    <Tooltip title={tag.vocabularyIri ?? ''}>
      <Chip
        label={tag.type}
        size="small"
        color={color}
        onDelete={canAction ? onDelete : undefined}
        disabled={deleting}
        sx={{ height: 20, fontSize: 11 }}
      />
    </Tooltip>
  );
}

function RecommendationCard({ rec, hintLabel, canAction, accepting, onAccept, onDismiss }: {
  rec: RecommendedSemanticType; hintLabel?: string;
  canAction: boolean; accepting: boolean;
  onAccept: () => void; onDismiss: () => void;
}) {
  const hintColor = rec.vocabularyHint?.includes('edmcouncil.org/fibo')
    ? 'primary.main'
    : rec.vocabularyHint?.includes('schema.org')
    ? 'success.main'
    : 'text.secondary';

  return (
    <Paper variant="outlined" sx={{ p: 1.5, display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Chip label={rec.type} size="small" color="warning" sx={{ mb: 0.5, height: 20, fontSize: 11 }} />
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{rec.rationale}</Typography>
        {hintLabel && (
          <Typography variant="caption" color={hintColor} sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={rec.vocabularyHint}>
            {hintLabel}
          </Typography>
        )}
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexShrink: 0, mt: 0.25 }}>
        {canAction ? (
          <Button size="small" variant="contained" onClick={onAccept} disabled={accepting} sx={{ textTransform: 'none', fontSize: 11 }}>
            Accept
          </Button>
        ) : (
          <Typography variant="caption" color="text.disabled" sx={{ fontStyle: 'italic' }}>Owner only</Typography>
        )}
        <Tooltip title="Dismiss">
          <IconButton size="small" onClick={onDismiss}><CloseIcon sx={{ fontSize: 14 }} /></IconButton>
        </Tooltip>
      </Box>
    </Paper>
  );
}
