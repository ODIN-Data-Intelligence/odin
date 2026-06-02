import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { datasetApi, preferredLabel, useIriTranslations } from '@datacatalog/shared';
import type { AcceptedSemanticTag, RecommendedSemanticType } from '@datacatalog/shared';
import Button from '../ui/Button';

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

  // Translate all vocabulary hint IRIs from recommendations and accepted tags
  const hintIris = [
    ...(recommendation?.types ?? []).map(r => r.vocabularyHint).filter(Boolean) as string[],
    ...(ctx?.acceptedTags ?? []).map(t => t.vocabularyIri).filter(Boolean) as string[],
  ];
  const hintTranslations = useIriTranslations(hintIris);

  return (
    <div className="space-y-5">
      {/* Header row */}
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Semantic Types</p>
        {recommending ? (
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <svg className="animate-spin h-4 w-4 text-blue-500" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
            Analysing dataset…
          </div>
        ) : canAction ? (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => { reset(); setDismissed(new Set()); getRecommendations(); }}
          >
            {recommendation ? 'Refresh AI Recommendations' : 'Get AI Recommendations'}
          </Button>
        ) : null}
      </div>

      {/* Vocab-derived semantic types */}
      {ctxLoading && <p className="text-xs text-gray-400">Loading…</p>}

      {ctx && ctx.semanticTypes.length === 0 && ctx.acceptedTags.length === 0 && !ctxLoading && (
        <p className="text-xs text-gray-400 italic">
          No semantic types yet — publish a logical model with exactMatch or closeMatch vocabulary mappings,
          or accept an AI recommendation below.
        </p>
      )}

      {ctx && ctx.semanticTypes.length > 0 && (
        <div>
          <p className="text-xs text-gray-400 mb-1.5">From vocabulary mappings</p>
          <div className="flex flex-wrap gap-1.5">
            {ctx.semanticTypes.map(t => (
              <span key={t} className="px-2 py-0.5 bg-violet-100 text-violet-700 text-xs rounded font-medium">
                {t}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Accepted AI tags */}
      {ctx && ctx.acceptedTags.length > 0 && (
        <div>
          <p className="text-xs text-gray-400 mb-1.5">Accepted AI recommendations</p>
          <div className="flex flex-wrap gap-1.5">
            {ctx.acceptedTags.map(tag => (
              <AcceptedTagBadge
                key={tag.id}
                tag={tag}
                canAction={canAction}
                onDelete={() => deleteMutation.mutate(tag.id)}
                deleting={deleteMutation.isPending}
              />
            ))}
          </div>
        </div>
      )}

      {/* Vocab coverage summary */}
      {ctx && (ctx.logicalElementNames.length > 0 || ctx.vocabConceptLabels.length > 0) && (
        <div className="text-xs text-gray-400 space-y-0.5">
          {ctx.logicalElementNames.length > 0 && (
            <p>{ctx.logicalElementNames.length} logical element{ctx.logicalElementNames.length !== 1 ? 's' : ''} across published models</p>
          )}
          {ctx.vocabConceptLabels.length > 0 && (
            <p>{ctx.vocabConceptLabels.length} vocabulary concept{ctx.vocabConceptLabels.length !== 1 ? 's' : ''} mapped</p>
          )}
        </div>
      )}

      {/* AI recommendation error */}
      {isError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
          AI service is unavailable. Please try again later.
        </div>
      )}

      {/* AI recommendations */}
      {recommendation && visibleRecs.length > 0 && (
        <div>
          <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">
            AI Recommendations
          </p>
          <div className="space-y-2">
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
          </div>
        </div>
      )}

      {recommendation && visibleRecs.length === 0 && !isError && (
        <p className="text-xs text-gray-400 italic">All recommendations reviewed.</p>
      )}
    </div>
  );
}

function AcceptedTagBadge({
  tag, canAction, onDelete, deleting,
}: {
  tag: AcceptedSemanticTag;
  canAction: boolean;
  onDelete: () => void;
  deleting: boolean;
}) {
  const hintColor = tag.vocabularyIri?.includes('edmcouncil.org/fibo')
    ? 'bg-blue-100 text-blue-700'
    : tag.vocabularyIri?.includes('schema.org')
    ? 'bg-green-100 text-green-700'
    : 'bg-amber-100 text-amber-700';

  return (
    <span
      title={tag.vocabularyIri ?? undefined}
      className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded font-medium ${hintColor}`}
    >
      {tag.type}
      {canAction && (
        <button
          onClick={onDelete}
          disabled={deleting}
          className="ml-0.5 opacity-60 hover:opacity-100"
          aria-label={`Remove ${tag.type}`}
        >
          ×
        </button>
      )}
    </span>
  );
}

function RecommendationCard({
  rec, hintLabel, canAction, accepting, onAccept, onDismiss,
}: {
  rec: RecommendedSemanticType;
  hintLabel?: string;
  canAction: boolean;
  accepting: boolean;
  onAccept: () => void;
  onDismiss: () => void;
}) {
  const hintColor = rec.vocabularyHint?.includes('edmcouncil.org/fibo')
    ? 'text-blue-600'
    : rec.vocabularyHint?.includes('schema.org')
    ? 'text-green-600'
    : 'text-gray-500';

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3 flex items-start gap-3">
      <div className="flex-1 min-w-0 space-y-1">
        <span className="px-2 py-0.5 bg-amber-100 text-amber-700 text-xs rounded font-medium">
          {rec.type}
        </span>
        <p className="text-xs text-gray-600">{rec.rationale}</p>
        {hintLabel && (
          <p className={`text-xs truncate ${hintColor}`} title={rec.vocabularyHint}>
            {hintLabel}
          </p>
        )}
      </div>
      <div className="flex items-center gap-1.5 flex-shrink-0 mt-0.5">
        {canAction ? (
          <Button size="sm" onClick={onAccept} disabled={accepting}>
            Accept
          </Button>
        ) : (
          <span className="text-xs text-gray-400 italic">Owner only</span>
        )}
        <button
          onClick={onDismiss}
          className="text-gray-300 hover:text-gray-500 p-1"
          title="Dismiss"
          aria-label="Dismiss recommendation"
        >
          ✕
        </button>
      </div>
    </div>
  );
}
