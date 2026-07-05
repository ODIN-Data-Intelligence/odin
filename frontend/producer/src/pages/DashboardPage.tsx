import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import { dashboardApi } from '@datacatalog/shared';
import type { ActivityProposal, ActivityChange } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';

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
    <Box sx={{ pb: 6 }}>
      <PageHeader title="Dashboard" description="Overview of your data estate" />

      {/* Stat cards */}
      <Box sx={{ px: 3, pt: 3, display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, maxWidth: 600 }}>
        <StatCard label="My Datasets"      value={datasetCount} />
        <StatCard label="My Data Products" value={dataProductCount} />
      </Box>

      <Box sx={{ px: 3, py: 3, display: 'flex', flexDirection: 'column', gap: 4 }}>
        {/* Outstanding tasks */}
        <Section title="Outstanding Tasks">
          {pendingRequests.length === 0 ? (
            <Typography variant="body2" color="text.secondary">No outstanding tasks.</Typography>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              {pendingRequests.map(req => (
                <Alert
                  key={req.id}
                  severity="warning"
                  action={
                    <Typography
                      component={Link}
                      to={`datasets/${req.datasetId}`}
                      variant="caption"
                      color="primary"
                      sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' }, whiteSpace: 'nowrap', mt: 0.5 }}
                    >
                      Review
                    </Typography>
                  }
                >
                  <Typography variant="body2" fontWeight={500}>Ownership transfer request</Typography>
                  <Typography variant="caption" color="text.secondary">
                    You have been proposed as the new owner of a dataset.
                    Requested {new Date(req.createdAt).toLocaleDateString()}.
                  </Typography>
                </Alert>
              ))}
            </Box>
          )}
        </Section>

        {/* My proposals */}
        <Section title="My Proposals">
          {activityLoading ? (
            <Typography variant="body2" color="text.secondary">Loading…</Typography>
          ) : proposals.length === 0 ? (
            <Typography variant="body2" color="text.secondary">No proposals yet.</Typography>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              {proposals.map(p => <ProposalRow key={p.id} proposal={p} />)}
            </Box>
          )}
        </Section>

        {/* My changes */}
        <Section title="My Changes">
          {activityLoading ? (
            <Typography variant="body2" color="text.secondary">Loading…</Typography>
          ) : changes.length === 0 ? (
            <Typography variant="body2" color="text.secondary">No dataset changes yet.</Typography>
          ) : (
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
              <List dense disablePadding>
                {changes.map((c, i) => (
                  <Box key={c.id}>
                    {i > 0 && <Divider />}
                    <ChangeRow change={c} />
                  </Box>
                ))}
              </List>
            </Paper>
          )}
        </Section>
      </Box>
    </Box>
  );
}

function ProposalRow({ proposal: p }: { proposal: ActivityProposal }) {
  const date = new Date(p.createdAt).toLocaleDateString(undefined, { dateStyle: 'medium' });
  const resolvedDate = p.resolvedAt
    ? new Date(p.resolvedAt).toLocaleDateString(undefined, { dateStyle: 'medium' })
    : null;

  const statusColor: Record<string, 'warning' | 'success' | 'error' | 'default'> = {
    PENDING: 'warning', APPROVED: 'success', REJECTED: 'error',
  };
  const roleLabel = p.role === 'PROPOSER' ? 'You proposed' : 'You were nominated';

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2, mb: 0.5 }}>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 0.25 }}>
            <Typography
              component={Link}
              to={`datasets/${p.datasetId}`}
              variant="body2"
              fontWeight={500}
              color="primary"
              noWrap
              sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
            >
              {p.datasetTitle}
            </Typography>
            <Chip label={p.status} color={statusColor[p.status] ?? 'default'} size="small" sx={{ height: 18, fontSize: 11 }} />
          </Box>
          <Typography variant="caption" color="text.secondary">
            {roleLabel} as owner · {date}
          </Typography>
        </Box>
      </Box>
      {p.status !== 'PENDING' && (
        <Box sx={{ mt: 1, pt: 1, borderTop: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary">
            <Typography component="span" variant="caption" fontWeight={500} color="text.primary">
              {p.status === 'APPROVED' ? 'Accepted' : 'Declined'}
            </Typography>
            {resolvedDate && <> on {resolvedDate}</>}
          </Typography>
          {p.note && (
            <Typography variant="caption" color="text.secondary" fontStyle="italic" display="block">
              &quot;{p.note}&quot;
            </Typography>
          )}
        </Box>
      )}
    </Paper>
  );
}

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
    <ListItem sx={{ '&:hover': { bgcolor: 'grey.50' } }}>
      <ListItemText
        primary={
          <Typography
            component={Link}
            to={`datasets/${c.datasetId}`}
            variant="body2"
            fontWeight={500}
            color="primary"
            noWrap
            sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' }, display: 'block' }}
          >
            {c.datasetTitle}
          </Typography>
        }
        secondary={label}
        secondaryTypographyProps={{ variant: 'caption' }}
      />
      <Typography variant="caption" color="text.disabled" sx={{ flexShrink: 0, ml: 2 }}>{date}</Typography>
    </ListItem>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>{title}</Typography>
      {children}
    </Box>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
      <Typography variant="h4" fontWeight={600} sx={{ mt: 0.5 }}>{value}</Typography>
    </Paper>
  );
}
