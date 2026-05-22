import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FacetPanel, { humanizeConcept } from './FacetPanel';
import { useSearchStore } from '../store/searchStore';
import type { SearchFacets } from '@datacatalog/shared';

vi.mock('../store/searchStore', () => ({
  useSearchStore: vi.fn(),
}));

const mockSetFilter = vi.fn();
const mockClearFilters = vi.fn();
const mockUseSearchStore = useSearchStore as unknown as ReturnType<typeof vi.fn>;

const emptyFacets: SearchFacets = {
  entityTypes: [],
  formats: [],
  lifecycleStatuses: [],
  vocabularyTypes: [],
  fiboConcepts: [],
  keywords: [],
  themes: [],
  vocabConcepts: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSearchStore.mockReturnValue({
    filters: {},
    setFilter: mockSetFilter,
    clearFilters: mockClearFilters,
  });
});

describe('humanizeConcept', () => {
  it('should extract fragment after last / # or :', () => {
    expect(humanizeConcept('https://spec.edmcouncil.org/fibo/ontology/FND/MonetaryAmount')).toBe('Monetary Amount');
    expect(humanizeConcept('schema:Price')).toBe('Price');
    expect(humanizeConcept('http://example.org#TradeDate')).toBe('Trade Date');
  });

  it('should handle plain strings with no delimiter', () => {
    expect(humanizeConcept('PlainWord')).toBe('Plain Word');
  });
});

describe('FacetPanel', () => {
  it('should render the Filters heading', () => {
    render(<FacetPanel facets={emptyFacets} />);
    expect(screen.getByText('Filters')).toBeInTheDocument();
  });

  it('should not render facet groups for empty facets', () => {
    render(<FacetPanel facets={emptyFacets} />);
    expect(screen.queryByText('Type')).toBeNull();
    expect(screen.queryByText('Format')).toBeNull();
  });

  it('should render entity type facets', () => {
    const facets: SearchFacets = {
      ...emptyFacets,
      entityTypes: [
        { key: 'DATASET', count: 42 },
        { key: 'DATA_PRODUCT', count: 10 },
      ],
    };
    render(<FacetPanel facets={facets} />);
    expect(screen.getByText('Dataset')).toBeInTheDocument();
    expect(screen.getByText('Data Product')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
  });

  it('should call setFilter with the facet key when a facet is clicked', async () => {
    const facets: SearchFacets = {
      ...emptyFacets,
      entityTypes: [{ key: 'DATASET', count: 5 }],
    };
    render(<FacetPanel facets={facets} />);
    await userEvent.click(screen.getByText('Dataset'));
    expect(mockSetFilter).toHaveBeenCalledWith('type', 'DATASET');
  });

  it('should call setFilter with undefined when active facet is clicked (deselect)', async () => {
    mockUseSearchStore.mockReturnValue({
      filters: { type: 'DATASET' },
      setFilter: mockSetFilter,
      clearFilters: mockClearFilters,
    });
    const facets: SearchFacets = {
      ...emptyFacets,
      entityTypes: [{ key: 'DATASET', count: 5 }],
    };
    render(<FacetPanel facets={facets} />);
    await userEvent.click(screen.getByText('Dataset'));
    expect(mockSetFilter).toHaveBeenCalledWith('type', undefined);
  });

  it('should show Clear all button when filters are active', () => {
    mockUseSearchStore.mockReturnValue({
      filters: { type: 'DATASET' },
      setFilter: mockSetFilter,
      clearFilters: mockClearFilters,
    });
    render(<FacetPanel facets={emptyFacets} />);
    expect(screen.getByText('Clear all')).toBeInTheDocument();
  });

  it('should not show Clear all when no filters active', () => {
    render(<FacetPanel facets={emptyFacets} />);
    expect(screen.queryByText('Clear all')).toBeNull();
  });

  it('should call clearFilters when Clear all is clicked', async () => {
    mockUseSearchStore.mockReturnValue({
      filters: { type: 'DATASET' },
      setFilter: mockSetFilter,
      clearFilters: mockClearFilters,
    });
    render(<FacetPanel facets={emptyFacets} />);
    await userEvent.click(screen.getByText('Clear all'));
    expect(mockClearFilters).toHaveBeenCalledOnce();
  });

  it('should collapse panel when collapse button is clicked', async () => {
    render(<FacetPanel facets={emptyFacets} />);
    await userEvent.click(screen.getByTitle('Collapse'));
    expect(screen.queryByText('Filters')).toBeNull();
    expect(screen.getByTitle('Expand filters')).toBeInTheDocument();
  });

  it('should re-expand when expand button is clicked', async () => {
    render(<FacetPanel facets={emptyFacets} />);
    await userEvent.click(screen.getByTitle('Collapse'));
    await userEvent.click(screen.getByTitle('Expand filters'));
    expect(screen.getByText('Filters')).toBeInTheDocument();
  });

  it('should render Has Lineage checkbox', () => {
    render(<FacetPanel facets={emptyFacets} />);
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });

  it('should call setFilter with true when Has Lineage checkbox is checked', async () => {
    render(<FacetPanel facets={emptyFacets} />);
    await userEvent.click(screen.getByRole('checkbox'));
    expect(mockSetFilter).toHaveBeenCalledWith('hasLineage', true);
  });

  it('should show FIBO concepts section when fiboConcepts are present', () => {
    const facets: SearchFacets = {
      ...emptyFacets,
      fiboConcepts: [{ key: 'https://fibo.org/MonetaryAmount', count: 3 }],
    };
    render(<FacetPanel facets={facets} />);
    expect(screen.getByText('FIBO Concepts')).toBeInTheDocument();
    expect(screen.getByText('Monetary Amount')).toBeInTheDocument();
  });
});
