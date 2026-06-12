import { create } from 'zustand';
import type { SearchRequest, SearchResponse } from '@datacatalog/shared';

interface SearchState {
  query: string;
  filters: Omit<SearchRequest, 'q' | 'page' | 'size'>;
  page: number;
  size: number;
  results: SearchResponse | null;
  isLoading: boolean;
  setQuery: (q: string) => void;
  setFilter: (key: string, value: string | boolean | undefined) => void;
  clearFilters: () => void;
  setPage: (page: number) => void;
  setSize: (size: number) => void;
  setResults: (results: SearchResponse | null) => void;
  setLoading: (loading: boolean) => void;
}

const DEFAULT_FILTERS: Omit<SearchRequest, 'q' | 'page' | 'size'> = { types: 'DATASET,DATA_PRODUCT' };

export const useSearchStore = create<SearchState>((set) => ({
  query: '',
  filters: { ...DEFAULT_FILTERS },
  page: 0,
  size: 20,
  results: null,
  isLoading: false,
  setQuery: (query) => set({ query, page: 0 }),
  setFilter: (key, value) =>
    set((s) => ({ filters: { ...s.filters, [key]: value }, page: 0 })),
  clearFilters: () => set({ filters: { ...DEFAULT_FILTERS }, page: 0 }),
  setPage: (page) => set({ page }),
  setSize: (size) => set({ size, page: 0 }),
  setResults: (results) => set({ results }),
  setLoading: (isLoading) => set({ isLoading }),
}));
