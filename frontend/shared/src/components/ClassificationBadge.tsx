import Chip from '@mui/material/Chip';
import type { LogicalDataElement } from '../types/catalog';

type ClassificationLevel = NonNullable<LogicalDataElement['classification']>;

const COLOR_MAP: Record<ClassificationLevel, 'success' | 'info' | 'warning' | 'error'> = {
  PUBLIC:            'success',
  INTERNAL:          'info',
  CONFIDENTIAL:      'warning',
  HIGH_CONFIDENTIAL: 'error',
};

const LABELS: Record<ClassificationLevel, string> = {
  PUBLIC:            'Public',
  INTERNAL:          'Internal',
  CONFIDENTIAL:      'Confidential',
  HIGH_CONFIDENTIAL: 'High Confidential',
};

interface Props {
  level?: ClassificationLevel | null;
}

export default function ClassificationBadge({ level }: Props) {
  if (!level) return <Chip label="—" size="small" variant="outlined" sx={{ color: 'text.disabled' }} />;
  return <Chip label={LABELS[level]} color={COLOR_MAP[level]} size="small" />;
}
