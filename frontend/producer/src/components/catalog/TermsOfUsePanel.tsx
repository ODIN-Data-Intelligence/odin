import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Alert from '@mui/material/Alert';
import Tooltip from '@mui/material/Tooltip';
import { datasetApi, TermsOfUseDisplay } from '@datacatalog/shared';
import type { TermsOfUseDerivationDetails } from '@datacatalog/shared';

interface Props {
  datasetId: string;
  canAction: boolean;
}

export default function TermsOfUsePanel({ datasetId, canAction }: Props) {
  const qc = useQueryClient();
  const [confirmReset, setConfirmReset] = useState(false);

  const { data: terms, isLoading } = useQuery({
    queryKey: ['dataset-terms', datasetId],
    queryFn: () => datasetApi.getTermsOfUse(datasetId),
    enabled: !!datasetId,
    staleTime: 30_000,
  });

  const acceptMut = useMutation({
    mutationFn: () => datasetApi.acceptTermsOfUse(datasetId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dataset-terms', datasetId] }),
  });

  const resetMut = useMutation({
    mutationFn: () => datasetApi.resetTermsOfUse(datasetId),
    onSuccess: () => { setConfirmReset(false); qc.invalidateQueries({ queryKey: ['dataset-terms', datasetId] }); },
  });

  const isExplicit = terms?.policySource === 'explicit';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="body2" fontWeight={600} color="text.primary">Terms of Use Policy</Typography>
          <Typography variant="caption" color="text.secondary">
            ODRL policy derived from element classifications and vocabulary concept mappings.
          </Typography>
        </Box>
        {terms && (
          <Chip
            label={isExplicit ? 'Accepted' : 'Recommended'}
            color={isExplicit ? 'success' : 'warning'}
            size="small"
            sx={{ flexShrink: 0, height: 20, fontSize: 11 }}
          />
        )}
      </Box>

      {isLoading && <Typography variant="caption" color="text.disabled">Loading…</Typography>}
      {!isLoading && !terms && (
        <Typography variant="caption" color="text.secondary">Terms of use are unavailable for this dataset.</Typography>
      )}

      {terms && (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          {terms.derivationDetails && !isExplicit && (
            <Box sx={{ px: 2, py: 1.25, bgcolor: 'grey.50', borderBottom: 1, borderColor: 'divider', display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {terms.derivationDetails.classifiedElementCount > 0 && (
                <Typography variant="caption" color="text.secondary">
                  <Box component="span" fontWeight={600} color="text.primary">{terms.derivationDetails.classifiedElementCount}</Box>{' '}
                  classified element{terms.derivationDetails.classifiedElementCount !== 1 ? 's' : ''}
                  {terms.derivationDetails.distinctClassifications.length > 0 && (
                    <> · {terms.derivationDetails.distinctClassifications.join(', ')}</>
                  )}
                </Typography>
              )}
              {terms.derivationDetails.vocabConceptCount > 0 && (
                <Typography variant="caption" color="text.secondary">
                  <Box component="span" fontWeight={600} color="text.primary">{terms.derivationDetails.vocabConceptCount}</Box>{' '}
                  vocabulary concept{terms.derivationDetails.vocabConceptCount !== 1 ? 's' : ''}
                </Typography>
              )}
              {terms.derivationDetails.matchedSignals.length > 0 && (
                <Typography variant="caption" color="text.disabled">
                  signals: {terms.derivationDetails.matchedSignals.join(', ')}
                </Typography>
              )}
            </Box>
          )}

          <Box sx={{ px: 2, py: 2 }}>
            <TermsOfUseDisplay terms={terms} />
          </Box>

          <Box sx={{ bgcolor: 'grey.50', borderTop: 1, borderColor: 'divider', px: 2, py: 1.5 }}>
            {canAction ? (
              isExplicit ? (
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Policy is locked. Reset to re-derive from current element classifications.
                  </Typography>
                  {confirmReset ? (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
                      <Typography variant="caption" color="text.secondary">Confirm reset?</Typography>
                      <Button size="small" variant="contained" color="error" disabled={resetMut.isPending} onClick={() => resetMut.mutate()} sx={{ textTransform: 'none', fontSize: 11 }}>
                        {resetMut.isPending ? 'Resetting…' : 'Reset'}
                      </Button>
                      <Button size="small" variant="outlined" onClick={() => setConfirmReset(false)} sx={{ textTransform: 'none', fontSize: 11 }}>Cancel</Button>
                    </Box>
                  ) : (
                    <Button size="small" variant="outlined" onClick={() => setConfirmReset(true)} sx={{ textTransform: 'none', fontSize: 11, flexShrink: 0 }}>
                      Reset to Derived
                    </Button>
                  )}
                </Box>
              ) : (
                <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      Review the recommended terms above, then accept to lock them as this dataset's official policy.
                    </Typography>
                    {terms.derivationDetails && !terms.derivationDetails.readyToAccept && (
                      <ReadinessHint d={terms.derivationDetails} />
                    )}
                    {acceptMut.isError && (
                      <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>Failed to accept. Try again.</Typography>
                    )}
                  </Box>
                  <Tooltip title={!(terms.derivationDetails?.readyToAccept ?? false) ? 'All published elements must be classified and vocabulary-mapped before accepting' : ''}>
                    <span>
                      <Button
                        size="small"
                        variant="contained"
                        color="success"
                        disabled={acceptMut.isPending || !(terms.derivationDetails?.readyToAccept ?? false)}
                        onClick={() => acceptMut.mutate()}
                        sx={{ textTransform: 'none', fontSize: 11, flexShrink: 0 }}
                      >
                        {acceptMut.isPending ? 'Accepting…' : 'Accept Policy'}
                      </Button>
                    </span>
                  </Tooltip>
                </Box>
              )
            ) : (
              <Typography variant="caption" color="text.disabled" sx={{ fontStyle: 'italic' }}>
                Only the data owner can accept or reset the terms policy.
              </Typography>
            )}
          </Box>
        </Paper>
      )}
    </Box>
  );
}

function ReadinessHint({ d }: { d: TermsOfUseDerivationDetails }) {
  const missing: string[] = [];

  if (d.totalPublishedElementCount === 0) {
    missing.push('No published logical model found — publish a model with classified elements first');
  } else {
    const unclassified = d.totalPublishedElementCount - d.classifiedElementCount;
    if (unclassified > 0)
      missing.push(`${unclassified} element${unclassified > 1 ? 's' : ''} still need${unclassified === 1 ? 's' : ''} an accepted classification`);
    const noVocab = d.totalPublishedElementCount - d.elementsWithVocabCount;
    if (noVocab > 0)
      missing.push(`${noVocab} element${noVocab > 1 ? 's' : ''} still need${noVocab === 1 ? 's' : ''} a controlled vocabulary mapping`);
  }

  if (missing.length === 0) return null;

  return (
    <Box sx={{ mt: 0.5 }}>
      {missing.map(msg => (
        <Box key={msg} sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
          <Typography variant="caption" color="warning.dark" fontWeight={700} sx={{ flexShrink: 0 }}>!</Typography>
          <Typography variant="caption" color="warning.dark">{msg}</Typography>
        </Box>
      ))}
    </Box>
  );
}
