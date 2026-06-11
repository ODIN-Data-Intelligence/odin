import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalElementApi } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function PiiRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();

  const applyUpdate = (updated: LogicalDataElement) => {
    qc.setQueryData<LogicalDataElement[]>(
      ['logical-elements', modelId],
      (old) => old?.map(el => el.id === updated.id ? updated : el),
    );
  };

  const accept = useMutation({
    mutationFn: () => logicalElementApi.acceptPii(element.id),
    onSuccess: applyUpdate,
  });

  const reject = useMutation({
    mutationFn: () => logicalElementApi.rejectPii(element.id),
    onSuccess: applyUpdate,
  });

  const isPending = accept.isPending || reject.isPending;

  const pii = element.recommendedIsPersonalInformation;
  const direct = element.recommendedIsDirectIdentifier;

  return (
    <tr className="bg-rose-50 border-t border-rose-200">
      <td colSpan={7} className="px-4 py-3">
        <div className="flex items-start gap-4">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-rose-900 mb-1">AI PII Recommendation</p>
            <div className="flex items-center gap-3 mb-1">
              <span className="text-sm text-rose-800">Personal Information:</span>
              <span className={`px-2 py-0.5 rounded text-xs font-semibold ${pii ? 'bg-rose-600 text-white' : 'bg-gray-200 text-gray-600'}`}>
                {pii ? 'YES' : 'NO'}
              </span>
              <span className="text-sm text-rose-800">Direct Identifier:</span>
              <span className={`px-2 py-0.5 rounded text-xs font-semibold ${direct ? 'bg-amber-500 text-white' : 'bg-gray-200 text-gray-600'}`}>
                {direct ? 'YES' : 'NO'}
              </span>
            </div>
            {element.piiRecommendationReasoning && (
              <p className="text-xs text-rose-700 italic">{element.piiRecommendationReasoning}</p>
            )}
          </div>
          {canAction ? (
            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => accept.mutate()}
                disabled={isPending}
                className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {accept.isPending ? 'Accepting…' : 'Accept'}
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
            <p className="text-xs text-rose-600 italic shrink-0">Owner only</p>
          )}
        </div>
      </td>
    </tr>
  );
}
