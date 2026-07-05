import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Alert from '@mui/material/Alert';
import TextField from '@mui/material/TextField';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import { policyApi } from '@datacatalog/shared';
import type { PolicyComponentSummary, EvaluationDecision } from '@datacatalog/shared';
import { useAuthStore } from '../../store/authStore';

interface Props {
  datasetId: string;
}

const PIECE_TYPE_COLORS: Record<string, 'primary' | 'secondary' | 'warning' | 'info' | 'default'> = {
  CLASSIFICATION: 'primary',
  REGULATION:     'secondary',
  CONTRACTUAL:    'warning',
  LOGICAL_TYPE:   'info',
  CUSTOM:         'default',
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
      setShowOverride(false); setOverrideJson(''); setOverrideErr('');
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
    setExpandedPieces(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n; });
  }

  function handleOverrideSave() {
    setOverrideErr('');
    try { JSON.parse(overrideJson); } catch { setOverrideErr('Invalid JSON.'); return; }
    upsertMut.mutate(overrideJson);
  }

  function handleEvaluate() {
    setMContextErr(''); setEvalResult(null);
    let parsed: Record<string, unknown>;
    try { parsed = JSON.parse(mContext); } catch { setMContextErr('Invalid JSON.'); return; }
    evaluateMut.mutate(parsed);
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="body2" fontWeight={600} color="text.primary">ODRL Policy Enforcement</Typography>
          <Typography variant="caption" color="text.secondary">
            Policy pieces registered with the enforcement engine (ODRE). Evaluated at request time.
          </Typography>
        </Box>
        <Chip
          label={notRegistered ? 'Not registered' : 'Active'}
          color={notRegistered ? 'default' : 'success'}
          size="small"
          sx={{ flexShrink: 0, height: 20, fontSize: 11 }}
        />
      </Box>

      {loadingComponents && <Typography variant="caption" color="text.disabled">Loading…</Typography>}

      {notRegistered && !loadingComponents && (
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="body2" color="text.secondary">No policy is registered with the enforcement engine for this dataset.</Typography>
          <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.5 }}>
            Accept the Terms of Use on this dataset to auto-register the derived policy, or use the Override option below.
          </Typography>
          {canManage && (
            <Button size="small" variant="contained" onClick={() => setShowOverride(v => !v)} sx={{ mt: 1.5, textTransform: 'none', fontSize: 11 }}>
              Override Policy
            </Button>
          )}
        </Paper>
      )}

      {notRegistered && showOverride && canManage && (
        <OverridePanel
          value={overrideJson}
          onChange={setOverrideJson}
          error={overrideErr}
          isPending={upsertMut.isPending}
          onSave={handleOverrideSave}
          onClose={() => { setShowOverride(false); setOverrideErr(''); }}
        />
      )}

      {components && (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          {components.components.length === 0 && (
            <Typography variant="caption" color="text.disabled" sx={{ px: 2, py: 1.5, fontStyle: 'italic', display: 'block' }}>
              No policy pieces linked. Policy was set manually — see assembled ODRL below.
            </Typography>
          )}
          {components.components.map((piece) => (
            <PieceRow
              key={piece.pieceId}
              piece={piece}
              expanded={expandedPieces.has(piece.pieceId)}
              onToggle={() => togglePiece(piece.pieceId)}
              color={PIECE_TYPE_COLORS[piece.pieceType] ?? 'default'}
            />
          ))}

          <Divider />
          <Box>
            <Box
              component="button"
              onClick={() => setShowAssembled(v => !v)}
              sx={{
                width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                px: 2, py: 1.25, bgcolor: 'transparent', border: 'none', cursor: 'pointer',
                '&:hover': { bgcolor: 'grey.50' },
              }}
            >
              <Typography variant="caption" fontWeight={600} color="text.secondary">Assembled ODRL Policy</Typography>
              <Typography variant="caption" color="text.disabled">{showAssembled ? '▲' : '▼'}</Typography>
            </Box>
            <Collapse in={showAssembled}>
              <Box component="pre" sx={{ px: 2, pb: 2, typography: 'caption', color: 'text.secondary', overflowX: 'auto', bgcolor: 'grey.50', m: 0 }}>
                {JSON.stringify(components.assembledPolicy, null, 2)}
              </Box>
            </Collapse>
          </Box>

          {canManage && (
            <>
              <Divider />
              <Box sx={{ bgcolor: 'grey.50', px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Button size="small" variant="contained" color="primary" onClick={() => { setShowEvaluate(v => !v); setEvalResult(null); }} sx={{ textTransform: 'none', fontSize: 11 }}>
                  Evaluate
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => { setShowOverride(v => !v); if (!showOverride) setOverrideJson(JSON.stringify(components.assembledPolicy, null, 2)); }}
                  sx={{ textTransform: 'none', fontSize: 11 }}
                >
                  Override Policy
                </Button>
                {confirmDelete ? (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 'auto' }}>
                    <Typography variant="caption" color="text.secondary">Delete policy record?</Typography>
                    <Button size="small" variant="contained" color="error" disabled={deleteMut.isPending} onClick={() => deleteMut.mutate()} sx={{ textTransform: 'none', fontSize: 11 }}>
                      {deleteMut.isPending ? 'Deleting…' : 'Confirm Delete'}
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => setConfirmDelete(false)} sx={{ textTransform: 'none', fontSize: 11 }}>Cancel</Button>
                  </Box>
                ) : (
                  <Button size="small" variant="outlined" color="error" onClick={() => setConfirmDelete(true)} sx={{ textTransform: 'none', fontSize: 11, ml: 'auto' }}>
                    Delete Policy
                  </Button>
                )}
              </Box>
            </>
          )}

          {showEvaluate && canManage && (
            <>
              <Divider />
              <Box sx={{ bgcolor: 'primary.50', px: 2, py: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Typography variant="caption" fontWeight={600} color="text.secondary">
                  Run ODRE enforcement — provide runtime context (M map):
                </Typography>
                <TextField
                  value={mContext}
                  onChange={e => setMContext(e.target.value)}
                  multiline
                  rows={4}
                  size="small"
                  fullWidth
                  inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }}
                  error={!!mContextErr}
                  helperText={mContextErr}
                />
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Button size="small" variant="contained" disabled={evaluateMut.isPending} onClick={handleEvaluate} sx={{ textTransform: 'none', fontSize: 11 }}>
                    {evaluateMut.isPending ? 'Evaluating…' : 'Run Evaluation'}
                  </Button>
                  {evaluateMut.isError && <Typography variant="caption" color="error">Evaluation failed.</Typography>}
                </Box>
                {evalResult && <EvalResultBlock granted={evalResult.granted} decisions={evalResult.decisions} />}
              </Box>
            </>
          )}

          {showOverride && canManage && (
            <>
              <Divider />
              <Box sx={{ bgcolor: 'warning.50', px: 2, py: 2 }}>
                <OverridePanel
                  value={overrideJson}
                  onChange={setOverrideJson}
                  error={overrideErr}
                  isPending={upsertMut.isPending}
                  onSave={handleOverrideSave}
                  onClose={() => { setShowOverride(false); setOverrideErr(''); }}
                />
              </Box>
            </>
          )}
        </Paper>
      )}

      {!loadingEvals && evaluations && evaluations.content.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ display: 'block', mb: 0.75 }}>Recent Evaluations</Typography>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            {evaluations.content.map(entry => (
              <Box key={entry.id} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 1.5, py: 1, gap: 1.5, borderBottom: 1, borderColor: 'divider', '&:last-child': { borderBottom: 'none' } }}>
                <Typography variant="caption" fontFamily="monospace" color="text.secondary">{entry.id.slice(0, 8)}…</Typography>
                <Typography variant="caption" color="text.disabled" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>{entry.action}</Typography>
                <Chip label={entry.granted ? 'Granted' : 'Denied'} color={entry.granted ? 'success' : 'error'} size="small" sx={{ height: 18, fontSize: 10 }} />
                <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0 }}>
                  {new Date(entry.createdAt).toLocaleString()}
                </Typography>
              </Box>
            ))}
          </Paper>
        </Box>
      )}
    </Box>
  );
}

