import { get, post, put } from './client';
import type { LineageGraph, LineageIdentity, ColumnLineage, LineageRun } from '../types/lineage';

const BASE = '/api/v1';

/**
 * Normalises raw backend lineage response into the typed LineageGraph.
 * Extracted as a pure function so it can be unit-tested without HTTP.
 */
export function normalizeLineageGraph(raw: Record<string, unknown>): LineageGraph {
  // Normalise backend edge keys: {from_ns, from_name, to_ns, to_name} → LineageEdge
  const edges = ((raw.edges ?? []) as Record<string, unknown>[]).map(e => ({
    fromNamespace: e['from_ns'] as string,
    fromName:      e['from_name'] as string,
    toNamespace:   e['to_ns'] as string,
    toName:        e['to_name'] as string,
    edgeType:      'DERIVED_FROM' as const,
  }));
  // Nodes returned as {namespace, name, depth} — add default type
  const nodes = ((raw.nodes ?? []) as Record<string, unknown>[]).map(n => ({
    namespace: n['namespace'] as string,
    name:      n['name'] as string,
    depth:     n['depth'] as number | undefined,
    type:      'Dataset' as const,
  }));
  return {
    rootNamespace: raw['rootNamespace'] as string,
    rootName:      raw['rootName'] as string,
    direction:     raw['direction'] as 'upstream' | 'downstream',
    depth:         raw['depth'] as number,
    nodes,
    edges,
  };
}

async function getLineageGraph(url: string): Promise<LineageGraph> {
  const raw = await get<Record<string, unknown>>(url);
  return normalizeLineageGraph(raw);
}

export const lineageApi = {
  getDatasetLineage: (ns: string, name: string, direction: 'upstream' | 'downstream' = 'downstream', depth = 5) =>
    getLineageGraph(`${BASE}/datasets/${encodeURIComponent(ns)}/${encodeURIComponent(name)}/lineage?direction=${direction}&depth=${depth}`),
  getColumnLineage: (ns: string, name: string, column: string, direction: 'upstream' | 'downstream' = 'downstream') =>
    get<ColumnLineage[]>(`${BASE}/datasets/${encodeURIComponent(ns)}/${encodeURIComponent(name)}/column-lineage?column=${encodeURIComponent(column)}&direction=${direction}`),
  getImpact: (ns: string, name: string) =>
    getLineageGraph(`${BASE}/datasets/${encodeURIComponent(ns)}/${encodeURIComponent(name)}/impact`),
  getRun: (runId: string) => get<LineageRun>(`${BASE}/runs/${runId}`),
  submitDdl: (body: { ddl: string; dialect?: string; namespace?: string }) =>
    post<void>(`${BASE}/ddl/submit`, body),
  getCatalogLineageIdentity: (catalogId: string) =>
    get<LineageIdentity>(`${BASE}/catalog-datasets/${catalogId}/lineage-identity`),
  linkCatalogResource: (ns: string, name: string, catalogResourceId: string) =>
    put<void>(`${BASE}/datasets/${encodeURIComponent(ns)}/${encodeURIComponent(name)}/catalog-link`, { catalogResourceId }),
  getCatalogId: (ns: string, name: string) =>
    get<{ catalogId: string }>(`${BASE}/datasets/${encodeURIComponent(ns)}/${encodeURIComponent(name)}/catalog-link`),
};
