import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Alert from '@mui/material/Alert';
import EditIcon from '@mui/icons-material/Edit';
import { termsPolicyApi } from '@datacatalog/shared';
import type {
  TermsPolicyDetail,
  TermsClassificationRule,
  TermsRegulationRule,
  TermsRegulationObligation,
} from '@datacatalog/shared';

const STATUS_COLORS: Record<TermsPolicyDetail['status'], 'success' | 'info' | 'default'> = {
  ACTIVE:   'success',
  DRAFT:    'info',
  ARCHIVED: 'default',
};

const CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'HIGH_CONFIDENTIAL'];
const SIGNAL_TYPES = ['IRI_CONTAINS', 'KEYWORD', 'LOGICAL_TYPE'];

function parseLines(text: string): string[] {
  return text.split('\n').map(s => s.trim()).filter(Boolean);
}

function joinLines(arr: string[]): string {
  return arr.join('\n');
}

function ListCell({ items }: { items: string[] }) {
  const [expanded, setExpanded] = useState(false);
  if (items.length === 0) return <Typography variant="caption" color="text.disabled">—</Typography>;
  if (!expanded) {
    return (
      <Button variant="text" size="small" sx={{ textTransform: 'none', p: 0, fontSize: 12, minWidth: 0 }} onClick={() => setExpanded(true)}>
        {items.length} item{items.length !== 1 ? 's' : ''}
      </Button>
    );
  }
  return (
    <Box>
      <Box component="ul" sx={{ m: 0, pl: 1.5, listStyle: 'none' }}>
        {items.map((item, i) => (
          <Box component="li" key={i}>
            <Typography variant="caption" color="text.secondary">• {item}</Typography>
          </Box>
        ))}
      </Box>
      <Button variant="text" size="small" sx={{ textTransform: 'none', p: 0, fontSize: 12, minWidth: 0 }} onClick={() => setExpanded(false)}>
        collapse
      </Button>
    </Box>
  );
}

// ── Classification Rule Dialog ────────────────────────────────────────────────

interface ClassificationRuleDialogProps {
  policyId: string;
  existing: TermsClassificationRule | null;
  onClose: () => void;
  onSaved: () => void;
}

