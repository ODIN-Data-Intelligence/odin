import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
    onSuccess: () => {
      setConfirmReset(false);
      qc.invalidateQueries({ queryKey: ['dataset-terms', datasetId] });
    },
  });

  const isExplicit = terms?.policySource === 'explicit';

  return (
    <div className="space-y-3">
      {/* Section header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-gray-800">Terms of Use Policy</p>
          <p className="text-xs text-gray-500 mt-0.5">
            ODRL policy derived from element classifications and vocabulary concept mappings.
          </p>
        </div>
        {terms && (
          <span className={`flex-shrink-0 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
            isExplicit ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'
          }`}>
            {isExplicit ? 'Accepted' : 'Recommended'}
          </span>
        )}
      </div>

      {isLoading && <p className="text-xs text-gray-400 py-2">Loading…</p>}
      {!isLoading && !terms && <p className="text-xs text-gray-500">Terms of use are unavailable for this dataset.</p>}

      {terms && (
        <div className="border border-gray-200 rounded-lg overflow-hidden text-sm">
          {/* Derivation summary strip */}
          {terms.derivationDetails && !isExplicit && (
            <div className="px-4 py-2.5 bg-gray-50 border-b border-gray-100 flex flex-wrap gap-x-4 gap-y-1">
              {terms.derivationDetails.classifiedElementCount > 0 && (
                <span className="text-xs text-gray-500">
                  <span className="font-medium text-gray-700">{terms.derivationDetails.classifiedElementCount}</span>{' '}
                  classified element{terms.derivationDetails.classifiedElementCount !== 1 ? 's' : ''}
                  {terms.derivationDetails.distinctClassifications.length > 0 && (
                    <> · {terms.derivationDetails.distinctClassifications.join(', ')}</>
                  )}
                </span>
              )}
              {terms.derivationDetails.vocabConceptCount > 0 && (
                <span className="text-xs text-gray-500">
                  <span className="font-medium text-gray-700">{terms.derivationDetails.vocabConceptCount}</span>{' '}
                  vocabulary concept{terms.derivationDetails.vocabConceptCount !== 1 ? 's' : ''}
                </span>
              )}
              {terms.derivationDetails.matchedSignals.length > 0 && (
                <span className="text-xs text-gray-400">
                  signals: {terms.derivationDetails.matchedSignals.join(', ')}
                </span>
              )}
            </div>
          )}

          {/* Shared display: access level, source banners, rules, regulations, ODRL JSON */}
          <div className="px-4 py-4">
            <TermsOfUseDisplay terms={terms} />
          </div>

          {/* Action footer */}
          <div className="bg-gray-50 border-t border-gray-200 px-4 py-3">
            {canAction ? (
              isExplicit ? (
                <div className="flex items-center justify-between gap-4">
                  <p className="text-xs text-gray-500">
                    Policy is locked. Reset to re-derive from current element classifications.
                  </p>
                  {confirmReset ? (
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className="text-xs text-gray-600">Confirm reset?</span>
                      <button
                        onClick={() => resetMut.mutate()}
                        disabled={resetMut.isPending}
                        className="px-3 py-1.5 text-xs font-medium text-white bg-red-600 rounded hover:bg-red-700 disabled:opacity-50"
                      >
                        {resetMut.isPending ? 'Resetting…' : 'Reset'}
                      </button>
                      <button
                        onClick={() => setConfirmReset(false)}
                        className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setConfirmReset(true)}
                      className="flex-shrink-0 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
                    >
                      Reset to Derived
                    </button>
                  )}
                </div>
              ) : (
                <div className="flex items-start justify-between gap-4">
                  <div className="space-y-1">
                    <p className="text-xs text-gray-500">
                      Review the recommended terms above, then accept to lock them as this dataset's official policy.
                    </p>
                    {terms.derivationDetails && !terms.derivationDetails.readyToAccept && (
                      <ReadinessHint d={terms.derivationDetails} />
                    )}
                    {acceptMut.isError && (
                      <p className="text-xs text-red-600">Failed to accept. Try again.</p>
                    )}
                  </div>
                  <button
                    onClick={() => acceptMut.mutate()}
                    disabled={acceptMut.isPending || !(terms.derivationDetails?.readyToAccept ?? false)}
                    title={
                      !(terms.derivationDetails?.readyToAccept ?? false)
                        ? 'All published elements must be classified and vocabulary-mapped before accepting'
                        : undefined
                    }
                    className="flex-shrink-0 px-3 py-1.5 text-xs font-medium text-white bg-green-600 rounded hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {acceptMut.isPending ? 'Accepting…' : 'Accept Policy'}
                  </button>
                </div>
              )
            ) : (
              <p className="text-xs text-gray-400 italic">
                Only the data owner can accept or reset the terms policy.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
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
    <ul className="space-y-0.5">
      {missing.map(msg => (
        <li key={msg} className="flex items-start gap-1.5 text-xs text-amber-700">
          <span className="font-bold flex-shrink-0 mt-0.5">!</span>
          {msg}
        </li>
      ))}
    </ul>
  );
}
