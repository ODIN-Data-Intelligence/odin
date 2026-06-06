import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { termsPolicyApi } from '@datacatalog/shared';
import type {
  TermsPolicyDetail,
  TermsClassificationRule,
  TermsRegulationRule,
  TermsRegulationObligation,
} from '@datacatalog/shared';
import { Button, Badge } from '@datacatalog/shared';

// ── Constants ─────────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<TermsPolicyDetail['status'], string> = {
  ACTIVE:   'bg-green-100 text-green-700',
  DRAFT:    'bg-blue-100 text-blue-700',
  ARCHIVED: 'bg-gray-100 text-gray-500',
};

const CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'HIGH_CONFIDENTIAL'];
const SIGNAL_TYPES = ['IRI_CONTAINS', 'KEYWORD', 'LOGICAL_TYPE'];

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseLines(text: string): string[] {
  return text.split('\n').map(s => s.trim()).filter(Boolean);
}

function joinLines(arr: string[]): string {
  return arr.join('\n');
}

function ListCell({ items }: { items: string[] }) {
  const [expanded, setExpanded] = useState(false);
  if (items.length === 0) return <span className="text-gray-400">—</span>;
  if (!expanded) {
    return (
      <button onClick={() => setExpanded(true)} className="text-xs text-blue-600 hover:underline">
        {items.length} item{items.length !== 1 ? 's' : ''}
      </button>
    );
  }
  return (
    <div>
      <ul className="text-xs text-gray-700 space-y-0.5">
        {items.map((item, i) => <li key={i} className="leading-snug">• {item}</li>)}
      </ul>
      <button onClick={() => setExpanded(false)} className="text-xs text-blue-600 hover:underline mt-1">
        collapse
      </button>
    </div>
  );
}

// ── Classification Rule Modal ─────────────────────────────────────────────────

interface ClassificationRuleModalProps {
  policyId: string;
  existing: TermsClassificationRule | null;
  onClose: () => void;
  onSaved: () => void;
}

