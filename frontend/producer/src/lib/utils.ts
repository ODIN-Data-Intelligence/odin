export function cn(...inputs: (string | undefined | null | false)[]): string {
  return inputs.filter(Boolean).join(' ');
}

export function formatDate(iso?: string): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(iso));
}

export function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}

export const LIFECYCLE_COLORS: Record<string, 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info' | 'secondary'> = {
  Ideation: 'default',
  Design:   'info',
  Build:    'warning',
  Deploy:   'secondary',
  Consume:  'success',
};

export const RUN_STATUS_COLORS: Record<string, 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'> = {
  pending:   'default',
  running:   'primary',
  completed: 'success',
  failed:    'error',
  cancelled: 'warning',
};
