import { useMutation, useQueryClient } from '@tanstack/react-query';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import { logicalElementApi, ClassificationBadge } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function ClassificationRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();

  const applyUpdate = (updated: LogicalDataElement) => {
    qc.setQueryData<LogicalDataElement[]>(
      ['logical-elements', modelId],
      (old) => old?.map(el => el.id === updated.id ? updated : el),
    );
  };

  const accept = useMutation({ mutationFn: () => logicalElementApi.acceptClassification(element.id), onSuccess: applyUpdate });
  const reject = useMutation({ mutationFn: () => logicalElementApi.rejectClassification(element.id), onSuccess: applyUpdate });
  const isPending = accept.isPending || reject.isPending;

  return (
    <TableRow sx={{ bgcolor: 'warning.50', borderTop: 1, borderColor: 'warning.200' }}>
      <TableCell colSpan={7} sx={{ py: 1.5, px: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 3 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="caption" fontWeight={600} color="warning.dark" sx={{ display: 'block', mb: 0.5 }}>AI Recommendation</Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
              <Typography variant="caption" color="warning.dark">Suggested level:</Typography>
              <ClassificationBadge level={element.recommendedClassification} />
            </Box>
            {element.classificationReasoning && (
              <Typography variant="caption" color="warning.dark" sx={{ fontStyle: 'italic' }}>{element.classificationReasoning}</Typography>
            )}
          </Box>
          {canAction ? (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
              <Button size="small" variant="contained" color="success" disabled={isPending} onClick={() => accept.mutate()} sx={{ textTransform: 'none', fontSize: 11 }}>
                {accept.isPending ? 'Accepting…' : 'Accept'}
              </Button>
              <Button size="small" variant="outlined" disabled={isPending} onClick={() => reject.mutate()} sx={{ textTransform: 'none', fontSize: 11 }}>
                {reject.isPending ? 'Rejecting…' : 'Reject'}
              </Button>
            </Box>
          ) : (
            <Typography variant="caption" color="warning.main" sx={{ fontStyle: 'italic', flexShrink: 0 }}>Owner only</Typography>
          )}
        </Box>
      </TableCell>
    </TableRow>
  );
}
