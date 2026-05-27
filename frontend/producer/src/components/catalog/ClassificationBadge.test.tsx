import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ClassificationBadge from './ClassificationBadge';

describe('ClassificationBadge', () => {
  it('should render "—" when no level is provided', () => {
    render(<ClassificationBadge />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('should render "—" when level is null', () => {
    render(<ClassificationBadge level={null} />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('should render "Public" label for PUBLIC level', () => {
    render(<ClassificationBadge level="PUBLIC" />);
    expect(screen.getByText('Public')).toBeInTheDocument();
  });

  it('should render "Internal" label for INTERNAL level', () => {
    render(<ClassificationBadge level="INTERNAL" />);
    expect(screen.getByText('Internal')).toBeInTheDocument();
  });

  it('should render "Confidential" label for CONFIDENTIAL level', () => {
    render(<ClassificationBadge level="CONFIDENTIAL" />);
    expect(screen.getByText('Confidential')).toBeInTheDocument();
  });

  it('should render "High Confidential" label for HIGH_CONFIDENTIAL level', () => {
    render(<ClassificationBadge level="HIGH_CONFIDENTIAL" />);
    expect(screen.getByText('High Confidential')).toBeInTheDocument();
  });

  it('should apply green classes for PUBLIC level', () => {
    const { container } = render(<ClassificationBadge level="PUBLIC" />);
    expect(container.firstChild).toHaveClass('bg-green-100', 'text-green-800');
  });

  it('should apply blue classes for INTERNAL level', () => {
    const { container } = render(<ClassificationBadge level="INTERNAL" />);
    expect(container.firstChild).toHaveClass('bg-blue-100', 'text-blue-800');
  });

  it('should apply amber classes for CONFIDENTIAL level', () => {
    const { container } = render(<ClassificationBadge level="CONFIDENTIAL" />);
    expect(container.firstChild).toHaveClass('bg-amber-100', 'text-amber-800');
  });

  it('should apply red classes for HIGH_CONFIDENTIAL level', () => {
    const { container } = render(<ClassificationBadge level="HIGH_CONFIDENTIAL" />);
    expect(container.firstChild).toHaveClass('bg-red-100', 'text-red-800');
  });
});
