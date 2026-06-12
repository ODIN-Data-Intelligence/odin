import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Paper from '@mui/material/Paper';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import type { TermsOfUse } from '../types/catalog';

const ACCESS_LEVEL_CONFIG: Record<
  NonNullable<TermsOfUse['accessLevel']>,
  { label: string; color: 'success' | 'info' | 'warning' | 'error' }
> = {
  OPEN:              { label: 'Open',             color: 'success' },
  INTERNAL_ONLY:     { label: 'Internal Only',    color: 'info' },
  RESTRICTED:        { label: 'Restricted',       color: 'warning' },
  HIGHLY_RESTRICTED: { label: 'Highly Restricted', color: 'error' },
};

interface TermsOfUseDisplayProps {
  terms: TermsOfUse;
}

export default function TermsOfUseDisplay({ terms }: TermsOfUseDisplayProps) {
  const levelConfig = terms.accessLevel ? ACCESS_LEVEL_CONFIG[terms.accessLevel] : null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        {levelConfig && (
          <Chip label={levelConfig.label} color={levelConfig.color} size="small" />
        )}
        {terms.effectiveClassification && (
          <Typography variant="caption" color="text.secondary">
            Effective classification:{' '}
            <Typography component="span" variant="caption" fontWeight={600} color="text.primary">
              {terms.effectiveClassification}
            </Typography>
          </Typography>
        )}
      </Box>

      {terms.policySource === 'explicit' && (
        <Alert severity="info" variant="outlined" sx={{ py: 0.5 }}>
          This dataset has a custom policy set by the data owner.
        </Alert>
      )}
      {terms.policySource === 'fallback' && (
        <Alert severity="info" variant="outlined" sx={{ py: 0.5 }}>
          No element classifications found. Terms are derived from the dataset's declared license.
        </Alert>
      )}

      {terms.permissions && terms.permissions.length > 0 && (
        <RuleSection title="Permitted Uses" items={terms.permissions} severity="success" icon="✓" />
      )}
      {terms.prohibitions && terms.prohibitions.length > 0 && (
        <RuleSection title="Restrictions" items={terms.prohibitions} severity="error" icon="✕" />
      )}
      {terms.obligations && terms.obligations.length > 0 && (
        <RuleSection title="Obligations" items={terms.obligations} severity="warning" icon="!" />
      )}

      {terms.applicableRegulations && terms.applicableRegulations.length > 0 && (
        <Box>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 1, display: 'block' }}>
            Applicable Regulations
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
            {terms.applicableRegulations.map(reg => (
              <Chip key={reg} label={reg} size="small" color="secondary" variant="outlined" />
            ))}
          </Box>
        </Box>
      )}

      {terms.odrlPolicy && (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Box
            component="details"
            sx={{ '& summary': { px: 2, py: 1, cursor: 'pointer', userSelect: 'none', fontSize: 13, fontWeight: 500, color: 'text.secondary', '&:hover': { bgcolor: 'grey.50' } } }}
          >
            <summary>ODRL Policy (Technical)</summary>
            <Box
              component="pre"
              sx={{ px: 2, py: 1.5, m: 0, fontSize: 12, fontFamily: 'monospace', bgcolor: 'grey.50', borderTop: 1, borderColor: 'divider', overflowX: 'auto', whiteSpace: 'pre-wrap' }}
            >
              {JSON.stringify(terms.odrlPolicy, null, 2)}
            </Box>
          </Box>
        </Paper>
      )}
    </Box>
  );
}

function RuleSection({
  title, items, severity, icon,
}: {
  title: string;
  items: string[];
  severity: 'success' | 'error' | 'warning';
  icon: string;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, borderColor: `${severity}.light` }}>
      <Typography variant="caption" fontWeight={700} color={`${severity}.dark`} sx={{ mb: 1, display: 'block' }}>
        {title}
      </Typography>
      <List dense disablePadding>
        {items.map(item => (
          <ListItem key={item} disableGutters sx={{ py: 0.25, alignItems: 'flex-start' }}>
            <Typography variant="caption" color={`${severity}.main`} fontWeight={700} sx={{ mr: 1, mt: 0.1, flexShrink: 0 }}>
              {icon}
            </Typography>
            <ListItemText primary={item} primaryTypographyProps={{ variant: 'caption', color: 'text.primary' }} />
          </ListItem>
        ))}
      </List>
    </Paper>
  );
}
