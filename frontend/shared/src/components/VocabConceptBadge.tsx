import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import { Link } from 'react-router-dom';
import { preferredLabel } from '../utils/iri';

export { preferredLabel };

interface VocabConceptBadgeProps {
  iri: string;
  label?: string;
  description?: string;
  matchType?: string;
}

const MATCH_TYPE_COLORS: Record<string, 'success' | 'primary' | 'secondary' | 'warning' | 'default'> = {
  exactMatch:   'success',
  closeMatch:   'primary',
  relatedMatch: 'secondary',
  broadMatch:   'warning',
  narrowMatch:  'default',
};

export default function VocabConceptBadge({ iri, label, description, matchType }: VocabConceptBadgeProps) {
  const color = matchType ? (MATCH_TYPE_COLORS[matchType] ?? 'default') : 'default';
  const display = preferredLabel(iri, label, description);

  return (
    <Tooltip title={iri} arrow>
      <Chip
        label={display}
        color={color}
        size="small"
        variant="outlined"
        component={Link}
        to={`/search?q=${encodeURIComponent(display)}`}
        clickable
      />
    </Tooltip>
  );
}
