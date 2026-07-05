import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Badge from './Badge';

describe('Badge', () => {
  it('should render the label text', () => {
    render(<Badge label="Deploy" />);
    expect(screen.getByText('Deploy')).toBeInTheDocument();
  });

  it('should apply base style classes', () => {
    render(<Badge label="Build" />);
    const badge = screen.getByText('Build');
    expect(badge).toHaveClass('inline-flex', 'items-center', 'rounded', 'text-xs', 'font-medium');
  });

  it('should merge custom className', () => {
    render(<Badge label="Active" className="bg-green-100 text-green-700" />);
    const badge = screen.getByText('Active');
    expect(badge).toHaveClass('bg-green-100', 'text-green-700');
  });

  it('should allow className to override base styles via tailwind-merge', () => {
    // Provide a custom padding that would conflict — tailwind-merge picks last
    render(<Badge label="X" className="px-4" />);
    const badge = screen.getByText('X');
    expect(badge).toHaveClass('px-4');
  });
});
