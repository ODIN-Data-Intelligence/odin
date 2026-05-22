import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { normalizeLineageGraph, lineageApi } from './lineage';

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null), setItem: vi.fn(), removeItem: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('normalizeLineageGraph', () => {
  it('should map snake_case edge keys to camelCase', () => {
    const raw = {
      rootNamespace: 'ns',
      rootName: 'ds',
      direction: 'downstream',
      depth: 3,
      nodes: [],
      edges: [{ from_ns: 'ns1', from_name: 'a', to_ns: 'ns2', to_name: 'b' }],
    };
    const result = normalizeLineageGraph(raw);
    expect(result.edges[0]).toEqual({
      fromNamespace: 'ns1',
      fromName: 'a',
      toNamespace: 'ns2',
      toName: 'b',
      edgeType: 'DERIVED_FROM',
    });
  });

  it('should add type: Dataset to all nodes', () => {
    const raw = {
      rootNamespace: 'ns',
      rootName: 'ds',
      direction: 'upstream',
      depth: 2,
      nodes: [{ namespace: 'ns1', name: 'table_a', depth: 1 }],
      edges: [],
    };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes[0].type).toBe('Dataset');
    expect(result.nodes[0].namespace).toBe('ns1');
    expect(result.nodes[0].depth).toBe(1);
  });

  it('should handle empty nodes and edges', () => {
    const raw = { rootNamespace: 'ns', rootName: 'ds', direction: 'downstream', depth: 1, nodes: [], edges: [] };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes).toEqual([]);
    expect(result.edges).toEqual([]);
  });

  it('should handle missing edges/nodes (null coalesce to [])', () => {
    const raw = { rootNamespace: 'ns', rootName: 'ds', direction: 'downstream', depth: 1 };
    const result = normalizeLineageGraph(raw);
    expect(result.nodes).toEqual([]);
    expect(result.edges).toEqual([]);
  });

  it('should preserve root metadata', () => {
    const raw = { rootNamespace: 'myNs', rootName: 'myDs', direction: 'upstream', depth: 5, nodes: [], edges: [] };
    const result = normalizeLineageGraph(raw);
    expect(result.rootNamespace).toBe('myNs');
    expect(result.rootName).toBe('myDs');
    expect(result.direction).toBe('upstream');
    expect(result.depth).toBe(5);
  });
});

describe('lineageApi.getDatasetLineage', () => {
  it('should URL-encode namespace and name', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ rootNamespace: 'a', rootName: 'b', direction: 'downstream', depth: 5, nodes: [], edges: [] }),
    });
    vi.stubGlobal('fetch', mockFetch);

    await lineageApi.getDatasetLineage('my ns', 'my/ds');

    const url: string = mockFetch.mock.calls[0][0];
    expect(url).toContain('my%20ns');
    expect(url).toContain('my%2Fds');
  });

  it('should default to downstream direction and depth 5', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true, status: 200,
      json: () => Promise.resolve({ rootNamespace: '', rootName: '', direction: 'downstream', depth: 5, nodes: [], edges: [] }),
    });
    vi.stubGlobal('fetch', mockFetch);

    await lineageApi.getDatasetLineage('ns', 'ds');

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
