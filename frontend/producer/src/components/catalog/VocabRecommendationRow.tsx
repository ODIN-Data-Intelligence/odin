import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalElementApi, iriFragment } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

const MATCH_TYPE_COLORS: Record<string, { base: string; selected: string }> = {
  exactMatch:   { base: 'bg-green-50 text-green-700 border-green-200',  selected: 'bg-green-200 text-green-900 border-green-400 ring-2 ring-green-400' },
  closeMatch:   { base: 'bg-blue-50 text-blue-700 border-blue-200',    selected: 'bg-blue-200 text-blue-900 border-blue-400 ring-2 ring-blue-400' },
  relatedMatch: { base: 'bg-purple-50 text-purple-700 border-purple-200', selected: 'bg-purple-200 text-purple-900 border-purple-400 ring-2 ring-purple-400' },
  broadMatch:   { base: 'bg-orange-50 text-orange-700 border-orange-200', selected: 'bg-orange-200 text-orange-900 border-orange-400 ring-2 ring-orange-400' },
  narrowMatch:  { base: 'bg-yellow-50 text-yellow-700 border-yellow-200', selected: 'bg-yellow-200 text-yellow-900 border-yellow-400 ring-2 ring-yellow-400' },
};

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function VocabRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['logical-elements', modelId] });

  const recommendations = element.recommendedVocabMappings ?? [];
  const allIris = recommendations.map(r => r.conceptIri);

  const [selected, setSelected] = useState<Set<string>>(new Set(allIris));

  const accept = useMutation({
    mutationFn: (iris?: string[]) => logicalElementApi.acceptVocabConcepts(element.id, iris),
    onSuccess: invalidate,
  });

  const reject = useMutation({
    mutationFn: () => logicalElementApi.rejectVocabConcepts(element.id),
    onSuccess: invalidate,
  });

  const isPending = accept.isPending || reject.isPending;

  function toggle(iri: string) {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(iri) ? next.delete(iri) : next.add(iri);
      return next;
    });
  }

  const noneSelected = selected.size === 0;
  const allSelected  = selected.size === allIris.length;

  return (
    <tr className="bg-violet-50 border-t border-violet-200">
      <td colSpan={6} className="px-4 py-3">
        <div className="flex items-start gap-4">
          <div className="flex-1 min-w-0 space-y-2">
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-violet-900">AI Vocabulary Suggestions</p>
              {canAction && (
                <span className="text-xs text-violet-500">Click concepts to select</span>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {recommendations.map((rec, i) => {
                const colors = MATCH_TYPE_COLORS[rec.matchType] ?? {
                  base: 'bg-gray-50 text-gray-700 border-gray-200',
                  selected: 'bg-gray-200 text-gray-900 border-gray-400 ring-2 ring-gray-400',
                };
                const isSelected = selected.has(rec.conceptIri);
                const label = rec.conceptLabel || iriFragment(rec.conceptIri);
                return (
                  <button
                    key={i}
                    type="button"
                    disabled={!canAction || isPending}
                    onClick={() => toggle(rec.conceptIri)}
                    title={rec.reasoning ?? rec.conceptIri}
                    className={`inline-flex flex-col items-start px-2 py-1 rounded border text-xs font-medium text-left transition-all
                      ${isSelected ? colors.selected : colors.base}
                      ${canAction && !isPending ? 'cursor-pointer hover:opacity-80' : 'cursor-default'}
                    `}
                  >
                    <span className="flex items-center gap-1">
                      {canAction && (
                        <span className={`w-3 h-3 rounded-sm border flex items-center justify-center flex-shrink-0 ${isSelected ? 'bg-current border-current' : 'border-current opacity-40'}`}>
                          {isSelected && (
                            <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
                              <path d="M1.5 4L3 5.5L6.5 2" stroke="white" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/>
                            </svg>
                          )}
                        </span>
                      )}
                      {label}
                    </span>
                    <span className="font-normal opacity-70">{iriFragment(rec.conceptIri)}</span>
                    {rec.matchType && (
                      <span className="font-normal opacity-60 capitalize">
                        {rec.matchType.replace(/Match$/, ' match')}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
            {element.vocabMappingReasoning && (
              <p className="text-xs text-violet-700 italic">{element.vocabMappingReasoning}</p>
            )}
          </div>
          {canAction ? (
            <div className="flex flex-col gap-1.5 shrink-0 min-w-[110px]">
              <button
                onClick={() => accept.mutate(allSelected ? undefined : [...selected])}
                disabled={isPending || noneSelected}
                className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed text-center"
              >
                {accept.isPending
                  ? 'Accepting…'
                  : allSelected
                  ? 'Accept All'
                  : `Accept (${selected.size})`}
              </button>
              <button
                onClick={() => reject.mutate()}
                disabled={isPending}
                className="px-3 py-1.5 bg-white text-gray-700 text-xs font-medium rounded border border-gray-300 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed text-center"
              >
                {reject.isPending ? 'Rejecting…' : 'Reject All'}
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
