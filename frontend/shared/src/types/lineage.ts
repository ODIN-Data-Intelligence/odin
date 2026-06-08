export interface LineageNode {
  id: string;
  namespace: string;
  name: string;
  type: 'Dataset' | 'Job';
  depth?: number;
  catalogId?: string;
  facets?: Record<string, unknown>;
}

export interface LineageEdge {
  fromId: string;
  toId: string;
  edgeType: 'DERIVED_FROM' | 'READ_BY' | 'WRITES_TO' | 'COLUMN_LINEAGE';
}

export interface LineageGraph {
  rootId: string;
  rootNamespace: string;
  rootName: string;
  nodes: LineageNode[];
  edges: LineageEdge[];
  direction: 'upstream' | 'downstream';
  depth: number;
}

export interface LineageIdentity {
  id: string;
  namespace: string;
  name: string;
}

export interface ColumnLineage {
  outputDatasetNamespace: string;
  outputDatasetName: string;
  outputColumn: string;
  inputDatasetNamespace: string;
  inputDatasetName: string;
  inputColumn: string;
  transformationType?: string;
}

export interface LineageRun {
  id: string;
  runId: string;
  jobId: string;
  eventType: string;
  eventTime: string;
  producer: string;
  inputs: LineageDatasetRef[];
  outputs: LineageDatasetRef[];
  facets?: Record<string, unknown>;
}

export interface LineageDatasetRef {
  namespace: string;
  name: string;
  facets?: Record<string, unknown>;
}
