import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as sharedApi from '@datacatalog/shared';
import type { Distribution, PageResponse } from '@datacatalog/shared';
import DistributionsPage from './DistributionsPage';

vi.mock('@datacatalog/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@datacatalog/shared')>();
  return {
    ...actual,
    distributionApi: {
      list: vi.fn(),
    },
  };
});

const mockedDistributionApi = vi.mocked(sharedApi.distributionApi);

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/default/distributions']}>
        <Routes>
          <Route path="/:tenant/distributions" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const EMPTY_PAGE: PageResponse<Distribution> = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
};

const DIST_1: Distribution = {
  id: 'dist-1', datasetId: 'ds-aabbccdd-1234-5678-0000-000000000001',
  tenantId: 'default', resourceType: 'DISTRIBUTION',
  title: 'Parquet Snapshot', format: 'Parquet',
  mediaType: 'application/vnd.apache.parquet',
  updatedAt: '2024-03-01T12:00:00Z',
};

const DIST_2: Distribution = {
  id: 'dist-2', datasetId: 'ds-11223344-1234-5678-0000-000000000002',
  tenantId: 'default', resourceType: 'DISTRIBUTION',
  title: 'CSV Export', format: 'CSV',
  mediaType: 'text/csv',
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedDistributionApi.list.mockResolvedValue(EMPTY_PAGE);
});

describe('DistributionsPage', () => {
  it('should render the page header', async () => {
    render(<DistributionsPage />, { wrapper });
    expect(screen.getByText('Distributions')).toBeInTheDocument();
  });

  it('should show empty state when no distributions', async () => {
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('No distributions found.')).toBeInTheDocument();
    });
  });

  it('should show distribution rows after data loads', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1, DIST_2], totalElements: 2, totalPages: 1,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Parquet Snapshot')).toBeInTheDocument();
      expect(screen.getByText('CSV Export')).toBeInTheDocument();
    });
  });

  it('should link each distribution to its detail page', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 1, totalPages: 1,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      const link = screen.getByRole('link', { name: 'Parquet Snapshot' });
      expect(link).toHaveAttribute(
        'href',
        `/default/datasets/${DIST_1.datasetId}/distributions/dist-1`,
      );
    });
  });

  it('should link each dataset cell to the parent dataset', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 1, totalPages: 1,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      const datasetLink = screen.getByRole('link', { name: /ds-aabbc/i });
      expect(datasetLink).toHaveAttribute('href', `/default/datasets/${DIST_1.datasetId}`);
    });
  });

  it('should render format badge with label', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 1, totalPages: 1,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Parquet')).toBeInTheDocument();
    });
  });

  it('should not show pagination when only one page', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 1, totalPages: 1,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => screen.getByText('Parquet Snapshot'));
    expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
  });

  it('should show pagination when totalPages > 1', async () => {
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 25, totalPages: 2,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
    });
  });

  it('should navigate to next page when Next is clicked', async () => {
    const user = userEvent.setup();
    mockedDistributionApi.list.mockResolvedValue({
      ...EMPTY_PAGE, content: [DIST_1], totalElements: 25, totalPages: 2,
    });
    render(<DistributionsPage />, { wrapper });
    await waitFor(() => screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => {
      const calls = mockedDistributionApi.list.mock.calls;
      const lastCall = calls[calls.length - 1][0];
      expect(lastCall?.page).toBe(1);
    });
  });
});
