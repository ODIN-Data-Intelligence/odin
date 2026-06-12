import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import { logicalElementApi, iriFragment } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';

const MATCH_TYPE_COLORS: Record<string, 'success' | 'primary' | 'secondary' | 'warning' | 'info'> = {
  exactMatch: 'success',
  closeMatch: 'primary',
  relatedMatch: 'secondary',
  broadMatch: 'warning',
  narrowMatch: 'info',
};

interface Props {
  element: LogicalDataElement;
  modelId: string;
  canAction: boolean;
}

export default function VocabRecommendationRow({ element, modelId, canAction }: Props) {
  const qc = useQueryClient();

  const applyUpdate = (updated: LogicalDataElement) => {
    qc.setQueryData<LogicalDataElement[]>(
      ['logical-elements', modelId],
      (old) => old?.map(el => el.id === updated.id ? updated : el),
    );
  };

  const recommendations = element.recommendedVocabMappings ?? [];
  const allIris = recommendations.map(r => r.conceptIri);
  const [selected, setSelected] = useState<Set<string>>(new Set(allIris));

  const accept = useMutation({ mutationFn: (iris: string[]) => logicalElementApi.acceptVocabConcepts(element.id, iris), onSuccess: applyUpdate });
  const reject = useMutation({ mutationFn: () => logicalElementApi.rejectVocabConcepts(element.id), onSuccess: applyUpdate });
  const isPending = accept.isPending || reject.isPending;

  function toggle(iri: string) {
    setSelected(prev => { const n = new Set(prev); n.has(iri) ? n.delete(iri) : n.add(iri); return n; });
  }

  const noneSelected = selected.size === 0;
  const allSelected = selected.size === allIris.length;

  return (
    <TableRow sx={{ bgcolor: 'secondary.50', borderTop: 1, borderColor: 'secondary.200' }}>
      <TableCell colSpan={7} sx={{ py: 1.5, px: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 3 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
              <Typography variant="caption" fontWeight={600} color="secondary.dark">AI Vocabulary Suggestions</Typography>
              {canAction && <Typography variant="caption" color="secondary.main">Click concepts to select</Typography>}
            </Box>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
              {recommendations.map((rec, i) => {
                const isSelected = selected.has(rec.conceptIri);
                const label = rec.conceptLabel || iriFragment(rec.conceptIri);
                const color = MATCH_TYPE_COLORS[rec.matchType] ?? 'default';
                return (
                  <Box
                    key={i}
                    component="button"
                    onClick={() => canAction && !isPending && toggle(rec.conceptIri)}
                    title={rec.reasoning ?? rec.conceptIri}
                    sx={{
                      display: 'inline-flex', flexDirection: 'column', alignItems: 'flex-start',
                      px: 1, py: 0.5, borderRadius: 1, border: 1,
                      cursor: canAction && !isPending ? 'pointer' : 'default',
                      opacity: isSelected ? 1 : 0.55,
                      bgcolor: isSelected ? `${color}.light` : 'background.paper',
                      borderColor: isSelected ? `${color}.main` : 'divider',
                      transition: 'opacity 0.15s',
                      background: 'none',
                      '&:hover': canAction && !isPending ? { opacity: 1 } : {},
                    }}
                  >
                    <Typography variant="caption" fontWeight={600} sx={{ lineHeight: 1.3 }}>{label}</Typography>
                    <Typography variant="caption" sx={{ fontSize: 10, opacity: 0.65, lineHeight: 1.2 }}>{iriFragment(rec.conceptIri)}</Typography>
                    {rec.matchType && (
                      <Typography variant="caption" sx={{ fontSize: 10, opacity: 0.55, lineHeight: 1.2, textTransform: 'capitalize' }}>
                        {rec.matchType.replace(/Match$/, ' match')}
                      </Typography>
                    )}
                  </Box>
                );
              })}
            </Box>
            {element.vocabMappingReasoning && (
              <Typography variant="caption" color="secondary.dark" sx={{ fontStyle: 'italic', display: 'block', mt: 0.5 }}>{element.vocabMappingReasoning}</Typography>
            )}
          </Box>
          {canAction ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75, flexShrink: 0, minWidth: 110 }}>
              <Button size="small" variant="contained" color="success" disabled={isPending || noneSelected} onClick={() => accept.mutate([...selected])} sx={{ textTransform: 'none', fontSize: 11 }}>
                {accept.isPending ? 'Accepting…' : allSelected ? 'Accept All' : `Accept (${selected.size})`}
              </Button>
              <Button size="small" variant="outlined" disabled={isPending} onClick={() => reject.mutate()} sx={{ textTransform: 'none', fontSize: 11 }}>
                {reject.isPending ? 'Rejecting…' : 'Reject All'}
              </Button>
            </Box>
          ) : (
            <Typography variant="caption" color="secondary.main" sx={{ fontStyle: 'italic', flexShrink: 0 }}>Owner only</Typography>
          )}
        </Box>
      </TableCell>
    </TableRow>
  );
}
