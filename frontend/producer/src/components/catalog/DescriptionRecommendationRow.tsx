import { useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalElementApi } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function DescriptionRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();

  const applyUpdate = (updated: LogicalDataElement) => {
    qc.setQueryData<LogicalDataElement[]>(
      ['logical-elements', modelId],
      (old) => old?.map(el => el.id === updated.id ? updated : el),
    );
  };

  const accept = useMutation({
    mutationFn: () => logicalElementApi.acceptDescription(element.id),
    onSuccess: applyUpdate,
  });

  const reject = useMutation({
    mutationFn: () => logicalElementApi.rejectDescription(element.id),
    onSuccess: applyUpdate,
  });

  const isPending = accept.isPending || reject.isPending;

  return (
    <tr className="bg-blue-50 border-t border-blue-200">
      <td colSpan={6} className="px-4 py-3">
        <div className="flex items-start gap-4">
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-blue-900 mb-1">AI Description Suggestion</p>
            <p className="text-sm text-blue-800 mb-1">{element.recommendedDescription}</p>
            {element.descriptionReasoning && (
              <p className="text-xs text-blue-600 italic">{element.descriptionReasoning}</p>
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
            <p className="text-xs text-blue-600 italic shrink-0">Owner only</p>
          )}
        </div>
      </td>
    </tr>
  );
}
