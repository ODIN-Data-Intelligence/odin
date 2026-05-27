import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { datasetApi, userApi } from '@datacatalog/shared';
import type { Dataset, OwnershipProposal } from '@datacatalog/shared';
import Button from '../ui/Button';
import OwnershipTransferModal from './OwnershipTransferModal';

// Stub: replace with real auth store when wired
function useCurrentUserId(): string | null {
  return null;
}

interface OwnershipPanelProps {
  dataset: Dataset;
  onUpdated: (dataset: Dataset) => void;
}

export default function OwnershipPanel({ dataset, onUpdated }: OwnershipPanelProps) {
  const qc = useQueryClient();
  const currentUserId = useCurrentUserId();
  const [showTransferModal, setShowTransferModal] = useState(false);

  // Resolve owner display name
  const { data: owner } = useQuery({
    queryKey: ['user', dataset.ownerId],
    queryFn: () => userApi.get(dataset.ownerId!),
    enabled: !!dataset.ownerId,
  });

  // Check for pending proposal
  const { data: pendingProposal, refetch: refetchProposal } = useQuery({
    queryKey: ['pending-proposal', dataset.id],
    queryFn: () => datasetApi.getPendingProposal(dataset.id),
  });

  const assignMutation = useMutation({
    mutationFn: (userId: string) => datasetApi.assignOwner(dataset.id, userId),
    onSuccess: (updated) => {
      onUpdated(updated);
      qc.invalidateQueries({ queryKey: ['pending-proposal', dataset.id] });
    },
  });

  const approveMutation = useMutation({
    mutationFn: (proposalId: string) => datasetApi.approveTransfer(dataset.id, proposalId),
    onSuccess: (updated) => {
      onUpdated(updated);
      qc.invalidateQueries({ queryKey: ['pending-proposal', dataset.id] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (proposalId: string) => datasetApi.rejectTransfer(dataset.id, proposalId),
    onSuccess: () => {
      refetchProposal();
    },
  });

  const isCurrentOwner = !!dataset.ownerId && dataset.ownerId === currentUserId;

  return (
    <div className="max-w-2xl space-y-6">
      {/* Owner section */}
      <Section title="Data Owner">
        {!dataset.ownerId ? (
          <UnownedState
            onAssignSelf={() => currentUserId && assignMutation.mutate(currentUserId)}
            isPending={assignMutation.isPending}
            hasCurrentUser={!!currentUserId}
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
        <Section title="Pending Transfer Proposal">
          <ProposalCard
            proposal={pendingProposal}
            isCurrentOwner={isCurrentOwner}
            onApprove={() => approveMutation.mutate(pendingProposal.id)}
            onReject={() => rejectMutation.mutate(pendingProposal.id)}
            isApprovePending={approveMutation.isPending}
            isRejectPending={rejectMutation.isPending}
          />
        </Section>
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
  onAssignSelf,
  isPending,
  hasCurrentUser,
  error,
}: {
  onAssignSelf: () => void;
  isPending: boolean;
  hasCurrentUser: boolean;
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
      {hasCurrentUser && (
        <Button size="sm" onClick={onAssignSelf} disabled={isPending}>
          {isPending ? 'Assigning…' : 'Assign myself as owner'}
        </Button>
      )}
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
  isCurrentOwner,
  onApprove,
  onReject,
  isApprovePending,
  isRejectPending,
}: {
  proposal: OwnershipProposal;
  isCurrentOwner: boolean;
  onApprove: () => void;
  onReject: () => void;
  isApprovePending: boolean;
  isRejectPending: boolean;
}) {
  const created = new Date(proposal.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });

  return (
    <div className="p-4 rounded-lg border border-amber-200 bg-amber-50 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <span className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700">
            PENDING
          </span>
          <p className="text-sm text-gray-700 mt-1.5">
            Transfer to <span className="font-medium font-mono text-xs">{proposal.proposedOwnerId}</span>
          </p>
          <p className="text-xs text-gray-500 mt-0.5">
            Proposed by <span className="font-mono">{proposal.proposedById}</span> on {created}
          </p>
        </div>
        {isCurrentOwner && (
          <div className="flex items-center gap-2 flex-shrink-0">
            <Button
              size="sm"
              onClick={onApprove}
              disabled={isApprovePending || isRejectPending}
            >
              {isApprovePending ? 'Approving…' : 'Approve'}
            </Button>
            <Button
              size="sm"
              variant="danger"
              onClick={onReject}
              disabled={isApprovePending || isRejectPending}
            >
              {isRejectPending ? 'Rejecting…' : 'Reject'}
            </Button>
          </div>
        )}
      </div>
      {!isCurrentOwner && (
        <p className="text-xs text-gray-500">
          Awaiting approval from the current owner.
        </p>
      )}
    </div>
  );
}