function PieceRow({ piece, expanded, onToggle, color }: {
  piece: PolicyComponentSummary; expanded: boolean; onToggle: () => void;
  color: 'primary' | 'secondary' | 'warning' | 'info' | 'default';
}) {
  return (
    <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
      <Box
        component="button"
        onClick={onToggle}
        sx={{
          width: '100%', display: 'flex', alignItems: 'center', gap: 1.5, px: 2, py: 1.5,
          textAlign: 'left', bgcolor: 'transparent', border: 'none', cursor: 'pointer',
          '&:hover': { bgcolor: 'grey.50' },
        }}
      >
        <Chip label={piece.pieceType} color={color} size="small" sx={{ height: 20, fontSize: 10, fontWeight: 600, flexShrink: 0 }} />
        <Typography variant="body2" color="text.primary" sx={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {piece.label ?? piece.dimensionKey}
        </Typography>
        <Typography variant="caption" color="text.disabled" fontFamily="monospace" sx={{ flexShrink: 0 }}>{piece.policyLevel}</Typography>
        <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0 }}>{expanded ? '▲' : '▼'}</Typography>
      </Box>
      <Collapse in={expanded}>
        <Box sx={{ px: 2, pb: 1.5, bgcolor: 'grey.50', borderTop: 1, borderColor: 'divider' }}>
          <OdrlFragment fragment={piece.policyFragment} />
        </Box>
      </Collapse>
    </Box>
  );
}

