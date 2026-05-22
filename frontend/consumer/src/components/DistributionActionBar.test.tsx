import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DistributionActionBar from './DistributionActionBar';
import type { Distribution } from '@datacatalog/shared';

const baseDistribution: Distribution = {
  id: 'dist-1',
  resourceType: 'Distribution',
  tenantId: 'tenant-1',
  datasetId: 'ds-1',
  title: 'Trade CSV',
  format: 'text/csv',
  accessUrl: 'http://api.example.com/trades',
  downloadUrl: 'http://files.example.com/trades.csv',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

afterEach(() => {
  vi.clearAllMocks();
});

describe('DistributionActionBar', () => {
  it('should return null when distributions array is empty', () => {
    const { container } = render(<DistributionActionBar distributions={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('should display format label', () => {
    render(<DistributionActionBar distributions={[baseDistribution]} />);
    expect(screen.getByText('text/csv')).toBeInTheDocument();
  });

  it('should display mediaType when format is not set', () => {
    const dist: Distribution = { ...baseDistribution, format: undefined, mediaType: 'application/parquet' };
    render(<DistributionActionBar distributions={[dist]} />);
    expect(screen.getByText('application/parquet')).toBeInTheDocument();
  });

  it('should show Download label fallback when neither format nor mediaType', () => {
    const dist: Distribution = { ...baseDistribution, format: undefined, mediaType: undefined };
    render(<DistributionActionBar distributions={[dist]} />);
    expect(screen.getByText('Download')).toBeInTheDocument();
  });

  it('should call copyFn with the access URL when copy button clicked', async () => {
    const user = userEvent.setup();
    const copyFn = vi.fn().mockResolvedValue(undefined);
    render(<DistributionActionBar distributions={[baseDistribution]} copyFn={copyFn} />);
    await user.click(screen.getByTitle('Copy URL'));
    await waitFor(() => expect(copyFn).toHaveBeenCalledWith('http://api.example.com/trades'));
  });

  it('should show checkmark after copying', async () => {
    const user = userEvent.setup();
    const copyFn = vi.fn().mockResolvedValue(undefined);
    render(<DistributionActionBar distributions={[baseDistribution]} copyFn={copyFn} />);
    await user.click(screen.getByTitle('Copy URL'));
    await waitFor(() => expect(screen.getByTitle('Copy URL').textContent).toBe('✓'));
  });

  it('should revert copy button back to ⎘ after 1500ms', async () => {
    const user = userEvent.setup();
    const copyFn = vi.fn().mockResolvedValue(undefined);
    render(<DistributionActionBar distributions={[baseDistribution]} copyFn={copyFn} />);

    await user.click(screen.getByTitle('Copy URL'));
    await waitFor(() => expect(screen.getByTitle('Copy URL').textContent).toBe('✓'));

    await waitFor(() => expect(screen.getByTitle('Copy URL').textContent).toBe('⎘'), { timeout: 3000 });
  }, 8000);

  it('should render download link with correct href', () => {
    render(<DistributionActionBar distributions={[baseDistribution]} />);
    expect(screen.getByTitle('Download').getAttribute('href')).toBe('http://files.example.com/trades.csv');
  });

  it('should render access link with correct href', () => {
    render(<DistributionActionBar distributions={[baseDistribution]} />);
    expect(screen.getByTitle('Open in new tab').getAttribute('href')).toBe('http://api.example.com/trades');
  });

  it('should render multiple distributions', () => {
    const dist2: Distribution = { ...baseDistribution, id: 'dist-2', format: 'application/parquet' };
    render(<DistributionActionBar distributions={[baseDistribution, dist2]} />);
    expect(screen.getByText('text/csv')).toBeInTheDocument();
    expect(screen.getByText('application/parquet')).toBeInTheDocument();
  });

  it('should not render copy or open buttons when no URL', () => {
    const dist: Distribution = { ...baseDistribution, accessUrl: undefined, downloadUrl: undefined };
    render(<DistributionActionBar distributions={[dist]} />);
    expect(screen.queryByTitle('Copy URL')).toBeNull();
    expect(screen.queryByTitle('Open in new tab')).toBeNull();
  });
});
