import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Avatar from '@mui/material/Avatar';
import Alert from '@mui/material/Alert';
import { datasetApi, userApi } from '@datacatalog/shared';
import type { Dataset, OwnershipProposal, User } from '@datacatalog/shared';
import { useAuthStore } from '../../store/authStore';
import OwnershipTransferModal from './OwnershipTransferModal';
import AssignOwnerModal from './AssignOwnerModal';

function resolveUser(users: User[], id: string): User | undefined {
  return users.find(u => u.keycloakUserId === id || u.id === id);
}

function userName(u: User | undefined): string | undefined {
  if (!u) return undefined;
  const name = `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim();
  return name || undefined;
}

function UserDisplay({ users, id }: { users: User[]; id: string }) {
  const u = resolveUser(users, id);
  const name = userName(u);
  const email = u?.email;
  if (!name && !email) return <Typography variant="caption" fontFamily="monospace" color="text.secondary">{id}</Typography>;
  return (
    <Box sx={{ display: 'inline-flex', flexDirection: 'column', lineHeight: 1.3 }}>
      {name && <Typography variant="body2" fontWeight={600}>{name}</Typography>}
      {email && <Typography variant="caption" color="text.secondary">{email}</Typography>}
    </Box>
  );
}

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

  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.list(),
  });

  const { data: owner } = useQuery({
    queryKey: ['user-by-keycloak', dataset.ownerId],
    queryFn: () => userApi.getByKeycloakId(dataset.ownerId!),
    enabled: !!dataset.ownerId,
    staleTime: 300_000,
    retry: false,
  });

  const currentLocalUser = users.find(u => u.keycloakUserId === userId);

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

  const canDirectAssign = hasRole('administrator');
  const canProposeOwner = hasAnyRole(['data-governance', 'data-steward']) && !canDirectAssign;
  const canClaimSelf    = hasRole('data-owner') && !!userId && !canDirectAssign && !canProposeOwner;
  const isCurrentOwner  = !!dataset.ownerId && (
    dataset.ownerId === userId ||
    (!!currentLocalUser && dataset.ownerId === currentLocalUser.id)
  );
  const isProposedOwner = !!pendingProposal && pendingProposal.proposedOwnerId === userId;

  return (
    <Box sx={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <SectionBox title="Data Owner">
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
            ownerName={owner ? `${owner.firstName ?? ''} ${owner.lastName ?? ''}`.trim() || undefined : undefined}
            isCurrentOwner={isCurrentOwner}
            onTransfer={() => setShowTransferModal(true)}
          />
        )}
      </SectionBox>

      {pendingProposal && (
        <SectionBox title={dataset.ownerId ? 'Pending Transfer Proposal' : 'Pending Ownership Nomination'}>
          <ProposalCard
            proposal={pendingProposal}
            users={users}
            isUnowned={!dataset.ownerId}
            canAct={dataset.ownerId
              ? (isCurrentOwner || canDirectAssign)
              : (isCurrentOwner || isProposedOwner || canDirectAssign)}
            onApprove={(note) => approveMutation.mutate({ proposalId: pendingProposal.id, note })}
            onReject={(note) => rejectMutation.mutate({ proposalId: pendingProposal.id, note })}
            isApprovePending={approveMutation.isPending}
            isRejectPending={rejectMutation.isPending}
          />
        </SectionBox>
      )}

      {showAssignModal && (
        <AssignOwnerModal
          datasetId={dataset.id}
          onSuccess={(updated) => { setShowAssignModal(false); onUpdated(updated); }}
          onCancel={() => setShowAssignModal(false)}
        />
      )}

      {showTransferModal && (
        <OwnershipTransferModal
          datasetId={dataset.id}
          onSuccess={(proposal) => { setShowTransferModal(false); qc.setQueryData(['pending-proposal', dataset.id], proposal); }}
          onCancel={() => setShowTransferModal(false)}
        />
      )}

      {showProposeModal && (
        <OwnershipTransferModal
          datasetId={dataset.id}
          title="Nominate Data Owner"
          description="Select the user you are proposing as data owner. They will be notified and can accept the nomination."
          submitLabel="Nominate"
          onSuccess={(proposal) => { setShowProposeModal(false); qc.setQueryData(['pending-proposal', dataset.id], proposal); }}
          onCancel={() => setShowProposeModal(false)}
        />
      )}
    </Box>
  );
}

function SectionBox({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="body2" fontWeight={600} sx={{ mb: 1.5 }}>{title}</Typography>
      {children}
    </Box>
  );
}

function UnownedState({ canDirectAssign, canProposeOwner, canClaimSelf, onOpenPicker, onOpenProposalPicker, onClaimSelf, isPending, error }: {
  canDirectAssign: boolean; canProposeOwner: boolean; canClaimSelf: boolean;
  onOpenPicker: () => void; onOpenProposalPicker: () => void; onClaimSelf: () => void;
  isPending: boolean; error: boolean;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 3, borderStyle: 'dashed', bgcolor: 'grey.50' }}>
      <Box sx={{ flex: 1 }}>
        <Chip label="Unowned" size="small" sx={{ mb: 1, height: 18, fontSize: 11 }} />
        <Typography variant="body2" color="text.secondary">
          No data owner assigned. Without an owner, metadata may become stale.
        </Typography>
        {error && <Typography variant="caption" color="error" sx={{ mt: 0.5, display: 'block' }}>Failed to assign owner. Please try again.</Typography>}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, flexShrink: 0 }}>
        {canDirectAssign && <Button variant="contained" size="small" onClick={onOpenPicker} disabled={isPending} sx={{ textTransform: 'none' }}>Assign Owner</Button>}
        {canProposeOwner && <Button variant="outlined" size="small" onClick={onOpenProposalPicker} disabled={isPending} sx={{ textTransform: 'none' }}>Propose Owner</Button>}
        {canClaimSelf && <Button variant="outlined" size="small" onClick={onClaimSelf} disabled={isPending} sx={{ textTransform: 'none' }}>{isPending ? 'Claiming…' : 'Claim as Owner'}</Button>}
      </Box>
    </Paper>
  );
}

function OwnedState({ ownerId, ownerEmail, ownerName, isCurrentOwner, onTransfer }: {
  ownerId: string; ownerEmail?: string; ownerName?: string; isCurrentOwner: boolean; onTransfer: () => void;
}) {
  const initials = ownerName ? ownerName[0].toUpperCase() : ownerEmail?.[0].toUpperCase() ?? '?';
  return (
    <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
      <Avatar sx={{ bgcolor: 'primary.light', color: 'primary.contrastText', width: 36, height: 36, fontSize: 14, fontWeight: 600 }}>
        {initials}
      </Avatar>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {ownerName && <Typography variant="body2" fontWeight={600} noWrap>{ownerName}</Typography>}
        <Typography variant="caption" color="text.secondary" noWrap>{ownerEmail ?? ownerId}</Typography>
        {isCurrentOwner && <Typography variant="caption" color="primary" fontWeight={500} sx={{ display: 'block' }}>You are the owner</Typography>}
      </Box>
      <Button variant="outlined" size="small" onClick={onTransfer} sx={{ textTransform: 'none', flexShrink: 0 }}>
        {isCurrentOwner ? 'Transfer ownership' : 'Propose transfer'}
      </Button>
    </Paper>
  );
}

function ProposalCard({ proposal, users, isUnowned, canAct, onApprove, onReject, isApprovePending, isRejectPending }: {
  proposal: OwnershipProposal; users: User[]; isUnowned: boolean; canAct: boolean;
  onApprove: (note?: string) => void; onReject: (note?: string) => void;
  isApprovePending: boolean; isRejectPending: boolean;
}) {
  const [action, setAction] = useState<'approve' | 'decline' | null>(null);
  const [note, setNote] = useState('');
  const created = new Date(proposal.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });
  const isPending = isApprovePending || isRejectPending;

  function confirm() {
    if (action === 'approve') onApprove(note.trim() || undefined);
    else onReject(note.trim() || undefined);
  }

  return (
    <Paper variant="outlined" sx={{ p: 2, bgcolor: 'warning.50', borderColor: 'warning.200', display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box>
        <Chip label="PENDING" color="warning" size="small" sx={{ height: 18, fontSize: 11, mb: 1 }} />
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap' }}>
          <Typography variant="body2" color="text.secondary">{isUnowned ? 'Nominated owner:' : 'Transfer to'}</Typography>
          <UserDisplay users={users} id={proposal.proposedOwnerId} />
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap', mt: 0.5 }}>
          <Typography variant="caption" color="text.secondary">Proposed by</Typography>
          <UserDisplay users={users} id={proposal.proposedById} />
          <Typography variant="caption" color="text.secondary">on {created}</Typography>
        </Box>
      </Box>

      {canAct && !action && (
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="contained" size="small" onClick={() => setAction('approve')} disabled={isPending} sx={{ textTransform: 'none' }}>
            {isUnowned ? 'Accept' : 'Approve'}
          </Button>
          <Button variant="contained" color="error" size="small" onClick={() => setAction('decline')} disabled={isPending} sx={{ textTransform: 'none' }}>
            Decline
          </Button>
        </Box>
      )}

      {canAct && action && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <TextField
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder={`Optional note for ${action === 'approve' ? (isUnowned ? 'accepting' : 'approving') : 'declining'}…`}
            multiline
            rows={2}
            size="small"
            autoFocus
            fullWidth
            inputProps={{ style: { fontSize: 12 } }}
          />
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="contained"
              color={action === 'approve' ? 'primary' : 'error'}
              size="small"
              onClick={confirm}
              disabled={isPending}
              sx={{ textTransform: 'none' }}
            >
              {isPending
                ? (action === 'approve' ? (isUnowned ? 'Accepting…' : 'Approving…') : 'Declining…')
                : (action === 'approve' ? (isUnowned ? 'Confirm Accept' : 'Confirm Approve') : 'Confirm Decline')}
            </Button>
            <Button size="small" onClick={() => { setAction(null); setNote(''); }} disabled={isPending} sx={{ textTransform: 'none' }}>
              Cancel
            </Button>
          </Box>
        </Box>
      )}

      {!canAct && (
        <Typography variant="caption" color="text.secondary">
          {isUnowned ? 'Awaiting acceptance from the nominated user.' : 'Awaiting approval from the current owner.'}
        </Typography>
      )}
    </Paper>
  );
}