function OdrlFragment({ fragment }: { fragment: Record<string, unknown> }) {
  const permissions  = (fragment.permission  as { action: string }[] | undefined) ?? [];
  const prohibitions = (fragment.prohibition as { action: string }[] | undefined) ?? [];
  const obligations  = (fragment.obligation  as { action: string }[] | undefined) ?? [];

  return (
    <Box sx={{ mt: 1, display: 'flex', flexDirection: 'column', gap: 0.75 }}>
      {permissions.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
          <Typography variant="caption" fontWeight={600} color="success.main" sx={{ minWidth: 70 }}>Permitted</Typography>
          <Typography variant="caption" color="text.secondary">{permissions.map(r => r.action).join(', ')}</Typography>
        </Box>
      )}
      {prohibitions.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
          <Typography variant="caption" fontWeight={600} color="error.main" sx={{ minWidth: 70 }}>Prohibited</Typography>
          <Typography variant="caption" color="text.secondary">{prohibitions.map(r => r.action).join(', ')}</Typography>
        </Box>
      )}
      {obligations.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
          <Typography variant="caption" fontWeight={600} color="warning.main" sx={{ minWidth: 70 }}>Obligated</Typography>
          <Typography variant="caption" color="text.secondary">{obligations.map(r => r.action).join(', ')}</Typography>
        </Box>
      )}
    </Box>
  );
}

function EvalResultBlock({ granted, decisions }: { granted: boolean; decisions: EvaluationDecision[] }) {
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, bgcolor: granted ? 'success.50' : 'error.50', borderColor: granted ? 'success.200' : 'error.200' }}>
      <Typography variant="body2" fontWeight={700} color={granted ? 'success.main' : 'error.main'} sx={{ mb: 1 }}>
        {granted ? '✓ Access Granted' : '✗ Access Denied'}
      </Typography>
      {decisions.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
          {decisions.map((d, i) => (
            <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Box component="code" sx={{ typography: 'caption', bgcolor: 'background.paper', border: 1, borderColor: 'divider', px: 0.75, py: 0.25, borderRadius: 0.5 }}>
                {d.action}
              </Box>
              <Typography variant="caption" color="text.disabled">→</Typography>
              <Typography variant="caption" color={d.delegated ? 'warning.main' : 'text.secondary'} sx={{ fontStyle: d.delegated ? 'italic' : 'normal' }}>
                {d.delegated ? `delegate (${d.result})` : d.result}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
}

function OverridePanel({ value, onChange, error, isPending, onSave, onClose }: {
  value: string; onChange: (v: string) => void; error: string;
  isPending: boolean; onSave: () => void; onClose: () => void;
}) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        Paste a complete ODRL Set document to override the assembled policy:
      </Typography>
      <TextField
        value={value}
        onChange={e => onChange(e.target.value)}
        multiline
        rows={10}
        size="small"
        fullWidth
        placeholder={'{\n  "@context": "http://www.w3.org/ns/odrl.jsonld",\n  "@type": "Set",\n  "uid": "...",\n  "permission": [...]\n}'}
        inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }}
        error={!!error}
        helperText={error}
      />
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button size="small" variant="contained" color="warning" disabled={isPending} onClick={onSave} sx={{ textTransform: 'none', fontSize: 11 }}>
          {isPending ? 'Saving…' : 'Save Override'}
        </Button>
        <Button size="small" variant="outlined" onClick={onClose} sx={{ textTransform: 'none', fontSize: 11 }}>Cancel</Button>
      </Box>
    </Box>
  );
}
