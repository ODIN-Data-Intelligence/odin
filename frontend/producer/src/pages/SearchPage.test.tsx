import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import * as sharedApi from '@datacatalog/shared';
import type { SearchResponse, SearchResult } from '@datacatalog/shared';
import SearchPage from './SearchPage';

vi.mock('@datacatalog/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@datacatalog/shared')>();
  return {
    ...actual,
    searchApi: {
      search: vi.fn(),
      suggest: vi.fn(),
    },
  };
});

const mockedSearchApi = vi.mocked(sharedApi.searchApi);

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/default/search']}>
        <Routes>
          <Route path="/:tenant/search" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const EMPTY_RESPONSE: SearchResponse = { results: [], total: 0, page: 0, size: 20, facets: {} };

const DATASET_RESULT: SearchResult = {
  id: 'ds-1', entityType: 'DATASET', tenantId: 'default',
  title: 'Trade Data', description: 'Position data', keywords: ['risk'],
};

const DP_RESULT: SearchResult = {
  id: 'dp-1', entityType: 'DATA_PRODUCT', tenantId: 'default',
  title: 'Risk Product', lifecycleStatus: 'Build',
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedSearchApi.search.mockResolvedValue(EMPTY_RESPONSE);
  mockedSearchApi.suggest.mockResolvedValue([]);
});

describe('SearchPage', () => {
  it('should render the search input', async () => {
    render(<SearchPage />, { wrapper });
    expect(screen.getByPlaceholderText('Search the catalog…')).toBeInTheDocument();
  });

  it('should render all four type filter pills', async () => {
    render(<SearchPage />, { wrapper });
    expect(screen.getByRole('button', { name: 'All' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Data Product' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Dataset' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Distribution' })).toBeInTheDocument();
  });

  it('should show empty state with prompt when no query', async () => {
    render(<SearchPage />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText(/enter a search term/i)).toBeInTheDocument();
    });
  });

  it('should call searchApi.search with type param when type pill clicked', async () => {
    const user = userEvent.setup();
    mockedSearchApi.search.mockResolvedValue({
      ...EMPTY_RESPONSE,
      results: [DATASET_RESULT],
      total: 1,
    });
    render(<SearchPage />, { wrapper });
    await user.click(screen.getByRole('button', { name: 'Dataset' }));
    await waitFor(() => {
      expect(mockedSearchApi.search).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'DATASET' }),
      );
    });
  });

  it('should show result cards after search resolves', async () => {
    const user = userEvent.setup();
    mockedSearchApi.search.mockResolvedValue({
      ...EMPTY_RESPONSE,
      results: [DATASET_RESULT, DP_RESULT],
      total: 2,
    });
    render(<SearchPage />, { wrapper });
    await user.type(screen.getByPlaceholderText('Search the catalog…'), 'risk');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      expect(screen.getByText('Trade Data')).toBeInTheDocument();
      expect(screen.getByText('Risk Product')).toBeInTheDocument();
    });
  });

  it('should show "no results" message including query when results empty', async () => {
    const user = userEvent.setup();
    render(<SearchPage />, { wrapper });
    await user.type(screen.getByPlaceholderText('Search the catalog…'), 'xyz123');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      expect(screen.getByText(/no results for "xyz123"/i)).toBeInTheDocument();
    });
  });

  it('should show result count when results exist', async () => {
    mockedSearchApi.search.mockResolvedValue({
      ...EMPTY_RESPONSE,
      results: [DATASET_RESULT],
      total: 42,
    });
    const user = userEvent.setup();
    render(<SearchPage />, { wrapper });
    await user.type(screen.getByPlaceholderText('Search the catalog…'), 'trade');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      expect(screen.getByText('42 results')).toBeInTheDocument();
    });
  });

  it('should show pagination when totalPages > 1', async () => {
    mockedSearchApi.search.mockResolvedValue({
      ...EMPTY_RESPONSE,
      results: [DATASET_RESULT],
      total: 50,
    });
    const user = userEvent.setup();
    render(<SearchPage />, { wrapper });
    await user.type(screen.getByPlaceholderText('Search the catalog…'), 'trade');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
    });
  });

  it('should reset page to 0 when type filter changes', async () => {
    const user = userEvent.setup();
    mockedSearchApi.search.mockResolvedValue({
      ...EMPTY_RESPONSE,
      results: [DATASET_RESULT],
      total: 50,
    });
    render(<SearchPage />, { wrapper });
    await user.type(screen.getByPlaceholderText('Search the catalog…'), 'trade');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Next' }));
    await user.click(screen.getByRole('button', { name: 'Dataset' }));
    await waitFor(() => {
      const calls = mockedSearchApi.search.mock.calls;
      const lastCall = calls[calls.length - 1][0];
      expect(lastCall.page).toBe(0);
    });
  });
});
