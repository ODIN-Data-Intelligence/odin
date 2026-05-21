import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDate(iso?: string): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium' }).format(new Date(iso));
}

export function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}

export const LIFECYCLE_COLORS: Record<string, string> = {
  Ideation: 'bg-gray-100 text-gray-700',
  Design: 'bg-blue-100 text-blue-700',
  Build: 'bg-yellow-100 text-yellow-700',
  Deploy: 'bg-purple-100 text-purple-700',
  Consume: 'bg-green-100 text-green-700',
};

export const RUN_STATUS_COLORS: Record<string, string> = {
  pending: 'bg-gray-100 text-gray-700',
  running: 'bg-blue-100 text-blue-700',
  completed: 'bg-green-100 text-green-700',
  failed: 'bg-red-100 text-red-700',
  cancelled: 'bg-yellow-100 text-yellow-700',
};
