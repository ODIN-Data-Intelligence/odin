import { get, post, del } from './client';
import type { SearchResponse, SearchRequest, SavedSearch } from '../types/search';

const BASE = '/api/v1';

export const searchApi = {
  search: (params: SearchRequest) => {
    const q = new URLSearchParams(
      Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)]))
    ).toString();
    return get<SearchResponse>(`${BASE}/search${q ? '?' + q : ''}`);
  },
  suggest: (q: string) =>
    get<string[]>(`${BASE}/search/suggest?q=${encodeURIComponent(q)}`),
  listSaved: () => get<SavedSearch[]>(`${BASE}/search/saved`),
  createSaved: (body: { name: string; query: SearchRequest }) =>
    post<SavedSearch>(`${BASE}/search/saved`, body),
  deleteSaved: (id: string) => del<void>(`${BASE}/search/saved/${id}`),
  reindex: () => post<void>(`${BASE}/admin/reindex`),
};
