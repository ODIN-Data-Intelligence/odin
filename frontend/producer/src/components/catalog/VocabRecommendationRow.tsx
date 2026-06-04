import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalElementApi, iriFragment } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

const MATCH_TYPE_COLORS: Record<string, string> = {
  exactMatch:   'bg-green-100 text-green-700 border-green-200',
  closeMatch:   'bg-blue-100 text-blue-700 border-blue-200',
  relatedMatch: 'bg-purple-100 text-purple-700 border-purple-200',
  broadMatch:   'bg-orange-100 text-orange-700 border-orange-200',
  narrowMatch:  'bg-yellow-100 text-yellow-700 border-yellow-200',
};

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function VocabRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['logical-elements', modelId] });

  const accept = useMutation({
    mutationFn: () => logicalElementApi.acceptVocabConcepts(element.id),
    onSuccess: invalidate,
  });

  const reject = useMutation({
    mutationFn: () => logicalElementApi.rejectVocabConcepts(element.id),
    onSuccess: invalidate,
  });

  const isPending = accept.isPending || reject.isPending;
  const recommendations = element.recommendedVocabMappings ?? [];

  return (
    <tr className="bg-violet-50 border-t border-violet-200">
      <td colSpan={6} className="px-4 py-3">
        <div className="flex items-start gap-4">
          <div className="flex-1 min-w-0 space-y-2">
            <p className="text-sm font-medium text-violet-900">AI Vocabulary Suggestions</p>
            <div className="flex flex-wrap gap-2">
              {recommendations.map((rec, i) => {
                const colorClass = MATCH_TYPE_COLORS[rec.matchType] ?? 'bg-gray-100 text-gray-700 border-gray-200';
                const label = rec.conceptLabel || iriFragment(rec.conceptIri);
                return (
                  <div
                    key={i}
                    title={rec.reasoning ?? rec.conceptIri}
                    className={`inline-flex flex-col px-2 py-1 rounded border text-xs font-medium ${colorClass}`}
                  >
                    <span>{label}</span>
                    <span className="font-normal opacity-70">{iriFragment(rec.conceptIri)}</span>
                    {rec.matchType && (
                      <span className="font-normal opacity-60 capitalize">
                        {rec.matchType.replace(/Match$/, ' match')}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
            {element.vocabMappingReasoning && (
              <p className="text-xs text-violet-700 italic">{element.vocabMappingReasoning}</p>
            )}
          </div>
          {canAction ? (
            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => accept.mutate()}
                disabled={isPending}
                className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {accept.isPending ? 'Accepting…' : 'Accept All'}
              </button>
              <button
                onClick={() => reject.mutate()}
                disabled={isPending}
                className="px-3 py-1.5 bg-white text-gray-700 text-xs font-medium rounded border border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {reject.isPending ? 'Rejecting…' : 'Reject'}
              </button>
            </div>
          ) : (
            <p className="text-xs text-violet-600 italic shrink-0">Owner only</p>
          )}
        </div>
      </td>
    </tr>
  );
}