function ClassificationRuleModal({ policyId, existing, onClose, onSaved }: ClassificationRuleModalProps) {
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
        rank: Number(rank),
        accessLevel,
        permissions: parseLines(permissions),
        prohibitions: parseLines(prohibitions),
        obligations: parseLines(obligations),
        odrlPermissions: parseLines(odrlPermissions),
        odrlProhibitions: parseLines(odrlProhibitions),
        odrlDuties: parseLines(odrlDuties),
      }),
    onSuccess: () => onSaved(),
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!classification || !rank || !accessLevel) {
      setError('Classification, rank, and access level are required.');
      return;
    }
    setError('');
    saveMut.mutate();
  }

  const textareaClass = 'w-full border border-gray-300 rounded px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none';

  return (
    <div className="fixed inset-0 bg-black/40 flex items-start justify-center z-50 overflow-y-auto py-8">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl p-6 mx-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {existing ? `Edit Rule: ${existing.classification}` : 'Add Classification Rule'}
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Classification *</label>
              <select
                value={classification}
                onChange={e => setClassification(e.target.value)}
                disabled={!!existing}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50"
              >
                <option value="">Select…</option>
                {CLASSIFICATIONS.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Rank *</label>
              <input
                type="number"
                value={rank}
                onChange={e => setRank(e.target.value)}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                min={0}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Access Level *</label>
              <input
                type="text"
                value={accessLevel}
                onChange={e => setAccessLevel(e.target.value)}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g. OPEN"
              />
            </div>
          </div>

          <p className="text-xs text-gray-500">Enter one item per line for list fields.</p>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Permissions</label>
              <textarea rows={5} value={permissions} onChange={e => setPermissions(e.target.value)} className={textareaClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Prohibitions</label>
              <textarea rows={5} value={prohibitions} onChange={e => setProhibitions(e.target.value)} className={textareaClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Obligations</label>
              <textarea rows={5} value={obligations} onChange={e => setObligations(e.target.value)} className={textareaClass} />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">ODRL Permissions</label>
              <textarea rows={4} value={odrlPermissions} onChange={e => setOdrlPermissions(e.target.value)} className={textareaClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">ODRL Prohibitions</label>
              <textarea rows={4} value={odrlProhibitions} onChange={e => setOdrlProhibitions(e.target.value)} className={textareaClass} />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">ODRL Duties</label>
              <textarea rows={4} value={odrlDuties} onChange={e => setOdrlDuties(e.target.value)} className={textareaClass} />
            </div>
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <Button type="submit" disabled={saveMut.isPending}>
              {saveMut.isPending ? 'Saving…' : 'Save Rule'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Regulation Rule Modal ─────────────────────────────────────────────────────

interface RegulationRuleModalProps {
  policyId: string;
  existing: TermsRegulationRule | null;
  onClose: () => void;
  onSaved: () => void;
}

function RegulationRuleModal({ policyId, existing, onClose, onSaved }: RegulationRuleModalProps) {
  const [signalType, setSignalType] = useState(existing?.signalType ?? '');
  const [pattern, setPattern] = useState(existing?.pattern ?? '');
  const [regulationName, setRegulationName] = useState(existing?.regulationName ?? '');
  const [signalLabel, setSignalLabel] = useState(existing?.signalLabel ?? '');
  const [error, setError] = useState('');

  const saveMut = useMutation({
    mutationFn: () => {
      const body = { signalType, pattern, regulationName, signalLabel };
      if (existing) {
        return termsPolicyApi.updateRegulationRule(policyId, existing.id, body);
      }
      return termsPolicyApi.addRegulationRule(policyId, body);
    },
    onSuccess: () => onSaved(),
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!signalType || !pattern || !regulationName || !signalLabel) {
      setError('All fields are required.');
      return;
    }
    setError('');
    saveMut.mutate();
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          {existing ? 'Edit Regulation Rule' : 'Add Regulation Rule'}
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Signal Type *</label>
            <select
              value={signalType}
              onChange={e => setSignalType(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Select…</option>
              {SIGNAL_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Pattern *</label>
            <input
              type="text"
              value={pattern}
              onChange={e => setPattern(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. fibo-fbc or gdpr"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Regulation Name *</label>
            <input
              type="text"
              value={regulationName}
              onChange={e => setRegulationName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. GDPR"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Signal Label *</label>
            <input
              type="text"
              value={signalLabel}
              onChange={e => setSignalLabel(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. FIBO Banking concepts detected"
            />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <Button type="submit" disabled={saveMut.isPending}>
              {saveMut.isPending ? 'Saving…' : 'Save Rule'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Regulation Obligation Modal ───────────────────────────────────────────────

interface RegulationObligationModalProps {
  policyId: string;
  onClose: () => void;
  onSaved: () => void;
}

function RegulationObligationModal({ policyId, onClose, onSaved }: RegulationObligationModalProps) {
  const [regulationName, setRegulationName] = useState('');
  const [obligation, setObligation] = useState('');
  const [odrlDuty, setOdrlDuty] = useState('');
  const [error, setError] = useState('');

  const saveMut = useMutation({
    mutationFn: () =>
      termsPolicyApi.addRegulationObligation(policyId, {
        regulationName,
        obligation,
        odrlDuty: odrlDuty.trim() || null,
      }),
    onSuccess: () => onSaved(),
    onError: () => setError('Failed to save. Please try again.'),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!regulationName || !obligation) {
      setError('Regulation name and obligation are required.');
      return;
    }
    setError('');
    saveMut.mutate();
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Add Regulation Obligation</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Regulation Name *</label>
            <input
              type="text"
              value={regulationName}
              onChange={e => setRegulationName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. GDPR"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Obligation *</label>
            <textarea
              value={obligation}
              onChange={e => setObligation(e.target.value)}
              rows={3}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
              placeholder="e.g. Conduct a DPIA before processing"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">ODRL Duty (optional)</label>
            <input
              type="text"
              value={odrlDuty}
              onChange={e => setOdrlDuty(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g. conductDPIA"
            />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
              Cancel
            </button>
            <Button type="submit" disabled={saveMut.isPending}>
              {saveMut.isPending ? 'Saving…' : 'Add Obligation'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Inline name/description editor ───────────────────────────────────────────

interface InlineEditProps {
  policyId: string;
  policy: TermsPolicyDetail;
  onSaved: () => void;
}

function InlineMetaEditor({ policyId, policy, onSaved }: InlineEditProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(policy.name);
  const [description, setDescription] = useState(policy.description ?? '');

  const saveMut = useMutation({
    mutationFn: () => termsPolicyApi.update(policyId, { name: name.trim(), description: description.trim() || undefined }),
    onSuccess: () => { setEditing(false); onSaved(); },
  });

  if (!editing) {
    return (
      <div className="flex items-start gap-2">
        <div>
          <p className="text-sm text-gray-500 mt-0.5">{policy.description || <span className="italic text-gray-400">No description</span>}</p>
        </div>
        <button
          onClick={() => setEditing(true)}
          className="ml-1 text-xs text-gray-400 hover:text-gray-600 mt-0.5"
          title="Edit name and description"
        >
          ✏
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2 max-w-lg">
      <input
        type="text"
        value={name}
        onChange={e => setName(e.target.value)}
        className="border border-gray-300 rounded px-3 py-1.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <textarea
        value={description}
        onChange={e => setDescription(e.target.value)}
        rows={2}
        className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
        placeholder="Description (optional)"
      />
      <div className="flex gap-2">
        <Button onClick={() => saveMut.mutate()} disabled={saveMut.isPending}>
          {saveMut.isPending ? 'Saving…' : 'Save'}
        </Button>
        <button onClick={() => setEditing(false)} className="text-sm text-gray-500 hover:text-gray-700">
          Cancel
        </button>
      </div>
    </div>
  );
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

type Tab = 'classification' | 'regulation' | 'obligations';

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TermsPolicyDetailPage() {
  const { tenant, policyId } = useParams<{ tenant: string; policyId: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [activeTab, setActiveTab] = useState<Tab>('classification');
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

  function invalidateDetail() {
    qc.invalidateQueries({ queryKey: ['terms-policy', policyId] });
  }

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

  if (isLoading) return <div className="p-8 text-sm text-gray-500">Loading…</div>;
  if (isError || !policy) return <div className="p-8 text-sm text-red-500">Failed to load policy.</div>;

  const isDraft = policy.status === 'DRAFT';

  const tabs: { id: Tab; label: string; count: number }[] = [
    { id: 'classification', label: 'Classification Rules', count: policy.classificationRules.length },
    { id: 'regulation',     label: 'Regulation Rules',     count: policy.regulationRules.length },
    { id: 'obligations',    label: 'Regulation Obligations', count: policy.regulationObligations.length },
  ];

  return (
    <div>
      {/* Header */}
      <div className="px-6 pt-6 pb-4 border-b border-gray-200 bg-white">
        <div className="mb-3">
          <Link
            to={`/${tenant}/admin/governance/terms-policies`}
            className="text-xs text-blue-600 hover:underline"
          >
            ← Back to Policies
          </Link>
        </div>
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-3 mb-1">
              <h1 className="text-xl font-semibold text-gray-900">{policy.name}</h1>
              <Badge label={policy.status} className={STATUS_COLORS[policy.status]} />
              <span className="text-xs text-gray-400">v{policy.version}</span>
            </div>
            {isDraft ? (
              <InlineMetaEditor policyId={policyId!} policy={policy} onSaved={invalidateDetail} />
            ) : (
              <p className="text-sm text-gray-500">{policy.description || <span className="italic text-gray-400">No description</span>}</p>
            )}
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            {isDraft && (
              <>
                <Button onClick={() => setConfirmActivate(true)}>Activate</Button>
                <button
                  onClick={() => setConfirmDelete(true)}
                  className="px-3 py-1.5 text-sm text-red-600 border border-red-300 rounded hover:bg-red-50"
                >
                  Delete
                </button>
              </>
            )}
            {!isDraft && (
              <button
                onClick={() => navigate(`/${tenant}/admin/governance/terms-policies`)}
                className="px-3 py-1.5 text-sm text-purple-600 border border-purple-300 rounded hover:bg-purple-50"
              >
                Clone to New Draft
              </button>
            )}
          </div>
        </div>

        {!isDraft && (
          <div className="mt-3 px-4 py-2 bg-amber-50 border border-amber-200 rounded text-sm text-amber-700">
            This policy is <strong>{policy.status}</strong> — clone it to make changes.
          </div>
        )}
      </div>

      {/* Tabs */}
      <div className="px-6 bg-white border-b border-gray-200">
        <div className="flex gap-1">
          {tabs.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`px-4 py-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {tab.label}
              <span className="ml-1.5 text-xs text-gray-400">({tab.count})</span>
            </button>
          ))}
        </div>
      </div>

      {/* Tab Content */}
      <div className="p-6">

        {/* ── Classification Rules ── */}
        {activeTab === 'classification' && (
          <div>
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-sm font-semibold text-gray-700">Classification Rules</h2>
              {isDraft && (
                <Button onClick={() => setEditClassRule('new')}>+ Add Rule</Button>
              )}
            </div>
            <div className="bg-white border border-gray-200 rounded-lg overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Classification</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Rank</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Access Level</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Permissions</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Prohibitions</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">Obligations</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">ODRL Perms</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">ODRL Prohibs</th>
                    <th className="px-3 py-2.5 text-left font-medium text-gray-600">ODRL Duties</th>
                    {isDraft && <th className="px-3 py-2.5" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {policy.classificationRules.length === 0 && (
                    <tr><td colSpan={isDraft ? 10 : 9} className="px-3 py-6 text-center text-gray-400">No rules defined.</td></tr>
                  )}
                  {policy.classificationRules.map(rule => (
                    <tr key={rule.id} className="hover:bg-gray-50 align-top">
                      <td className="px-3 py-2.5 font-medium text-gray-800">{rule.classification}</td>
                      <td className="px-3 py-2.5 text-gray-600">{rule.rank}</td>
                      <td className="px-3 py-2.5 text-gray-600">{rule.accessLevel}</td>
                      <td className="px-3 py-2.5"><ListCell items={rule.permissions} /></td>
                      <td className="px-3 py-2.5"><ListCell items={rule.prohibitions} /></td>
                      <td className="px-3 py-2.5"><ListCell items={rule.obligations} /></td>
                      <td className="px-3 py-2.5"><ListCell items={rule.odrlPermissions} /></td>
                      <td className="px-3 py-2.5"><ListCell items={rule.odrlProhibitions} /></td>
                      <td className="px-3 py-2.5"><ListCell items={rule.odrlDuties} /></td>
                      {isDraft && (
                        <td className="px-3 py-2.5">
                          <div className="flex gap-2">
                            <button onClick={() => setEditClassRule(rule)} className="text-xs text-blue-600 hover:underline">Edit</button>
                            <button onClick={() => setConfirmDeleteClassRule(rule)} className="text-xs text-red-500 hover:underline">Delete</button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Regulation Rules ── */}
        {activeTab === 'regulation' && (
          <div>
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-sm font-semibold text-gray-700">Regulation Detection Rules</h2>
              {isDraft && (
                <Button onClick={() => setEditRegRule('new')}>+ Add Rule</Button>
              )}
            </div>
            <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Signal Type</th>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Pattern</th>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Regulation Name</th>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Signal Label</th>
                    {isDraft && <th className="px-4 py-2.5" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {policy.regulationRules.length === 0 && (
                    <tr><td colSpan={isDraft ? 5 : 4} className="px-4 py-6 text-center text-gray-400">No rules defined.</td></tr>
                  )}
                  {policy.regulationRules.map(rule => (
                    <tr key={rule.id} className="hover:bg-gray-50">
                      <td className="px-4 py-2.5">
                        <span className="text-xs font-mono bg-gray-100 px-1.5 py-0.5 rounded">{rule.signalType}</span>
                      </td>
                      <td className="px-4 py-2.5 font-mono text-xs text-gray-700">{rule.pattern}</td>
                      <td className="px-4 py-2.5 font-medium text-gray-800">{rule.regulationName}</td>
                      <td className="px-4 py-2.5 text-gray-500 text-xs">{rule.signalLabel}</td>
                      {isDraft && (
                        <td className="px-4 py-2.5">
                          <div className="flex gap-2">
                            <button onClick={() => setEditRegRule(rule)} className="text-xs text-blue-600 hover:underline">Edit</button>
                            <button onClick={() => setConfirmDeleteRegRule(rule)} className="text-xs text-red-500 hover:underline">Delete</button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Regulation Obligations ── */}
        {activeTab === 'obligations' && (
          <div>
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-sm font-semibold text-gray-700">Regulation Obligations</h2>
              {isDraft && (
                <Button onClick={() => setShowAddObligation(true)}>+ Add Obligation</Button>
              )}
            </div>
            <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Regulation</th>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">Obligation</th>
                    <th className="px-4 py-2.5 text-left font-medium text-gray-600">ODRL Duty</th>
                    {isDraft && <th className="px-4 py-2.5" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {policy.regulationObligations.length === 0 && (
                    <tr><td colSpan={isDraft ? 4 : 3} className="px-4 py-6 text-center text-gray-400">No obligations defined.</td></tr>
                  )}
                  {policy.regulationObligations.map(obl => (
                    <tr key={obl.id} className="hover:bg-gray-50">
                      <td className="px-4 py-2.5 font-medium text-gray-800">{obl.regulationName}</td>
                      <td className="px-4 py-2.5 text-gray-600 text-xs leading-relaxed max-w-sm">{obl.obligation}</td>
                      <td className="px-4 py-2.5">
                        {obl.odrlDuty
                          ? <span className="text-xs font-mono bg-gray-100 px-1.5 py-0.5 rounded">{obl.odrlDuty}</span>
                          : <span className="text-gray-400">—</span>}
                      </td>
                      {isDraft && (
                        <td className="px-4 py-2.5">
                          <button onClick={() => setConfirmDeleteObligation(obl)} className="text-xs text-red-500 hover:underline">
                            Delete
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* ── Modals ── */}

      {editClassRule && editClassRule !== 'new' && (
        <ClassificationRuleModal
          policyId={policyId!}
          existing={editClassRule}
          onClose={() => setEditClassRule(null)}
          onSaved={() => { setEditClassRule(null); invalidateDetail(); }}
        />
      )}
      {editClassRule === 'new' && (
        <ClassificationRuleModal
          policyId={policyId!}
          existing={null}
          onClose={() => setEditClassRule(null)}
          onSaved={() => { setEditClassRule(null); invalidateDetail(); }}
        />
      )}

      {editRegRule && editRegRule !== 'new' && (
        <RegulationRuleModal
          policyId={policyId!}
          existing={editRegRule}
          onClose={() => setEditRegRule(null)}
          onSaved={() => { setEditRegRule(null); invalidateDetail(); }}
        />
      )}
      {editRegRule === 'new' && (
        <RegulationRuleModal
          policyId={policyId!}
          existing={null}
          onClose={() => setEditRegRule(null)}
          onSaved={() => { setEditRegRule(null); invalidateDetail(); }}
        />
      )}

      {showAddObligation && (
        <RegulationObligationModal
          policyId={policyId!}
          onClose={() => setShowAddObligation(false)}
          onSaved={() => { setShowAddObligation(false); invalidateDetail(); }}
        />
      )}

      {confirmActivate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Activate Policy</h2>
            <p className="text-sm text-gray-600 mb-4">
              Activate "<strong>{policy.name}</strong>"? The current ACTIVE policy will be archived.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmActivate(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <Button onClick={() => activateMut.mutate()} disabled={activateMut.isPending}>
                {activateMut.isPending ? 'Activating…' : 'Activate'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Delete Policy</h2>
            <p className="text-sm text-gray-600 mb-4">
              Delete "<strong>{policy.name}</strong>"? This cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDelete(false)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button
                onClick={() => deleteMut.mutate()}
                disabled={deleteMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deleteMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDeleteClassRule && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Delete Classification Rule</h2>
            <p className="text-sm text-gray-600 mb-4">
              Delete the rule for <strong>{confirmDeleteClassRule.classification}</strong>?
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDeleteClassRule(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button
                onClick={() => deleteClassRuleMut.mutate(confirmDeleteClassRule.classification)}
                disabled={deleteClassRuleMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deleteClassRuleMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDeleteRegRule && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Delete Regulation Rule</h2>
            <p className="text-sm text-gray-600 mb-4">
              Delete the <strong>{confirmDeleteRegRule.regulationName}</strong> detection rule?
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDeleteRegRule(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button
                onClick={() => deleteRegRuleMut.mutate(confirmDeleteRegRule.id)}
                disabled={deleteRegRuleMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deleteRegRuleMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDeleteObligation && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-2">Delete Obligation</h2>
            <p className="text-sm text-gray-600 mb-4">
              Delete the <strong>{confirmDeleteObligation.regulationName}</strong> obligation?
            </p>
            <div className="flex justify-end gap-3">
              <button onClick={() => setConfirmDeleteObligation(null)} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button
                onClick={() => deleteObligationMut.mutate(confirmDeleteObligation.id)}
                disabled={deleteObligationMut.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50"
              >
                {deleteObligationMut.isPending ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
