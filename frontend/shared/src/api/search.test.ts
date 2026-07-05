import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { searchApi } from './search';

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null), setItem: vi.fn(), removeItem: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

function mockOk(body: unknown) {
  return vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve(body) });
}

describe('searchApi.search', () => {
  it('should build query string from params', async () => {
    const mockFetch = mockOk({ results: [], total: 0, page: 0, size: 20, facets: {} });
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.search({ q: 'trades', type: 'DATASET', page: 0, size: 20 });

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('q=trades');
    expect(url).toContain('type=DATASET');
    expect(url).toContain('page=0');
  });

  it('should omit undefined values from query string', async () => {
    const mockFetch = mockOk({ results: [], total: 0, page: 0, size: 20, facets: {} });
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.search({ q: 'test', type: undefined });

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).not.toContain('type=');
  });

  it('should omit empty string values from query string', async () => {
    const mockFetch = mockOk({ results: [], total: 0, page: 0, size: 20, facets: {} });
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.search({ q: '', type: 'DATASET' });

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).not.toContain('q=');
  });

  it('should GET /api/v1/search with no params when all params are undefined', async () => {
    const mockFetch = mockOk({ results: [], total: 0, page: 0, size: 20, facets: {} });
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.search({});

    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/search');
  });
});

describe('searchApi.suggest', () => {
  it('should URL-encode the query parameter', async () => {
    const mockFetch = mockOk([]);
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.suggest('trade & risk');

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('q=trade%20%26%20risk');
  });
});

describe('searchApi.createSaved', () => {
  it('should POST to /api/v1/search/saved with name and query', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: '1' }) });
    vi.stubGlobal('fetch', mockFetch);

    await searchApi.createSaved({ name: 'My Search', query: { q: 'trades' } });

    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/search/saved');
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.name).toBe('My Search');
    expect(body.query.q).toBe('trades');
  });
});
