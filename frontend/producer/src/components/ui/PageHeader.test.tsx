import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PageHeader from './PageHeader';

describe('PageHeader', () => {
  it('should render the title', () => {
    render(<PageHeader title="Data Products" />);
    expect(screen.getByRole('heading', { name: 'Data Products' })).toBeInTheDocument();
  });

  it('should render description when provided', () => {
    render(<PageHeader title="Datasets" description="Manage all datasets" />);
    expect(screen.getByText('Manage all datasets')).toBeInTheDocument();
  });

  it('should not render description element when not provided', () => {
    render(<PageHeader title="Settings" />);
    expect(screen.queryByRole('paragraph')).toBeNull();
  });

  it('should render actions when provided', () => {
    render(
      <PageHeader
        title="Users"
        actions={<button>Invite User</button>}
      />
    );
    expect(screen.getByRole('button', { name: 'Invite User' })).toBeInTheDocument();
  });

  it('should not render actions container when not provided', () => {
    const { container } = render(<PageHeader title="No Actions" />);
    // The actions wrapper div should not be in the DOM
    const actionsDiv = container.querySelector('.flex.items-center.gap-2');
    expect(actionsDiv).toBeNull();
  });

  it('should have h1 heading level', () => {
    render(<PageHeader title="Title" />);
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toHaveTextContent('Title');
  });
});
