import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { datasetApi, userApi } from '@datacatalog/shared';
import type { Dataset, OwnershipProposal } from '@datacatalog/shared';
import { useAuthStore } from '../../store/authStore';
import Button from '../ui/Button';
import OwnershipTransferModal from './OwnershipTransferModal';
import AssignOwnerModal from './AssignOwnerModal';

interface OwnershipPanelProps {
  dataset: Dataset;
  onUpdated: (dataset: Dataset) => void;
}

export default function OwnershipPanel({ dataset, onUpdated }: OwnershipPanelProps) {
  const qc = useQueryClient();
  const { userId, hasRole, hasAnyRole } = useAuthStore();
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [showAssignModal,   setShowAssignModal]   = useState(false);
  const [showProposeModal,  setShowProposeModal]  = useState(false);

  // Fetch users: needed to display the current owner's name and to power the admin assign picker.
  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
  });

  // Resolve the current user and the dataset owner from the users list.
  // Match by keycloakUserId (authoritative) with local id as fallback for legacy-assigned datasets.
  const currentLocalUser = users.find(u => u.keycloakUserId === userId);
  const owner = users.find(u =>
    (u.keycloakUserId && u.keycloakUserId === dataset.ownerId) ||
    u.id === dataset.ownerId
  );

  // Pending proposal
  const { data: pendingProposal, refetch: refetchProposal } = useQuery({
    queryKey: ['pending-proposal', dataset.id],
    queryFn: () => datasetApi.getPendingProposal(dataset.id),
  });

  const assignMutation = useMutation({
    mutationFn: (assignUserId: string) => datasetApi.assignOwner(dataset.id, assignUserId),
    onSuccess: (updated) => {
      onUpdated(updated);
      qc.invalidateQueries({ queryKey: ['pending-proposal', dataset.id] });
    },
  });

  const approveMutation = useMutation({
    mutationFn: ({ proposalId, note }: { proposalId: string; note?: string }) =>
      datasetApi.approveTransfer(dataset.id, proposalId, note),
    onSuccess: (updated) => {
      onUpdated(updated);
      qc.invalidateQueries({ queryKey: ['pending-proposal', dataset.id] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ proposalId, note }: { proposalId: string; note?: string }) =>
      datasetApi.rejectTransfer(dataset.id, proposalId, note),
    onSuccess: () => refetchProposal(),
  });

  // Role-based permissions.
  // isCurrentOwner matches against Keycloak UUID (new data) or local catalog UUID (legacy data).
  const canDirectAssign = hasRole('administrator');
  const canProposeOwner = hasAnyRole(['data-governance', 'data-steward']) && !canDirectAssign;
  const canClaimSelf    = hasRole('data-owner') && !!userId && !canDirectAssign && !canProposeOwner;
  const isCurrentOwner  = !!dataset.ownerId && (
    dataset.ownerId === userId ||
    (!!currentLocalUser && dataset.ownerId === currentLocalUser.id)
  );
  // Nominated user can accept their own proposal on an unowned dataset.
  const isProposedOwner = !!pendingProposal && pendingProposal.proposedOwnerId === userId;

  return (
    <div className="max-w-2xl space-y-6">
      {/* Owner section */}
      <Section title="Data Owner">
        {!dataset.ownerId ? (
          <UnownedState
            canDirectAssign={canDirectAssign}
            canProposeOwner={canProposeOwner}
            canClaimSelf={canClaimSelf}
            onOpenPicker={() => setShowAssignModal(true)}
            onOpenProposalPicker={() => setShowProposeModal(true)}
            onClaimSelf={() => userId && assignMutation.mutate(userId)}
            isPending={assignMutation.isPending}
            error={assignMutation.isError}
          />
        ) : (
          <OwnedState
            ownerId={dataset.ownerId}
            ownerEmail={owner?.email}
            ownerName={owner ? `${owner.firstName ?? ''} ${owner.lastName ?? ''}`.trim() : undefined}
            isCurrentOwner={isCurrentOwner}
            onTransfer={() => setShowTransferModal(true)}
          />
        )}
      </Section>

      {/* Pending proposal section */}
      {pendingProposal && (
        <Section title={dataset.ownerId ? 'Pending Transfer Proposal' : 'Pending Ownership Nomination'}>
          <ProposalCard
            proposal={pendingProposal}
            isUnowned={!dataset.ownerId}
            canAct={dataset.ownerId
              ? (isCurrentOwner || canDirectAssign)
              : (isCurrentOwner || isProposedOwner || canDirectAssign)}
            onApprove={(note) => approveMutation.mutate({ proposalId: pendingProposal.id, note })}
            onReject={(note) => rejectMutation.mutate({ proposalId: pendingProposal.id, note })}
            isApprovePending={approveMutation.isPending}
            isRejectPending={rejectMutation.isPending}
          />
        </Section>
      )}

      {showAssignModal && (
        <AssignOwnerModal
          datasetId={dataset.id}
          onSuccess={(updated) => {
            setShowAssignModal(false);
            onUpdated(updated);
          }}
          onCancel={() => setShowAssignModal(false)}
        />
      )}

      {showTransferModal && (
        <OwnershipTransferModal
          datasetId={dataset.id}
          onSuccess={(proposal) => {
            setShowTransferModal(false);
            qc.setQueryData(['pending-proposal', dataset.id], proposal);
          }}
          onCancel={() => setShowTransferModal(false)}
        />
      )}

      {showProposeModal && (
        <OwnershipTransferModal
          datasetId={dataset.id}
          title="Nominate Data Owner"
          description="Select the user you are proposing as data owner. They will be notified and can accept the nomination."
          submitLabel="Nominate"
          onSuccess={(proposal) => {
            setShowProposeModal(false);
            qc.setQueryData(['pending-proposal', dataset.id], proposal);
          }}
          onCancel={() => setShowProposeModal(false)}
        />
      )}
    </div>
  );
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-gray-800 mb-3">{title}</h3>
      {children}
    </div>
  );
}

