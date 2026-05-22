import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { catalogApi, datasetApi, dataProductApi, vocabularyApi, logicalModelApi, logicalElementApi, vocabMappingApi } from './catalog';

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null), setItem: vi.fn(), removeItem: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

function mockOk(body: unknown, status = 200) {
  return vi.fn().mockResolvedValue({ ok: true, status, json: () => Promise.resolve(body) });
}
function mockNoContent() {
  return vi.fn().mockResolvedValue({ ok: true, status: 204, text: () => Promise.resolve('') });
}

describe('catalogApi', () => {
  it('list should GET /api/v1/catalogs', async () => {
    const mockFetch = mockOk([]);
    vi.stubGlobal('fetch', mockFetch);
    await catalogApi.list();
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/catalogs');
  });

  it('get should GET /api/v1/catalogs/:id', async () => {
    const mockFetch = mockOk({ id: 'abc' });
    vi.stubGlobal('fetch', mockFetch);
    await catalogApi.get('abc');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/catalogs/abc');
  });

  it('export should GET /api/v1/catalogs/:id/export', async () => {
    const mockFetch = mockOk({});
    vi.stubGlobal('fetch', mockFetch);
    await catalogApi.export('abc');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/catalogs/abc/export');
  });
});

describe('datasetApi', () => {
  it('list should GET /api/v1/datasets without params', async () => {
    const mockFetch = mockOk({ content: [], totalElements: 0 });
    vi.stubGlobal('fetch', mockFetch);
    await datasetApi.list();
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/datasets');
  });

  it('list should append query params when provided', async () => {
    const mockFetch = mockOk({ content: [] });
    vi.stubGlobal('fetch', mockFetch);
    await datasetApi.list({ domain: 'finance', page: 1, size: 10 });
    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('domain=finance');
    expect(url).toContain('page=1');
    expect(url).toContain('size=10');
  });

  it('delete should call DELETE on /api/v1/datasets/:id', async () => {
    const mockFetch = mockNoContent();
    vi.stubGlobal('fetch', mockFetch);
    await datasetApi.delete('ds-1');
    expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/datasets/ds-1');
  });

  it('suggestElementMappings should include modelId in URL', async () => {
    const mockFetch = mockOk([]);
    vi.stubGlobal('fetch', mockFetch);
    await datasetApi.suggestElementMappings('dist-1', 'model-1');
    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('modelId=model-1');
  });
});

describe('dataProductApi', () => {
  it('patchLifecycle should PATCH with status body', async () => {
    const mockFetch = mockOk({ id: '1' });
    vi.stubGlobal('fetch', mockFetch);
    await dataProductApi.patchLifecycle('dp-1', 'Deploy');
    expect(mockFetch.mock.calls[0][1].method).toBe('PATCH');
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.status).toBe('Deploy');
  });

  it('linkDataset should POST with datasetId body', async () => {
    const mockFetch = mockNoContent();
    vi.stubGlobal('fetch', mockFetch);
    await dataProductApi.linkDataset('dp-1', 'ds-2');
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.datasetId).toBe('ds-2');
  });

  it('unlinkDataset should DELETE /data-products/:id/datasets/:datasetId', async () => {
    const mockFetch = mockNoContent();
    vi.stubGlobal('fetch', mockFetch);
    await dataProductApi.unlinkDataset('dp-1', 'ds-2');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/data-products/dp-1/datasets/ds-2');
    expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
  });
});

describe('vocabularyApi', () => {
  it('searchConcepts should URL-encode query and set limit', async () => {
    const mockFetch = mockOk([]);
    vi.stubGlobal('fetch', mockFetch);
    await vocabularyApi.searchConcepts('vocab-1', 'monetary amount', 10);
    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('q=monetary%20amount');
    expect(url).toContain('limit=10');
  });

  it('searchConcepts should default to limit 20', async () => {
    const mockFetch = mockOk([]);
    vi.stubGlobal('fetch', mockFetch);
    await vocabularyApi.searchConcepts('vocab-1', 'price');
    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('limit=20');
  });
});

describe('logicalModelApi', () => {
  it('patchStatus should PATCH with status body', async () => {
    const mockFetch = mockOk({ id: 'lm-1' });
    vi.stubGlobal('fetch', mockFetch);
    await logicalModelApi.patchStatus('lm-1', 'published');
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.status).toBe('published');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/logical-models/lm-1/status');
  });

  it('suggestMappings should POST to suggest-mappings', async () => {
    const mockFetch = mockNoContent();
    vi.stubGlobal('fetch', mockFetch);
    await logicalModelApi.suggestMappings('lm-1');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/logical-models/lm-1/suggest-mappings');
    expect(mockFetch.mock.calls[0][1].method).toBe('POST');
  });
});

describe('logicalElementApi', () => {
  it('bind should POST with physicalColumnId body', async () => {
    const mockFetch = mockOk({ id: 'el-1' });
    vi.stubGlobal('fetch', mockFetch);
    await logicalElementApi.bind('el-1', 'col-99');
    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.physicalColumnId).toBe('col-99');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/logical-data-elements/el-1/bind');
  });

  it('unbind should DELETE the bind endpoint', async () => {
    const mockFetch = mockOk({ id: 'el-1' });
    vi.stubGlobal('fetch', mockFetch);
    await logicalElementApi.unbind('el-1');
    expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/logical-data-elements/el-1/bind');
  });
});

describe('vocabMappingApi', () => {
  it('delete should DELETE /api/v1/vocab-mappings/:id', async () => {
    const mockFetch = mockNoContent();
    vi.stubGlobal('fetch', mockFetch);
    await vocabMappingApi.delete('mapping-1');
    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/vocab-mappings/mapping-1');
    expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
  });
});
