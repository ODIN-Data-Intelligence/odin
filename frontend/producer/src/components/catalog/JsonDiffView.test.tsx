import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { JsonDiffView, computeDiff } from '@datacatalog/shared';

describe('computeDiff', () => {
  it('should mark keys present only in after as added', () => {
    const diff = computeDiff({}, { name: 'Alice' });
    const entry = diff.find(d => d.key === 'name')!;
    expect(entry.status).toBe('added');
    expect(entry.before).toBeUndefined();
    expect(entry.after).toBe('Alice');
  });

  it('should mark keys present only in before as removed', () => {
    const diff = computeDiff({ name: 'Alice' }, {});
    const entry = diff.find(d => d.key === 'name')!;
    expect(entry.status).toBe('removed');
    expect(entry.before).toBe('Alice');
    expect(entry.after).toBeUndefined();
  });

  it('should mark keys with different values as changed', () => {
    const diff = computeDiff({ title: 'Old' }, { title: 'New' });
    const entry = diff.find(d => d.key === 'title')!;
    expect(entry.status).toBe('changed');
    expect(entry.before).toBe('Old');
    expect(entry.after).toBe('New');
  });

  it('should mark keys with same values as unchanged', () => {
    const diff = computeDiff({ id: '123' }, { id: '123' });
    const entry = diff.find(d => d.key === 'id')!;
    expect(entry.status).toBe('unchanged');
  });

  it('should detect deep JSON differences by serializing', () => {
    const before = { tags: ['a', 'b'] };
    const after = { tags: ['a', 'c'] };
    const diff = computeDiff(before, after);
    expect(diff.find(d => d.key === 'tags')?.status).toBe('changed');
  });

  it('should sort changed entries before unchanged ones', () => {
    const diff = computeDiff({ a: 1, b: 2 }, { a: 99, b: 2 });
    expect(diff[0].status).toBe('changed');
    expect(diff[1].status).toBe('unchanged');
  });

  it('should return empty array when both objects are empty', () => {
    expect(computeDiff({}, {})).toHaveLength(0);
  });

  it('should include all keys from both before and after', () => {
    const diff = computeDiff({ x: 1 }, { y: 2 });
    const keys = diff.map(d => d.key);
    expect(keys).toContain('x');
    expect(keys).toContain('y');
  });
});

describe('JsonDiffView', () => {
  it('should render a table with Before and After headers', () => {
    render(
      <JsonDiffView
        before={JSON.stringify({ title: 'Old Title' })}
        after={JSON.stringify({ title: 'New Title' })}
      />,
    );
    expect(screen.getByText('Before')).toBeInTheDocument();
    expect(screen.getByText('After')).toBeInTheDocument();
  });

  it('should show the changed field name', () => {
    render(
      <JsonDiffView
        before={JSON.stringify({ title: 'Old' })}
        after={JSON.stringify({ title: 'New' })}
      />,
    );
    expect(screen.getByText('title')).toBeInTheDocument();
  });

  it('should show before and after values', () => {
    render(
      <JsonDiffView
        before={JSON.stringify({ version: '1.0' })}
        after={JSON.stringify({ version: '2.0' })}
      />,
    );
    expect(screen.getByText('1.0')).toBeInTheDocument();
    expect(screen.getByText('2.0')).toBeInTheDocument();
  });

  it('should render no-snapshot message when both before and after are null', () => {
    render(<JsonDiffView before={null} after={null} />);
    expect(screen.getByText(/no snapshot available/i)).toBeInTheDocument();
  });

  it('should handle invalid JSON gracefully', () => {
    render(<JsonDiffView before="not-json" after={JSON.stringify({ x: 1 })} />);
    expect(screen.getByText('x')).toBeInTheDocument();
  });

  it('should show unchanged field count when there are both changed and unchanged fields', () => {
    const before = { title: 'Old', id: 'same' };
    const after = { title: 'New', id: 'same' };
    render(<JsonDiffView before={JSON.stringify(before)} after={JSON.stringify(after)} />);
    expect(screen.getByText(/1 unchanged field/i)).toBeInTheDocument();
  });

  it('should render array values as comma-joined strings', () => {
    render(
      <JsonDiffView
        before={JSON.stringify({ keywords: ['risk', 'trading'] })}
        after={JSON.stringify({ keywords: ['risk', 'trading', 'equity'] })}
      />,
    );
    expect(screen.getByText('risk, trading')).toBeInTheDocument();
    expect(screen.getByText('risk, trading, equity')).toBeInTheDocument();
  });
});
