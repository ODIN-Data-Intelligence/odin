import { describe, it, expect, beforeEach } from 'vitest';
import { useSearchStore } from './searchStore';

beforeEach(() => {
  useSearchStore.setState({ query: '', filters: {}, page: 0, results: null, isLoading: false });
});

describe('useSearchStore', () => {
  describe('initial state', () => {
    it('should have empty query', () => {
      expect(useSearchStore.getState().query).toBe('');
    });

    it('should have empty filters', () => {
      expect(useSearchStore.getState().filters).toEqual({});
    });

    it('should start on page 0', () => {
      expect(useSearchStore.getState().page).toBe(0);
    });

    it('should have null results', () => {
      expect(useSearchStore.getState().results).toBeNull();
    });

    it('should not be loading', () => {
      expect(useSearchStore.getState().isLoading).toBe(false);
    });
  });

  describe('setQuery', () => {
    it('should update the query', () => {
      useSearchStore.getState().setQuery('trades');
      expect(useSearchStore.getState().query).toBe('trades');
    });

    it('should reset page to 0', () => {
      useSearchStore.setState({ page: 3 });
      useSearchStore.getState().setQuery('new search');
      expect(useSearchStore.getState().page).toBe(0);
    });
  });

  describe('setFilter', () => {
    it('should add a filter', () => {
      useSearchStore.getState().setFilter('type', 'DATASET');
      expect(useSearchStore.getState().filters.type).toBe('DATASET');
    });

    it('should preserve existing filters when adding a new one', () => {
      useSearchStore.setState({ filters: { type: 'DATASET' } });
      useSearchStore.getState().setFilter('format', 'text/csv');
      expect(useSearchStore.getState().filters.type).toBe('DATASET');
      expect(useSearchStore.getState().filters.format).toBe('text/csv');
    });

    it('should allow unsetting a filter with undefined', () => {
      useSearchStore.setState({ filters: { type: 'DATASET' } });
      useSearchStore.getState().setFilter('type', undefined);
      expect(useSearchStore.getState().filters.type).toBeUndefined();
    });

    it('should reset page to 0 when filter changes', () => {
      useSearchStore.setState({ page: 5 });
      useSearchStore.getState().setFilter('type', 'DATASET');
      expect(useSearchStore.getState().page).toBe(0);
    });
  });

  describe('clearFilters', () => {
    it('should remove all filters', () => {
      useSearchStore.setState({ filters: { type: 'DATASET', format: 'text/csv' } });
      useSearchStore.getState().clearFilters();
      expect(useSearchStore.getState().filters).toEqual({});
    });

    it('should reset page to 0', () => {
      useSearchStore.setState({ page: 4, filters: { type: 'DATASET' } });
      useSearchStore.getState().clearFilters();
      expect(useSearchStore.getState().page).toBe(0);
    });
  });

  describe('setPage', () => {
    it('should update the page', () => {
      useSearchStore.getState().setPage(3);
      expect(useSearchStore.getState().page).toBe(3);
    });

    it('should not reset filters', () => {
      useSearchStore.setState({ filters: { type: 'DATASET' } });
      useSearchStore.getState().setPage(2);
      expect(useSearchStore.getState().filters.type).toBe('DATASET');
    });
  });

  describe('setResults', () => {
    it('should store results', () => {
      const results = { results: [], total: 0, page: 0, size: 20, facets: {} };
      useSearchStore.getState().setResults(results as never);
      expect(useSearchStore.getState().results).toBe(results);
    });

    it('should allow clearing results with null', () => {
      useSearchStore.setState({ results: { results: [], total: 0, page: 0, size: 20, facets: {} } as never });
      useSearchStore.getState().setResults(null);
      expect(useSearchStore.getState().results).toBeNull();
    });
  });

  describe('setLoading', () => {
    it('should set isLoading to true', () => {
      useSearchStore.getState().setLoading(true);
      expect(useSearchStore.getState().isLoading).toBe(true);
    });

    it('should set isLoading to false', () => {
      useSearchStore.setState({ isLoading: true });
      useSearchStore.getState().setLoading(false);
      expect(useSearchStore.getState().isLoading).toBe(false);
    });
  });
});
