import { create } from 'zustand';
import type { SearchRequest, SearchResponse } from '@datacatalog/shared';

interface SearchState {
  query: string;
  filters: Omit<SearchRequest, 'q' | 'page' | 'size'>;
  page: number;
  results: SearchResponse | null;
  isLoading: boolean;
  setQuery: (q: string) => void;
  setFilter: (key: string, value: string | boolean | undefined) => void;
  clearFilters: () => void;
  setPage: (page: number) => void;
  setResults: (results: SearchResponse | null) => void;
  setLoading: (loading: boolean) => void;
}

export const useSearchStore = create<SearchState>((set) => ({
  query: '',
  filters: {},
  page: 0,
  results: null,
  isLoading: false,
  setQuery: (query) => set({ query, page: 0 }),
  setFilter: (key, value) =>
    set((s) => ({ filters: { ...s.filters, [key]: value }, page: 0 })),
  clearFilters: () => set({ filters: {}, page: 0 }),
  setPage: (page) => set({ page }),
  setResults: (results) => set({ results }),
  setLoading: (isLoading) => set({ isLoading }),
}));
