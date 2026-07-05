import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { normalizeLineageGraph, lineageApi } from './lineage';

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null), setItem: vi.fn(), removeItem: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('normalizeLineageGraph', () => {
  it('should map fromId/toId edge fields', () => {
    const raw = {
      rootId: 'root-uuid',
      rootNamespace: 'ns',
      rootName: 'ds',
      direction: 'downstream',
      depth: 3,
      nodes: [],
      edges: [{ fromId: 'uuid-a', toId: 'uuid-b' }],
    };
    const result = normalizeLineageGraph(raw);
    expect(result.edges[0]).toEqual({
      fromId: 'uuid-a',
      toId: 'uuid-b',
      edgeType: 'DERIVED_FROM',
    });
  });

  it('should map node id and catalogId fields', () => {
    const raw = {
      rootId: 'root-uuid',
      rootNamespace: 'ns',
      rootName: 'ds',
      direction: 'upstream',
      depth: 2,
      nodes: [{ id: 'node-uuid', namespace: 'ns1', name: 'table_a', depth: 1, catalogId: 'cat-uuid' }],
      edges: [],
    };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes[0].id).toBe('node-uuid');
    expect(result.nodes[0].type).toBe('Dataset');
    expect(result.nodes[0].namespace).toBe('ns1');
    expect(result.nodes[0].depth).toBe(1);
    expect(result.nodes[0].catalogId).toBe('cat-uuid');
  });

  it('should handle missing catalogId on nodes', () => {
    const raw = {
      rootId: 'root-uuid',
      rootNamespace: 'ns',
      rootName: 'ds',
      direction: 'upstream',
      depth: 2,
      nodes: [{ id: 'node-uuid', namespace: 'ns1', name: 'table_a', depth: 1 }],
      edges: [],
    };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes[0].catalogId).toBeUndefined();
  });

  it('should handle empty nodes and edges', () => {
    const raw = { rootId: 'r', rootNamespace: 'ns', rootName: 'ds', direction: 'downstream', depth: 1, nodes: [], edges: [] };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes).toEqual([]);
    expect(result.edges).toEqual([]);
  });

  it('should handle missing edges/nodes (null coalesce to [])', () => {
    const raw = { rootId: 'r', rootNamespace: 'ns', rootName: 'ds', direction: 'downstream', depth: 1 };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes).toEqual([]);
    expect(result.edges).toEqual([]);
  });

  it('should preserve root metadata including rootId', () => {
    const raw = { rootId: 'root-uuid', rootNamespace: 'myNs', rootName: 'myDs', direction: 'upstream', depth: 5, nodes: [], edges: [] };
    const result = normalizeLineageGraph(raw);
    expect(result.rootId).toBe('root-uuid');
    expect(result.rootNamespace).toBe('myNs');
    expect(result.rootName).toBe('myDs');
    expect(result.direction).toBe('upstream');
    expect(result.depth).toBe(5);
  });
});

describe('lineageApi.getDatasetLineage', () => {
  it('should URL-encode the dataset UUID in the path', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ rootId: 'r', rootNamespace: 'a', rootName: 'b', direction: 'downstream', depth: 5, nodes: [], edges: [] }),
    });
    vi.stubGlobal('fetch', mockFetch);

    await lineageApi.getDatasetLineage('some-uuid');

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('/api/v1/datasets/some-uuid/lineage');
  });

  it('should default to downstream direction and depth 5', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true, status: 200,
      json: () => Promise.resolve({ rootId: 'r', rootNamespace: '', rootName: '', direction: 'downstream', depth: 5, nodes: [], edges: [] }),
    });
    vi.stubGlobal('fetch', mockFetch);

    await lineageApi.getDatasetLineage('some-uuid');

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('direction=downstream');
    expect(url).toContain('depth=5');
  });
});

describe('lineageApi.submitDdl', () => {
  it('should POST to /api/v1/ddl/submit with the body', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 204, text: () => Promise.resolve('') });
    vi.stubGlobal('fetch', mockFetch);

    await lineageApi.submitDdl({ ddl: 'CREATE VIEW v AS SELECT * FROM t', dialect: 'SNOWFLAKE' });

    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/ddl/submit');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body)).toEqual({ ddl: 'CREATE VIEW v AS SELECT * FROM t', dialect: 'SNOWFLAKE' });
  });
});
