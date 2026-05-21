import { get, post, del } from './client';
import type { HarvestSource, HarvestJob, HarvestRun, HarvestRunItem } from '../types/harvest';

const BASE = '/api/v1';

export const harvestSourceApi = {
  list: (params?: { type?: string }) => {
    const q = new URLSearchParams(params as Record<string, string>).toString();
    return get<HarvestSource[]>(`${BASE}/sources${q ? '?' + q : ''}`);
  },
  get: (id: string) => get<HarvestSource>(`${BASE}/sources/${id}`),
  create: (body: Partial<HarvestSource>) => post<HarvestSource>(`${BASE}/sources`, body),
  test: (id: string) => post<{ success: boolean; message?: string }>(`${BASE}/sources/${id}/test`),
  delete: (id: string) => del<void>(`${BASE}/sources/${id}`),
};

export const harvestJobApi = {
  list: () => get<HarvestJob[]>(`${BASE}/jobs`),
  get: (id: string) => get<HarvestJob>(`${BASE}/jobs/${id}`),
  create: (body: Partial<HarvestJob>) => post<HarvestJob>(`${BASE}/jobs`, body),
  trigger: (id: string) => post<HarvestRun>(`${BASE}/jobs/${id}/trigger`),
  cancel: (id: string) => post<void>(`${BASE}/jobs/${id}/cancel`),
};

export const harvestRunApi = {
  get: (id: string) => get<HarvestRun>(`${BASE}/runs/${id}`),
  listItems: (id: string) => get<HarvestRunItem[]>(`${BASE}/runs/${id}/items`),
};
