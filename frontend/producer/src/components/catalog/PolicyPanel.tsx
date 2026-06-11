import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { policyApi } from '@datacatalog/shared';
import type { PolicyComponentSummary, EvaluationDecision } from '@datacatalog/shared';
import { useAuthStore } from '../../store/authStore';

interface Props {
  datasetId: string;
}

const PIECE_TYPE_COLORS: Record<string, string> = {
  CLASSIFICATION: 'bg-blue-50 text-blue-700 border-blue-200',
  REGULATION:     'bg-purple-50 text-purple-700 border-purple-200',
  CONTRACTUAL:    'bg-amber-50 text-amber-700 border-amber-200',
  LOGICAL_TYPE:   'bg-teal-50 text-teal-700 border-teal-200',
  CUSTOM:         'bg-gray-50 text-gray-700 border-gray-200',
};

export default function PolicyPanel({ datasetId }: Props) {
  const { hasAnyRole } = useAuthStore();
  const qc = useQueryClient();
  const canManage = hasAnyRole(['administrator', 'data-governance']);

  const [expandedPieces, setExpandedPieces] = useState<Set<string>>(new Set());
  const [showAssembled, setShowAssembled] = useState(false);
  const [showOverride, setShowOverride] = useState(false);
  const [overrideJson, setOverrideJson] = useState('');
  const [overrideErr, setOverrideErr] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [showEvaluate, setShowEvaluate] = useState(false);
  const [mContext, setMContext] = useState('{\n  "callerRole": "DATA_OWNER"\n}');
  const [mContextErr, setMContextErr] = useState('');
  const [evalResult, setEvalResult] = useState<{ granted: boolean; decisions: EvaluationDecision[] } | null>(null);

  const { data: components, isLoading: loadingComponents, error: componentsError } = useQuery({
    queryKey: ['policy-components', datasetId],
    queryFn: () => policyApi.getComponents(datasetId),
    enabled: !!datasetId,
    staleTime: 30_000,
    retry: false,
  });

  const { data: evaluations, isLoading: loadingEvals } = useQuery({
    queryKey: ['policy-evaluations', datasetId],
    queryFn: () => policyApi.listEvaluations(datasetId),
    enabled: !!datasetId,
    staleTime: 30_000,
    retry: false,
  });

  const upsertMut = useMutation({
    mutationFn: (json: string) => policyApi.upsertPolicy(datasetId, { policyJson: json, policyLevel: 'A' }),
    onSuccess: () => {
      setShowOverride(false);
      setOverrideJson('');
      setOverrideErr('');
      qc.invalidateQueries({ queryKey: ['policy-components', datasetId] });
      qc.invalidateQueries({ queryKey: ['policy-evaluations', datasetId] });
    },
    onError: () => setOverrideErr('Save failed. Check that the JSON is valid ODRL.'),
  });

  const deleteMut = useMutation({
    mutationFn: () => policyApi.deletePolicy(datasetId),
    onSuccess: () => {
      setConfirmDelete(false);
      qc.invalidateQueries({ queryKey: ['policy-components', datasetId] });
      qc.invalidateQueries({ queryKey: ['policy-evaluations', datasetId] });
    },
  });

  const evaluateMut = useMutation({
    mutationFn: (M: Record<string, unknown>) => policyApi.evaluate(datasetId, { M }),
    onSuccess: (res) => {
      setEvalResult({ granted: res.granted, decisions: res.decisions });
      qc.invalidateQueries({ queryKey: ['policy-evaluations', datasetId] });
    },
  });

  const notRegistered = !loadingComponents && (componentsError || !components);

  function togglePiece(id: string) {
    setExpandedPieces(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  function handleOverrideSave() {
    setOverrideErr('');
    try { JSON.parse(overrideJson); } catch { setOverrideErr('Invalid JSON.'); return; }
    upsertMut.mutate(overrideJson);
  }

  function handleEvaluate() {
    setMContextErr('');
    setEvalResult(null);
    let parsed: Record<string, unknown>;
    try { parsed = JSON.parse(mContext); } catch { setMContextErr('Invalid JSON.'); return; }
    evaluateMut.mutate(parsed);
  }

  const pieceTypeColor = (t: string) => PIECE_TYPE_COLORS[t] ?? PIECE_TYPE_COLORS.CUSTOM;

  return (
    <div className="space-y-3">
      {/* Section header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-gray-800">ODRL Policy Enforcement</p>
          <p className="text-xs text-gray-500 mt-0.5">
            Policy pieces registered with the enforcement engine (ODRE). Evaluated at request time.
          </p>
        </div>
        <span className={`flex-shrink-0 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
          notRegistered
            ? 'bg-gray-100 text-gray-500'
            : 'bg-green-100 text-green-700'
        }`}>
          {notRegistered ? 'Not registered' : 'Active'}
        </span>
      </div>

      {loadingComponents && <p className="text-xs text-gray-400 py-2">Loading…</p>}

      {notRegistered && !loadingComponents && (
        <div className="border border-gray-200 rounded-lg px-4 py-4 text-sm text-gray-500">
          <p>No policy is registered with the enforcement engine for this dataset.</p>
          <p className="mt-1 text-xs text-gray-400">
            Accept the Terms of Use on this dataset to auto-register the derived policy, or use the Override option below.
          </p>
          {canManage && (
            <button
              onClick={() => setShowOverride(v => !v)}
              className="mt-3 px-3 py-1.5 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700"
            >
              Override Policy
            </button>
          )}
        </div>
      )}

      {components && (
        <div className="border border-gray-200 rounded-lg overflow-hidden text-sm">
          {/* Policy pieces */}
          <div className="divide-y divide-gray-100">
            {components.components.length === 0 && (
              <p className="px-4 py-3 text-xs text-gray-400 italic">
                No policy pieces linked. Policy was set manually — see assembled ODRL below.
              </p>
            )}
            {components.components.map((piece) => (
              <PieceRow
                key={piece.pieceId}
                piece={piece}
                expanded={expandedPieces.has(piece.pieceId)}
                onToggle={() => togglePiece(piece.pieceId)}
                colorClass={pieceTypeColor(piece.pieceType)}
              />
            ))}
          </div>

          {/* Assembled ODRL toggle */}
          <div className="border-t border-gray-100">
            <button
              onClick={() => setShowAssembled(v => !v)}
              className="w-full flex items-center justify-between px-4 py-2.5 text-xs text-gray-500 hover:bg-gray-50"
            >
              <span className="font-medium">Assembled ODRL Policy</span>
              <span>{showAssembled ? '▲' : '▼'}</span>
            </button>
            {showAssembled && (
              <pre className="px-4 pb-4 text-xs text-gray-700 overflow-x-auto bg-gray-50 leading-relaxed">
                {JSON.stringify(components.assembledPolicy, null, 2)}
              </pre>
            )}
          </div>

          {/* Action bar */}
          {canManage && (
            <div className="border-t border-gray-200 bg-gray-50 px-4 py-3 flex items-center gap-2 flex-wrap">
              <button
                onClick={() => { setShowEvaluate(v => !v); setEvalResult(null); }}
                className="px-3 py-1.5 text-xs font-medium text-white bg-indigo-600 rounded hover:bg-indigo-700"
              >
                Evaluate
              </button>
              <button
                onClick={() => {
                  setShowOverride(v => !v);
                  if (!showOverride) {
                    setOverrideJson(JSON.stringify(components.assembledPolicy, null, 2));
                  }
                }}
                className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
              >
                Override Policy
              </button>
              {confirmDelete ? (
                <span className="flex items-center gap-2 ml-auto">
                  <span className="text-xs text-gray-600">Delete policy record?</span>
                  <button
                    onClick={() => deleteMut.mutate()}
                    disabled={deleteMut.isPending}
                    className="px-3 py-1.5 text-xs font-medium text-white bg-red-600 rounded hover:bg-red-700 disabled:opacity-50"
                  >
                    {deleteMut.isPending ? 'Deleting…' : 'Confirm Delete'}
                  </button>
                  <button
                    onClick={() => setConfirmDelete(false)}
                    className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                </span>
              ) : (
                <button
                  onClick={() => setConfirmDelete(true)}
                  className="ml-auto px-3 py-1.5 text-xs font-medium text-red-600 bg-white border border-red-200 rounded hover:bg-red-50"
                >
                  Delete Policy
                </button>
              )}
            </div>
          )}

          {/* Evaluate panel */}
          {showEvaluate && canManage && (
            <div className="border-t border-gray-200 px-4 py-4 space-y-3 bg-indigo-50">
              <p className="text-xs font-medium text-gray-700">
                Run ODRE enforcement — provide runtime context (M map):
              </p>
              <textarea
                value={mContext}
                onChange={e => setMContext(e.target.value)}
                rows={4}
                className="w-full font-mono text-xs border border-gray-300 rounded p-2 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-300"
              />
              {mContextErr && <p className="text-xs text-red-600">{mContextErr}</p>}
              <div className="flex items-center gap-2">
                <button
                  onClick={handleEvaluate}
                  disabled={evaluateMut.isPending}
                  className="px-3 py-1.5 text-xs font-medium text-white bg-indigo-600 rounded hover:bg-indigo-700 disabled:opacity-50"
                >
                  {evaluateMut.isPending ? 'Evaluating…' : 'Run Evaluation'}
                </button>
                {evaluateMut.isError && (
                  <p className="text-xs text-red-600">Evaluation failed.</p>
                )}
              </div>
              {evalResult && (
                <EvalResultBlock granted={evalResult.granted} decisions={evalResult.decisions} />
              )}
            </div>
          )}

          {/* Override panel */}
          {showOverride && canManage && (
            <div className="border-t border-gray-200 px-4 py-4 space-y-3 bg-amber-50">
              <p className="text-xs font-medium text-gray-700">
                Paste a complete ODRL Set document to override the assembled policy:
              </p>
              <textarea
                value={overrideJson}
                onChange={e => setOverrideJson(e.target.value)}
                rows={10}
                className="w-full font-mono text-xs border border-gray-300 rounded p-2 bg-white focus:outline-none focus:ring-2 focus:ring-amber-300"
                placeholder={'{\n  "@context": "http://www.w3.org/ns/odrl.jsonld",\n  "@type": "Set",\n  "uid": "...",\n  "permission": [...]\n}'}
              />
              {overrideErr && <p className="text-xs text-red-600">{overrideErr}</p>}
              <div className="flex gap-2">
                <button
                  onClick={handleOverrideSave}
                  disabled={upsertMut.isPending}
                  className="px-3 py-1.5 text-xs font-medium text-white bg-amber-600 rounded hover:bg-amber-700 disabled:opacity-50"
                >
                  {upsertMut.isPending ? 'Saving…' : 'Save Override'}
                </button>
                <button
                  onClick={() => { setShowOverride(false); setOverrideErr(''); }}
                  className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Override panel for not-registered state */}
      {notRegistered && showOverride && canManage && (
        <div className="border border-gray-200 rounded-lg px-4 py-4 space-y-3 bg-amber-50 text-sm">
          <p className="text-xs font-medium text-gray-700">
            Paste a complete ODRL Set document:
          </p>
          <textarea
            value={overrideJson}
            onChange={e => setOverrideJson(e.target.value)}
            rows={10}
            className="w-full font-mono text-xs border border-gray-300 rounded p-2 bg-white focus:outline-none focus:ring-2 focus:ring-amber-300"
            placeholder={'{\n  "@context": "http://www.w3.org/ns/odrl.jsonld",\n  "@type": "Set",\n  "uid": "...",\n  "permission": [...]\n}'}
          />
          {overrideErr && <p className="text-xs text-red-600">{overrideErr}</p>}
          <div className="flex gap-2">
            <button
              onClick={handleOverrideSave}
              disabled={upsertMut.isPending}
              className="px-3 py-1.5 text-xs font-medium text-white bg-amber-600 rounded hover:bg-amber-700 disabled:opacity-50"
            >
              {upsertMut.isPending ? 'Saving…' : 'Save'}
            </button>
            <button
              onClick={() => { setShowOverride(false); setOverrideErr(''); }}
              className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Recent evaluations */}
      {!loadingEvals && evaluations && evaluations.content.length > 0 && (
        <div className="space-y-1.5">
          <p className="text-xs font-medium text-gray-600">Recent Evaluations</p>
          <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 overflow-hidden text-xs">
            {evaluations.content.map(entry => (
              <div key={entry.id} className="flex items-center justify-between px-3 py-2 gap-2">
                <span className="font-mono text-gray-500 truncate">{entry.id.slice(0, 8)}…</span>
                <span className="text-gray-400 uppercase tracking-wide">{entry.action}</span>
                <span className={`px-2 py-0.5 rounded-full font-semibold ${
                  entry.granted ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                }`}>
                  {entry.granted ? 'Granted' : 'Denied'}
                </span>
                <span className="text-gray-400 flex-shrink-0">
                  {new Date(entry.createdAt).toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function PieceRow({
  piece, expanded, onToggle, colorClass,
}: {
  piece: PolicyComponentSummary;
  expanded: boolean;
  onToggle: () => void;
  colorClass: string;
}) {
  return (
    <div>
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors"
      >
        <span className={`flex-shrink-0 px-2 py-0.5 rounded border text-xs font-semibold ${colorClass}`}>
          {piece.pieceType}
        </span>
        <span className="flex-1 text-sm text-gray-800 truncate">
          {piece.label ?? piece.dimensionKey}
        </span>
        <span className="flex-shrink-0 text-xs text-gray-400 font-mono">{piece.policyLevel}</span>
        <span className="flex-shrink-0 text-gray-400 text-xs">{expanded ? '▲' : '▼'}</span>
      </button>
      {expanded && (
        <div className="px-4 pb-3 pt-0 bg-gray-50 border-t border-gray-100">
          <OdrlFragment fragment={piece.policyFragment} />
        </div>
      )}
    </div>
  );
}

function OdrlFragment({ fragment }: { fragment: Record<string, unknown> }) {
  const permissions  = (fragment.permission  as { action: string }[] | undefined) ?? [];
  const prohibitions = (fragment.prohibition as { action: string }[] | undefined) ?? [];
  const obligations  = (fragment.obligation  as { action: string }[] | undefined) ?? [];

  return (
    <div className="mt-2 space-y-1.5 text-xs">
      {permissions.length > 0 && (
        <div className="flex items-start gap-2">
          <span className="flex-shrink-0 font-semibold text-green-700 w-20">Permitted</span>
          <span className="text-gray-600">{permissions.map(r => r.action).join(', ')}</span>
        </div>
      )}
      {prohibitions.length > 0 && (
        <div className="flex items-start gap-2">
          <span className="flex-shrink-0 font-semibold text-red-700 w-20">Prohibited</span>
          <span className="text-gray-600">{prohibitions.map(r => r.action).join(', ')}</span>
        </div>
      )}
      {obligations.length > 0 && (
        <div className="flex items-start gap-2">
          <span className="flex-shrink-0 font-semibold text-amber-700 w-20">Obligated</span>
          <span className="text-gray-600">{obligations.map(r => r.action).join(', ')}</span>
        </div>
      )}
    </div>
  );
}

function EvalResultBlock({ granted, decisions }: { granted: boolean; decisions: EvaluationDecision[] }) {
  return (
    <div className={`rounded-lg border px-4 py-3 text-xs space-y-2 ${
      granted ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'
    }`}>
      <div className="flex items-center gap-2">
        <span className={`text-sm font-bold ${granted ? 'text-green-700' : 'text-red-700'}`}>
          {granted ? '✓ Access Granted' : '✗ Access Denied'}
        </span>
      </div>
      {decisions.length > 0 && (
        <div className="space-y-1">
          {decisions.map((d, i) => (
            <div key={i} className="flex items-center gap-2 text-gray-600">
              <span className="font-mono bg-white border border-gray-200 rounded px-1.5 py-0.5">{d.action}</span>
              <span className="text-gray-400">→</span>
              <span className={d.delegated ? 'text-amber-600 italic' : 'text-gray-700'}>
                {d.delegated ? `delegate (${d.result})` : d.result}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
