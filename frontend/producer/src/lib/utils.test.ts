import { describe, it, expect } from 'vitest';
import { cn, formatDate, formatDateTime, LIFECYCLE_COLORS, RUN_STATUS_COLORS } from './utils';

describe('cn', () => {
  it('should return the single class name unchanged', () => {
    expect(cn('text-sm')).toBe('text-sm');
  });

  it('should merge multiple class names', () => {
    expect(cn('px-2', 'py-1', 'text-sm')).toBe('px-2 py-1 text-sm');
  });

  it('should handle conditional classes (falsy omitted)', () => {
    expect(cn('base', false && 'skipped', 'other')).toBe('base other');
  });

  it('should deduplicate conflicting Tailwind classes (tailwind-merge)', () => {
    // tailwind-merge should keep the last conflicting utility
    const result = cn('px-2', 'px-4');
    expect(result).toBe('px-4');
  });

  it('should return empty string for no inputs', () => {
    expect(cn()).toBe('');
  });
});

describe('formatDate', () => {
  it('should return em-dash for undefined', () => {
    expect(formatDate(undefined)).toBe('—');
  });

  it('should return em-dash for empty string', () => {
    expect(formatDate('')).toBe('—');
  });

  it('should format a valid ISO date string', () => {
    // 2024-06-15 is a Saturday
    const result = formatDate('2024-06-15T00:00:00Z');
    expect(result).toMatch(/Jun/);
    expect(result).toMatch(/2024/);
  });
});

describe('formatDateTime', () => {
  it('should return em-dash for undefined', () => {
    expect(formatDateTime(undefined)).toBe('—');
  });

  it('should return em-dash for empty string', () => {
    expect(formatDateTime('')).toBe('—');
  });

  it('should format a valid ISO datetime string with time', () => {
    const result = formatDateTime('2024-06-15T14:30:00Z');
    expect(result).toMatch(/Jun/);
    expect(result).toMatch(/2024/);
    // Formatted output should be longer than date-only (includes time)
    expect(result.length).toBeGreaterThan(formatDate('2024-06-15T14:30:00Z').length);
  });
});

describe('LIFECYCLE_COLORS', () => {
  const expectedStatuses = ['Ideation', 'Design', 'Build', 'Deploy', 'Consume'];

  it.each(expectedStatuses)('should have a color class for %s', (status) => {
    expect(LIFECYCLE_COLORS[status]).toBeDefined();
    expect(LIFECYCLE_COLORS[status]).toContain('text-');
  });

  it('should not be empty', () => {
    expect(Object.keys(LIFECYCLE_COLORS).length).toBe(5);
  });
});

describe('RUN_STATUS_COLORS', () => {
  const expectedStatuses = ['pending', 'running', 'completed', 'failed', 'cancelled'];

  it.each(expectedStatuses)('should have a color class for %s', (status) => {
    expect(RUN_STATUS_COLORS[status]).toBeDefined();
    expect(RUN_STATUS_COLORS[status]).toContain('text-');
  });
});
