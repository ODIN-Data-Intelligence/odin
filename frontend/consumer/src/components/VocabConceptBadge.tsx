import { Link } from 'react-router-dom';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import { preferredLabel, iriFragment } from '@datacatalog/shared';

export { iriFragment as humanize };

interface VocabConceptBadgeProps {
  iri: string;
  label?: string;
  description?: string;
  matchType?: string;
}

const MATCH_TYPE_COLORS: Record<string, 'success' | 'primary' | 'secondary' | 'warning' | 'info'> = {
  exactMatch:   'success',
  closeMatch:   'primary',
  relatedMatch: 'secondary',
  broadMatch:   'warning',
  narrowMatch:  'info',
};

export default function VocabConceptBadge({ iri, label, description, matchType }: VocabConceptBadgeProps) {
  const color = matchType ? (MATCH_TYPE_COLORS[matchType] ?? 'default') : 'default';
  const display = preferredLabel(iri, label, description);

  return (
    <Tooltip title={iri}>
      <Chip
        label={display}
        color={color as any}
        size="small"
        component={Link}
        to={`/search?q=${encodeURIComponent(display)}`}
        clickable
        sx={{ height: 20, fontSize: 11, textDecoration: 'none' }}
      />
    </Tooltip>
  );
}
