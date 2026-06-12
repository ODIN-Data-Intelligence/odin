import { useMutation, useQueryClient } from '@tanstack/react-query';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
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

  const accept = useMutation({ mutationFn: () => logicalElementApi.acceptPii(element.id), onSuccess: applyUpdate });
  const reject = useMutation({ mutationFn: () => logicalElementApi.rejectPii(element.id), onSuccess: applyUpdate });
  const isPending = accept.isPending || reject.isPending;

  const pii = element.recommendedIsPersonalInformation;
  const direct = element.recommendedIsDirectIdentifier;

  return (
    <TableRow sx={{ bgcolor: 'error.50', borderTop: 1, borderColor: 'error.200' }}>
      <TableCell colSpan={7} sx={{ py: 1.5, px: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 3 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="caption" fontWeight={600} color="error.dark" sx={{ display: 'block', mb: 0.5 }}>AI PII Recommendation</Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Typography variant="caption" color="error.dark">Personal Information:</Typography>
              <Chip label={pii ? 'YES' : 'NO'} size="small" color={pii ? 'error' : 'default'} sx={{ height: 18, fontSize: 11, fontWeight: 700 }} />
              <Typography variant="caption" color="error.dark">Direct Identifier:</Typography>
              <Chip label={direct ? 'YES' : 'NO'} size="small" color={direct ? 'warning' : 'default'} sx={{ height: 18, fontSize: 11, fontWeight: 700 }} />
            </Box>
            {element.piiRecommendationReasoning && (
              <Typography variant="caption" color="error.dark" sx={{ fontStyle: 'italic' }}>{element.piiRecommendationReasoning}</Typography>
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
            <Typography variant="caption" color="error.main" sx={{ fontStyle: 'italic', flexShrink: 0 }}>Owner only</Typography>
          )}
        </Box>
      </TableCell>
    </TableRow>
  );
}
