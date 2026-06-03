import { Link } from 'react-router-dom';
import { preferredLabel } from '../utils/iri';

export { preferredLabel };

interface VocabConceptBadgeProps {
  iri: string;
  label?: string;
  description?: string;
  matchType?: string;
}

const MATCH_TYPE_COLORS: Record<string, string> = {
  exactMatch:   'bg-green-100 text-green-700 border-green-200 hover:bg-green-200',
  closeMatch:   'bg-blue-100 text-blue-700 border-blue-200 hover:bg-blue-200',
  relatedMatch: 'bg-purple-100 text-purple-700 border-purple-200 hover:bg-purple-200',
  broadMatch:   'bg-orange-100 text-orange-700 border-orange-200 hover:bg-orange-200',
  narrowMatch:  'bg-yellow-100 text-yellow-700 border-yellow-200 hover:bg-yellow-200',
};

export default function VocabConceptBadge({ iri, label, description, matchType }: VocabConceptBadgeProps) {
  const colorClass = matchType
    ? (MATCH_TYPE_COLORS[matchType] ?? 'bg-gray-100 text-gray-700 border-gray-200 hover:bg-gray-200')
    : 'bg-gray-100 text-gray-700 border-gray-200 hover:bg-gray-200';
  const display = preferredLabel(iri, label, description);

  return (
    <Link
      to={`/search?q=${encodeURIComponent(display)}`}
      title={iri}
      className={`inline-flex items-center px-2 py-0.5 rounded border text-xs font-medium transition-colors ${colorClass}`}
    >
      {display}
    </Link>
  );
}
