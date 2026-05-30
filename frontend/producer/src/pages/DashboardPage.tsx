import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { dashboardApi } from '@datacatalog/shared';
import type { ActivityProposal, ActivityChange } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';

export default function DashboardPage() {
  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: () => dashboardApi.getSummary(),
  });

  const { data: activity, isLoading: activityLoading } = useQuery({
    queryKey: ['dashboard-activity'],
    queryFn: () => dashboardApi.getActivity(),
  });

  const datasetCount     = summaryLoading ? '…' : (summary?.ownedDatasetCount ?? 0);
  const dataProductCount = summaryLoading ? '…' : (summary?.ownedDataProductCount ?? 0);
  const pendingRequests  = summary?.pendingTransferRequests ?? [];
  const proposals        = activity?.proposals ?? [];
  const changes          = activity?.changes ?? [];

  return (
    <div className="space-y-8 pb-12">
      <PageHeader title="Dashboard" description="Overview of your data estate" />

      {/* Stat cards */}
      <div className="px-6 grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard label="My Datasets"      value={datasetCount} />
        <StatCard label="My Data Products" value={dataProductCount} />
      </div>

      {/* Outstanding tasks */}
      <Section title="Outstanding Tasks">
        {pendingRequests.length === 0 ? (
          <Empty>No outstanding tasks.</Empty>
        ) : (
          <ul className="space-y-2">
            {pendingRequests.map(req => (
              <li key={req.id} className="bg-white border border-amber-200 rounded-lg p-4 flex items-center justify-between gap-4">
                <div>
                  <p className="text-sm font-medium text-gray-800">Ownership transfer request</p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    You have been proposed as the new owner of a dataset.
                    Requested {new Date(req.createdAt).toLocaleDateString()}.
                  </p>
                </div>
                <Link to={`/datasets/${req.datasetId}`} className="shrink-0 text-xs font-medium text-indigo-600 hover:text-indigo-800 underline">
                  Review
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Section>

      {/* My proposals */}
      <Section title="My Proposals">
        {activityLoading ? (
          <Empty>Loading…</Empty>
        ) : proposals.length === 0 ? (
          <Empty>No proposals yet.</Empty>
        ) : (
          <ul className="space-y-2">
            {proposals.map(p => <ProposalRow key={p.id} proposal={p} />)}
          </ul>
        )}
      </Section>

      {/* My changes */}
      <Section title="My Changes">
        {activityLoading ? (
          <Empty>Loading…</Empty>
        ) : changes.length === 0 ? (
          <Empty>No dataset changes yet.</Empty>
        ) : (
          <ul className="divide-y divide-gray-100 border border-gray-200 rounded-lg bg-white overflow-hidden">
            {changes.map(c => <ChangeRow key={c.id} change={c} />)}
          </ul>
        )}
      </Section>
    </div>
  );
}

// ── Proposal row ──────────────────────────────────────────────────────────────

function ProposalRow({ proposal: p }: { proposal: ActivityProposal }) {
  const date = new Date(p.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });
  const resolvedDate = p.resolvedAt
    ? new Date(p.resolvedAt).toLocaleDateString(undefined, { dateStyle: 'medium' })
    : null;

  const statusStyles: Record<string, string> = {
    PENDING:  'bg-amber-100 text-amber-700',
    APPROVED: 'bg-green-100 text-green-700',
    REJECTED: 'bg-red-100 text-red-700',
  };

  const roleLabel = p.role === 'PROPOSER' ? 'You proposed' : 'You were nominated';

  return (
    <li className="bg-white border border-gray-200 rounded-lg p-4 space-y-1.5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 flex-wrap">
            <Link
              to={`/datasets/${p.datasetId}`}
              className="text-sm font-medium text-gray-900 hover:text-indigo-600 hover:underline truncate"
            >
              {p.datasetTitle}
            </Link>
            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${statusStyles[p.status] ?? 'bg-gray-100 text-gray-600'}`}>
              {p.status}
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-0.5">
            {roleLabel} as owner · {date}
          </p>
        </div>
      </div>

      {p.status !== 'PENDING' && (
        <div className="text-xs text-gray-500 space-y-0.5 border-t border-gray-100 pt-1.5 mt-1.5">
          <p>
            <span className="font-medium text-gray-700">
              {p.status === 'APPROVED' ? 'Accepted' : 'Declined'}
            </span>
            {resolvedDate && <span> on {resolvedDate}</span>}
          </p>
          {p.note && (
            <p className="italic text-gray-600">"{p.note}"</p>
          )}
        </div>
      )}
    </li>
  );
}

// ── Change row ────────────────────────────────────────────────────────────────

const EVENT_LABELS: Record<string, string> = {
  CREATED:                   'Created',
  UPDATED:                   'Updated',
  DELETED:                   'Deleted',
  OWNER_ASSIGNED:            'Assigned owner',
  OWNER_TRANSFER_PROPOSED:   'Proposed transfer',
  OWNER_TRANSFER_APPROVED:   'Approved transfer',
  OWNER_TRANSFER_REJECTED:   'Rejected transfer',
};

function ChangeRow({ change: c }: { change: ActivityChange }) {
  const date = new Date(c.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });
  const label = EVENT_LABELS[c.eventType] ?? c.eventType.toLowerCase().replace(/_/g, ' ');

  return (
    <li className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-gray-50">
      <div className="min-w-0 flex-1">
        <Link
          to={`/datasets/${c.datasetId}`}
          className="text-sm font-medium text-gray-900 hover:text-indigo-600 hover:underline truncate block"
        >
          {c.datasetTitle}
        </Link>
        <p className="text-xs text-gray-500">{label}</p>
      </div>
      <p className="text-xs text-gray-400 shrink-0">{date}</p>
    </li>
  );
}

// ── Shared primitives ─────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="px-6">
      <h2 className="text-base font-semibold text-gray-800 mb-3">{title}</h2>
      {children}
    </div>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-gray-500">{children}</p>;
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-3xl font-semibold text-gray-900 mt-1">{value}</p>
    </div>
  );
}
