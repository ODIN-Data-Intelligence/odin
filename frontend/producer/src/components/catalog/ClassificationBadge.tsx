import type { LogicalDataElement } from '@datacatalog/shared';

type ClassificationLevel = NonNullable<LogicalDataElement['classification']>;

const STYLES: Record<ClassificationLevel, string> = {
  PUBLIC: 'bg-green-100 text-green-800',
  INTERNAL: 'bg-blue-100 text-blue-800',
  CONFIDENTIAL: 'bg-amber-100 text-amber-800',
  HIGH_CONFIDENTIAL: 'bg-red-100 text-red-800',
};

const LABELS: Record<ClassificationLevel, string> = {
  PUBLIC: 'Public',
  INTERNAL: 'Internal',
  CONFIDENTIAL: 'Confidential',
  HIGH_CONFIDENTIAL: 'High Confidential',
};

interface Props {
  level?: ClassificationLevel | null;
}

export default function ClassificationBadge({ level }: Props) {
  if (!level) {
    return <span className="text-gray-400 text-xs">—</span>;
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${STYLES[level]}`}>
      {LABELS[level]}
    </span>
  );
}