function ClassificationRuleDialog({ policyId, existing, onClose, onSaved }: ClassificationRuleDialogProps) {
  const [classification, setClassification] = useState(existing?.classification ?? '');
  const [rank, setRank] = useState(String(existing?.rank ?? ''));
  const [accessLevel, setAccessLevel] = useState(existing?.accessLevel ?? '');
  const [permissions, setPermissions] = useState(joinLines(existing?.permissions ?? []));
  const [prohibitions, setProhibitions] = useState(joinLines(existing?.prohibitions ?? []));
  const [obligations, setObligations] = useState(joinLines(existing?.obligations ?? []));
  const [odrlPermissions, setOdrlPermissions] = useState(joinLines(existing?.odrlPermissions ?? []));
  const [odrlProhibitions, setOdrlProhibitions] = useState(joinLines(existing?.odrlProhibitions ?? []));
  const [odrlDuties, setOdrlDuties] = useState(joinLines(existing?.odrlDuties ?? []));
  const [error, setError] = useState('');

  const saveMut = useMutation({
    mutationFn: () =>
      termsPolicyApi.upsertClassificationRule(policyId, classification, {
        rank: Number(rank), accessLevel,
        permissions: parseLines(permissions), prohibitions: parseLines(prohibitions), obligations: parseLines(obligations),
        odrlPermissions: parseLines(odrlPermissions), odrlProhibitions: parseLines(odrlProhibitions), odrlDuties: parseLines(odrlDuties),
      }),
    onSuccess: onSaved,
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!classification || !rank || !accessLevel) { setError('Classification, rank, and access level are required.'); return; }
    setError('');
    saveMut.mutate();
  }

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>{existing ? `Edit Rule: ${existing.classification}` : 'Add Classification Rule'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
            <FormControl size="small" required>
              <InputLabel>Classification</InputLabel>
              <Select label="Classification" value={classification} onChange={e => setClassification(e.target.value)} disabled={!!existing}>
                {CLASSIFICATIONS.map(c => <MenuItem key={c} value={c}>{c}</MenuItem>)}
              </Select>
            </FormControl>
            <TextField label="Rank" type="number" value={rank} onChange={e => setRank(e.target.value)} size="small" inputProps={{ min: 0 }} required />
            <TextField label="Access Level" value={accessLevel} onChange={e => setAccessLevel(e.target.value)} size="small" placeholder="e.g. OPEN" required />
          </Box>
          <Typography variant="caption" color="text.secondary">Enter one item per line for list fields.</Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
            <TextField label="Permissions" value={permissions} onChange={e => setPermissions(e.target.value)} multiline rows={5} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
            <TextField label="Prohibitions" value={prohibitions} onChange={e => setProhibitions(e.target.value)} multiline rows={5} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
            <TextField label="Obligations" value={obligations} onChange={e => setObligations(e.target.value)} multiline rows={5} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr 1fr' }, gap: 2 }}>
            <TextField label="ODRL Permissions" value={odrlPermissions} onChange={e => setOdrlPermissions(e.target.value)} multiline rows={4} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
            <TextField label="ODRL Prohibitions" value={odrlProhibitions} onChange={e => setOdrlProhibitions(e.target.value)} multiline rows={4} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
            <TextField label="ODRL Duties" value={odrlDuties} onChange={e => setOdrlDuties(e.target.value)} multiline rows={4} size="small" inputProps={{ style: { fontFamily: 'monospace', fontSize: 12 } }} />
          </Box>
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
            {saveMut.isPending ? 'Saving…' : 'Save Rule'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

// ── Regulation Rule Dialog ────────────────────────────────────────────────────

interface RegulationRuleDialogProps {
  policyId: string;
  existing: TermsRegulationRule | null;
  onClose: () => void;
  onSaved: () => void;
}

function RegulationRuleDialog({ policyId, existing, onClose, onSaved }: RegulationRuleDialogProps) {
  const [signalType, setSignalType] = useState(existing?.signalType ?? '');
  const [pattern, setPattern] = useState(existing?.pattern ?? '');
  const [regulationName, setRegulationName] = useState(existing?.regulationName ?? '');
  const [signalLabel, setSignalLabel] = useState(existing?.signalLabel ?? '');
  const [error, setError] = useState('');

  const saveMut = useMutation({
    mutationFn: () => {
      const body = { signalType, pattern, regulationName, signalLabel };
      return existing
        ? termsPolicyApi.updateRegulationRule(policyId, existing.id, body)
        : termsPolicyApi.addRegulationRule(policyId, body);
    },
    onSuccess: onSaved,
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!signalType || !pattern || !regulationName || !signalLabel) { setError('All fields are required.'); return; }
    setError('');
    saveMut.mutate();
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>{existing ? 'Edit Regulation Rule' : 'Add Regulation Rule'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <FormControl size="small" required>
            <InputLabel>Signal Type</InputLabel>
            <Select label="Signal Type" value={signalType} onChange={e => setSignalType(e.target.value)}>
              {SIGNAL_TYPES.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField label="Pattern" value={pattern} onChange={e => setPattern(e.target.value)} size="small" placeholder="e.g. fibo-fbc or gdpr" required fullWidth />
          <TextField label="Regulation Name" value={regulationName} onChange={e => setRegulationName(e.target.value)} size="small" placeholder="e.g. GDPR" required fullWidth />
          <TextField label="Signal Label" value={signalLabel} onChange={e => setSignalLabel(e.target.value)} size="small" placeholder="e.g. FIBO Banking concepts detected" required fullWidth />
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
            {saveMut.isPending ? 'Saving…' : 'Save Rule'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

// ── Regulation Obligation Dialog ──────────────────────────────────────────────

function RegulationObligationDialog({ policyId, onClose, onSaved }: { policyId: string; onClose: () => void; onSaved: () => void }) {
  const [regulationName, setRegulationName] = useState('');
  const [obligation, setObligation] = useState('');
  const [odrlDuty, setOdrlDuty] = useState('');
  const [error, setError] = useState('');

  const saveMut = useMutation({
    mutationFn: () => termsPolicyApi.addRegulationObligation(policyId, { regulationName, obligation, odrlDuty: odrlDuty.trim() || null }),
    onSuccess: onSaved,
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!regulationName || !obligation) { setError('Regulation name and obligation are required.'); return; }
    setError('');
    saveMut.mutate();
  }

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Add Regulation Obligation</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <TextField label="Regulation Name" value={regulationName} onChange={e => setRegulationName(e.target.value)} size="small" placeholder="e.g. GDPR" required fullWidth />
          <TextField label="Obligation" value={obligation} onChange={e => setObligation(e.target.value)} size="small" multiline rows={3} placeholder="e.g. Conduct a DPIA before processing" required fullWidth />
          <TextField label="ODRL Duty (optional)" value={odrlDuty} onChange={e => setOdrlDuty(e.target.value)} size="small" placeholder="e.g. conductDPIA" fullWidth />
          {error && <Alert severity="error">{error}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
            {saveMut.isPending ? 'Saving…' : 'Add Obligation'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

// ── Inline Meta Editor ────────────────────────────────────────────────────────

function InlineMetaEditor({ policyId, policy, onSaved }: { policyId: string; policy: TermsPolicyDetail; onSaved: () => void }) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(policy.name);
  const [description, setDescription] = useState(policy.description ?? '');

  const saveMut = useMutation({
    mutationFn: () => termsPolicyApi.update(policyId, { name: name.trim(), description: description.trim() || undefined }),
    onSuccess: () => { setEditing(false); onSaved(); },
  });

  if (!editing) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
        <Typography variant="body2" color="text.secondary">{policy.description || <em>No description</em>}</Typography>
        <IconButton size="small" onClick={() => setEditing(true)} sx={{ mt: '-2px' }}>
          <EditIcon sx={{ fontSize: 14 }} />
        </IconButton>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, maxWidth: 480 }}>
      <TextField value={name} onChange={e => setName(e.target.value)} size="small" inputProps={{ style: { fontWeight: 600 } }} fullWidth />
      <TextField value={description} onChange={e => setDescription(e.target.value)} size="small" multiline rows={2} placeholder="Description (optional)" fullWidth />
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button variant="contained" size="small" onClick={() => saveMut.mutate()} disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
          {saveMut.isPending ? 'Saving…' : 'Save'}
        </Button>
        <Button size="small" onClick={() => setEditing(false)} sx={{ textTransform: 'none' }}>Cancel</Button>
      </Box>
    </Box>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type TabId = 'classification' | 'regulation' | 'obligations';

function ConfirmDialog({ open, title, children, confirmLabel, confirmColor, onClose, onConfirm, isPending }: {
  open: boolean; title: string; children: React.ReactNode; confirmLabel: string;
  confirmColor?: 'error' | 'success' | 'primary'; onClose: () => void; onConfirm: () => void; isPending?: boolean;
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>{children}</DialogContent>
      <DialogActions>
        <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
        <Button variant="contained" color={confirmColor ?? 'primary'} disabled={isPending} onClick={onConfirm} sx={{ textTransform: 'none' }}>
          {isPending ? `${confirmLabel.replace(/^(\w)/, c => c.toUpperCase())}ing…` : confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function TermsPolicyDetailPage() {
  const { tenant, policyId } = useParams<{ tenant: string; policyId: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [activeTab, setActiveTab] = useState<TabId>('classification');
  const [editClassRule, setEditClassRule] = useState<TermsClassificationRule | null | 'new'>(null);
  const [editRegRule, setEditRegRule] = useState<TermsRegulationRule | null | 'new'>(null);
  const [showAddObligation, setShowAddObligation] = useState(false);
  const [confirmActivate, setConfirmActivate] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmDeleteClassRule, setConfirmDeleteClassRule] = useState<TermsClassificationRule | null>(null);
  const [confirmDeleteRegRule, setConfirmDeleteRegRule] = useState<TermsRegulationRule | null>(null);
  const [confirmDeleteObligation, setConfirmDeleteObligation] = useState<TermsRegulationObligation | null>(null);

  const { data: policy, isLoading, isError } = useQuery({
    queryKey: ['terms-policy', policyId],
    queryFn: () => termsPolicyApi.get(policyId!),
    enabled: !!policyId,
  });

  function invalidateDetail() { qc.invalidateQueries({ queryKey: ['terms-policy', policyId] }); }

  const activateMut = useMutation({
    mutationFn: () => termsPolicyApi.activate(policyId!),
    onSuccess: () => {
      setConfirmActivate(false);
      qc.invalidateQueries({ queryKey: ['terms-policies'] });
      qc.invalidateQueries({ queryKey: ['terms-policy', policyId] });
    },
  });

  const deleteMut = useMutation({
    mutationFn: () => termsPolicyApi.delete(policyId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['terms-policies'] });
      navigate(`/${tenant}/admin/governance/terms-policies`);
    },
  });

  const deleteClassRuleMut = useMutation({
    mutationFn: (classification: string) => termsPolicyApi.deleteClassificationRule(policyId!, classification),
    onSuccess: () => { setConfirmDeleteClassRule(null); invalidateDetail(); },
  });

  const deleteRegRuleMut = useMutation({
    mutationFn: (ruleId: string) => termsPolicyApi.deleteRegulationRule(policyId!, ruleId),
    onSuccess: () => { setConfirmDeleteRegRule(null); invalidateDetail(); },
  });

  const deleteObligationMut = useMutation({
    mutationFn: (oblId: string) => termsPolicyApi.deleteRegulationObligation(policyId!, oblId),
    onSuccess: () => { setConfirmDeleteObligation(null); invalidateDetail(); },
  });

  if (isLoading) return <Typography variant="body2" color="text.secondary" sx={{ p: 4 }}>Loading…</Typography>;
  if (isError || !policy) return <Alert severity="error" sx={{ m: 3 }}>Failed to load policy.</Alert>;

  const isDraft = policy.status === 'DRAFT';

  const tabs: { id: TabId; label: string; count: number }[] = [
    { id: 'classification', label: 'Classification Rules', count: policy.classificationRules.length },
    { id: 'regulation',     label: 'Regulation Rules',     count: policy.regulationRules.length },
    { id: 'obligations',    label: 'Regulation Obligations', count: policy.regulationObligations.length },
  ];

  return (
    <Box>
      {/* Header */}
      <Box sx={{ px: 3, pt: 3, pb: 2, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
        <Typography
          component={Link}
          to={`/${tenant}/admin/governance/terms-policies`}
          variant="caption"
          color="primary"
          sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
        >
          ← Back to Policies
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2, mt: 1.5 }}>
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.5 }}>
              <Typography variant="h6" fontWeight={600}>{policy.name}</Typography>
              <Chip label={policy.status} color={STATUS_COLORS[policy.status]} size="small" sx={{ height: 20, fontSize: 11 }} />
              <Typography variant="caption" color="text.disabled">v{policy.version}</Typography>
            </Box>
            {isDraft
              ? <InlineMetaEditor policyId={policyId!} policy={policy} onSaved={invalidateDetail} />
              : <Typography variant="body2" color="text.secondary">{policy.description || <em>No description</em>}</Typography>}
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
            {isDraft && (
              <>
                <Button variant="contained" size="small" onClick={() => setConfirmActivate(true)} sx={{ textTransform: 'none' }}>Activate</Button>
                <Button variant="outlined" color="error" size="small" onClick={() => setConfirmDelete(true)} sx={{ textTransform: 'none' }}>Delete</Button>
              </>
            )}
            {!isDraft && (
              <Button variant="outlined" color="secondary" size="small" onClick={() => navigate(`/${tenant}/admin/governance/terms-policies`)} sx={{ textTransform: 'none' }}>
                Clone to New Draft
              </Button>
            )}
          </Box>
        </Box>
        {!isDraft && (
          <Alert severity="warning" sx={{ mt: 1.5 }}>
            This policy is <strong>{policy.status}</strong> — clone it to make changes.
          </Alert>
        )}
      </Box>

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper', px: 1 }}>
        <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v as TabId)}>
          {tabs.map(tab => (
            <Tab key={tab.id} value={tab.id} label={`${tab.label} (${tab.count})`} sx={{ textTransform: 'none', fontSize: 13 }} />
          ))}
        </Tabs>
      </Box>

      {/* Tab Content */}
      <Box sx={{ p: 3 }}>
        {/* ── Classification Rules ── */}
        {activeTab === 'classification' && (
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle2" fontWeight={600}>Classification Rules</Typography>
              {isDraft && (
                <Button variant="contained" size="small" onClick={() => setEditClassRule('new')} sx={{ textTransform: 'none' }}>+ Add Rule</Button>
              )}
            </Box>
            <Paper variant="outlined" sx={{ overflow: 'auto' }}>
              <Table size="small" sx={{ minWidth: 980 }}>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'grey.50' }}>
                    {['Classification', 'Rank', 'Access Level', 'Permissions', 'Prohibitions', 'Obligations', 'ODRL Perms', 'ODRL Prohibs', 'ODRL Duties'].map(h => (
                      <TableCell key={h} sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase', whiteSpace: 'nowrap' }}>{h}</TableCell>
                    ))}
                    {isDraft && <TableCell />}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {policy.classificationRules.length === 0 && (
                    <TableRow><TableCell colSpan={isDraft ? 10 : 9} sx={{ textAlign: 'center', py: 4, color: 'text.disabled' }}>No rules defined.</TableCell></TableRow>
                  )}
                  {policy.classificationRules.map(rule => (
                    <TableRow key={rule.id} hover sx={{ verticalAlign: 'top' }}>
                      <TableCell><Typography variant="body2" fontWeight={600}>{rule.classification}</Typography></TableCell>
                      <TableCell><Typography variant="body2" color="text.secondary">{rule.rank}</Typography></TableCell>
                      <TableCell><Typography variant="body2" color="text.secondary">{rule.accessLevel}</Typography></TableCell>
                      <TableCell><ListCell items={rule.permissions} /></TableCell>
                      <TableCell><ListCell items={rule.prohibitions} /></TableCell>
                      <TableCell><ListCell items={rule.obligations} /></TableCell>
                      <TableCell><ListCell items={rule.odrlPermissions} /></TableCell>
                      <TableCell><ListCell items={rule.odrlProhibitions} /></TableCell>
                      <TableCell><ListCell items={rule.odrlDuties} /></TableCell>
                      {isDraft && (
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 0.5 }}>
                            <Button size="small" sx={{ textTransform: 'none', fontSize: 12, p: 0, minWidth: 0 }} onClick={() => setEditClassRule(rule)}>Edit</Button>
                            <Button size="small" color="error" sx={{ textTransform: 'none', fontSize: 12, p: 0, minWidth: 0 }} onClick={() => setConfirmDeleteClassRule(rule)}>Delete</Button>
                          </Box>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Box>
        )}

        {/* ── Regulation Rules ── */}
        {activeTab === 'regulation' && (
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle2" fontWeight={600}>Regulation Detection Rules</Typography>
              {isDraft && (
                <Button variant="contained" size="small" onClick={() => setEditRegRule('new')} sx={{ textTransform: 'none' }}>+ Add Rule</Button>
              )}
            </Box>
            <Paper variant="outlined" sx={{ overflow: 'auto' }}>
              <Table size="small" sx={{ minWidth: 720 }}>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'grey.50' }}>
                    {['Signal Type', 'Pattern', 'Regulation Name', 'Signal Label'].map(h => (
                      <TableCell key={h} sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>{h}</TableCell>
                    ))}
                    {isDraft && <TableCell />}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {policy.regulationRules.length === 0 && (
                    <TableRow><TableCell colSpan={isDraft ? 5 : 4} sx={{ textAlign: 'center', py: 4, color: 'text.disabled' }}>No rules defined.</TableCell></TableRow>
                  )}
                  {policy.regulationRules.map(rule => (
                    <TableRow key={rule.id} hover>
                      <TableCell>
                        <Chip label={rule.signalType} size="small" sx={{ height: 18, fontSize: 11, fontFamily: 'monospace' }} />
                      </TableCell>
                      <TableCell><Typography variant="caption" fontFamily="monospace" color="text.secondary">{rule.pattern}</Typography></TableCell>
                      <TableCell><Typography variant="body2" fontWeight={600}>{rule.regulationName}</Typography></TableCell>
                      <TableCell><Typography variant="caption" color="text.secondary">{rule.signalLabel}</Typography></TableCell>
                      {isDraft && (
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 0.5 }}>
                            <Button size="small" sx={{ textTransform: 'none', fontSize: 12, p: 0, minWidth: 0 }} onClick={() => setEditRegRule(rule)}>Edit</Button>
                            <Button size="small" color="error" sx={{ textTransform: 'none', fontSize: 12, p: 0, minWidth: 0 }} onClick={() => setConfirmDeleteRegRule(rule)}>Delete</Button>
                          </Box>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Box>
        )}

        {/* ── Regulation Obligations ── */}
        {activeTab === 'obligations' && (
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Typography variant="subtitle2" fontWeight={600}>Regulation Obligations</Typography>
              {isDraft && (
                <Button variant="contained" size="small" onClick={() => setShowAddObligation(true)} sx={{ textTransform: 'none' }}>+ Add Obligation</Button>
              )}
            </Box>
            <Paper variant="outlined" sx={{ overflow: 'auto' }}>
              <Table size="small" sx={{ minWidth: 720 }}>
                <TableHead>
                  <TableRow sx={{ bgcolor: 'grey.50' }}>
                    {['Regulation', 'Obligation', 'ODRL Duty'].map(h => (
                      <TableCell key={h} sx={{ fontWeight: 600, fontSize: 11, textTransform: 'uppercase' }}>{h}</TableCell>
                    ))}
                    {isDraft && <TableCell />}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {policy.regulationObligations.length === 0 && (
                    <TableRow><TableCell colSpan={isDraft ? 4 : 3} sx={{ textAlign: 'center', py: 4, color: 'text.disabled' }}>No obligations defined.</TableCell></TableRow>
                  )}
                  {policy.regulationObligations.map(obl => (
                    <TableRow key={obl.id} hover>
                      <TableCell><Typography variant="body2" fontWeight={600}>{obl.regulationName}</Typography></TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.5 }}>{obl.obligation}</Typography>
                      </TableCell>
                      <TableCell>
                        {obl.odrlDuty
                          ? <Chip label={obl.odrlDuty} size="small" sx={{ height: 18, fontSize: 11, fontFamily: 'monospace' }} />
                          : <Typography variant="caption" color="text.disabled">—</Typography>}
                      </TableCell>
                      {isDraft && (
                        <TableCell>
                          <Button size="small" color="error" sx={{ textTransform: 'none', fontSize: 12, p: 0, minWidth: 0 }} onClick={() => setConfirmDeleteObligation(obl)}>Delete</Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
          </Box>
        )}
      </Box>

      {/* ── Dialogs ── */}

      {editClassRule !== null && (
        <ClassificationRuleDialog
          policyId={policyId!}
          existing={editClassRule === 'new' ? null : editClassRule}
          onClose={() => setEditClassRule(null)}
          onSaved={() => { setEditClassRule(null); invalidateDetail(); }}
        />
      )}

      {editRegRule !== null && (
        <RegulationRuleDialog
          policyId={policyId!}
          existing={editRegRule === 'new' ? null : editRegRule}
          onClose={() => setEditRegRule(null)}
          onSaved={() => { setEditRegRule(null); invalidateDetail(); }}
        />
      )}

      {showAddObligation && (
        <RegulationObligationDialog
          policyId={policyId!}
          onClose={() => setShowAddObligation(false)}
          onSaved={() => { setShowAddObligation(false); invalidateDetail(); }}
        />
      )}

      <ConfirmDialog open={confirmActivate} title="Activate Policy" confirmLabel="Activate" confirmColor="success" isPending={activateMut.isPending} onClose={() => setConfirmActivate(false)} onConfirm={() => activateMut.mutate()}>
        <Typography variant="body2" color="text.secondary">
          Activate "<strong>{policy.name}</strong>"? The current ACTIVE policy will be archived.
        </Typography>
      </ConfirmDialog>

      <ConfirmDialog open={confirmDelete} title="Delete Policy" confirmLabel="Delete" confirmColor="error" isPending={deleteMut.isPending} onClose={() => setConfirmDelete(false)} onConfirm={() => deleteMut.mutate()}>
        <Typography variant="body2" color="text.secondary">
          Delete "<strong>{policy.name}</strong>"? This cannot be undone.
        </Typography>
      </ConfirmDialog>

      <ConfirmDialog open={!!confirmDeleteClassRule} title="Delete Classification Rule" confirmLabel="Delete" confirmColor="error" isPending={deleteClassRuleMut.isPending} onClose={() => setConfirmDeleteClassRule(null)} onConfirm={() => confirmDeleteClassRule && deleteClassRuleMut.mutate(confirmDeleteClassRule.classification)}>
        <Typography variant="body2" color="text.secondary">
          Delete the rule for <strong>{confirmDeleteClassRule?.classification}</strong>?
        </Typography>
      </ConfirmDialog>

      <ConfirmDialog open={!!confirmDeleteRegRule} title="Delete Regulation Rule" confirmLabel="Delete" confirmColor="error" isPending={deleteRegRuleMut.isPending} onClose={() => setConfirmDeleteRegRule(null)} onConfirm={() => confirmDeleteRegRule && deleteRegRuleMut.mutate(confirmDeleteRegRule.id)}>
        <Typography variant="body2" color="text.secondary">
          Delete the <strong>{confirmDeleteRegRule?.regulationName}</strong> detection rule?
        </Typography>
      </ConfirmDialog>

      <ConfirmDialog open={!!confirmDeleteObligation} title="Delete Obligation" confirmLabel="Delete" confirmColor="error" isPending={deleteObligationMut.isPending} onClose={() => setConfirmDeleteObligation(null)} onConfirm={() => confirmDeleteObligation && deleteObligationMut.mutate(confirmDeleteObligation.id)}>
        <Typography variant="body2" color="text.secondary">
          Delete the <strong>{confirmDeleteObligation?.regulationName}</strong> obligation?
        </Typography>
      </ConfirmDialog>
    </Box>
  );
}