function UnownedState({
  canDirectAssign,
  canProposeOwner,
  canClaimSelf,
  onOpenPicker,
  onOpenProposalPicker,
  onClaimSelf,
  isPending,
  error,
}: {
  canDirectAssign: boolean;
  canProposeOwner: boolean;
  canClaimSelf: boolean;
  onOpenPicker: () => void;
  onOpenProposalPicker: () => void;
  onClaimSelf: () => void;
  isPending: boolean;
  error: boolean;
}) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-lg border border-dashed border-gray-300 bg-gray-50">
      <div className="flex-1">
        <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
          Unowned
        </span>
        <p className="text-sm text-gray-500 mt-1">
          No data owner assigned. Without an owner, metadata may become stale.
        </p>
        {error && <p className="text-xs text-red-600 mt-1">Failed to assign owner. Please try again.</p>}
      </div>
      <div className="flex flex-col gap-2 shrink-0">
        {canDirectAssign && (
          <Button size="sm" onClick={onOpenPicker} disabled={isPending}>
            Assign Owner
          </Button>
        )}
        {canProposeOwner && (
          <Button size="sm" variant="secondary" onClick={onOpenProposalPicker} disabled={isPending}>
            Propose Owner
          </Button>
        )}
        {canClaimSelf && (
          <Button size="sm" variant="secondary" onClick={onClaimSelf} disabled={isPending}>
            {isPending ? 'Claiming…' : 'Claim as Owner'}
          </Button>
        )}
      </div>
    </div>
  );
}

function OwnedState({
  ownerId,
  ownerEmail,
  ownerName,
  isCurrentOwner,
  onTransfer,
}: {
  ownerId: string;
  ownerEmail?: string;
  ownerName?: string;
  isCurrentOwner: boolean;
  onTransfer: () => void;
}) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-lg border border-gray-200 bg-white">
      <div className="w-9 h-9 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-sm font-semibold flex-shrink-0">
        {ownerName ? ownerName[0].toUpperCase() : ownerEmail?.[0].toUpperCase() ?? '?'}
      </div>
      <div className="flex-1 min-w-0">
        {ownerName && <p className="text-sm font-medium text-gray-900 truncate">{ownerName}</p>}
        <p className="text-sm text-gray-500 truncate">{ownerEmail ?? ownerId}</p>
        {isCurrentOwner && (
          <span className="text-xs text-blue-600 font-medium">You are the owner</span>
        )}
      </div>
      <Button size="sm" variant="secondary" onClick={onTransfer}>
        {isCurrentOwner ? 'Transfer ownership' : 'Propose transfer'}
      </Button>
    </div>
  );
}

function ProposalCard({
  proposal,
  isUnowned,
  canAct,
  onApprove,
  onReject,
  isApprovePending,
  isRejectPending,
}: {
  proposal: OwnershipProposal;
  isUnowned: boolean;
  canAct: boolean;
  onApprove: (note?: string) => void;
  onReject: (note?: string) => void;
  isApprovePending: boolean;
  isRejectPending: boolean;
}) {
  const [action, setAction] = useState<'approve' | 'decline' | null>(null);
  const [note, setNote] = useState('');
  const created = new Date(proposal.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });
  const isPending = isApprovePending || isRejectPending;

  function confirm() {
    if (action === 'approve') onApprove(note.trim() || undefined);
    else onReject(note.trim() || undefined);
  }

  function cancel() { setAction(null); setNote(''); }

  return (
    <div className="p-4 rounded-lg border border-amber-200 bg-amber-50 space-y-3">
      <div>
        <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700">
          PENDING
        </span>
        <p className="text-sm text-gray-700 mt-1.5">
          {isUnowned ? 'Nominated owner: ' : 'Transfer to '}
          <span className="font-medium font-mono text-xs">{proposal.proposedOwnerId}</span>
        </p>
        <p className="text-xs text-gray-500 mt-0.5">
          Proposed by <span className="font-mono">{proposal.proposedById}</span> on {created}
        </p>
      </div>

      {canAct && !action && (
        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => setAction('approve')} disabled={isPending}>
            {isUnowned ? 'Accept' : 'Approve'}
          </Button>
          <Button size="sm" variant="danger" onClick={() => setAction('decline')} disabled={isPending}>
            Decline
          </Button>
        </div>
      )}

      {canAct && action && (
        <div className="space-y-2">
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder={`Optional note for ${action === 'approve' ? (isUnowned ? 'accepting' : 'approving') : 'declining'}…`}
            rows={2}
            className="w-full px-3 py-2 text-xs border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            autoFocus
          />
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant={action === 'approve' ? 'primary' : 'danger'}
              onClick={confirm}
              disabled={isPending}
            >
              {isPending
                ? (action === 'approve' ? (isUnowned ? 'Accepting…' : 'Approving…') : 'Declining…')
                : (action === 'approve' ? (isUnowned ? 'Confirm Accept' : 'Confirm Approve') : 'Confirm Decline')}
            </Button>
            <Button size="sm" variant="ghost" onClick={cancel} disabled={isPending}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      {!canAct && (
        <p className="text-xs text-gray-500">
          {isUnowned
            ? 'Awaiting acceptance from the nominated user.'
            : 'Awaiting approval from the current owner.'}
        </p>
      )}
    </div>
  );
}
