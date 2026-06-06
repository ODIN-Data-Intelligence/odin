import { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { lineageApi } from '@datacatalog/shared';
import type { LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
import ReactFlow, { Background, Controls, MiniMap, Position, type Node, type Edge, type NodeMouseHandler } from 'reactflow';
import 'reactflow/dist/style.css';

interface MiniLineageGraphProps {
  namespace: string;
  name: string;
  onOpenFull?: () => void;
}

type Direction = 'upstream' | 'downstream' | 'both';

const COL   = 170;
const ROW   = 80;
const FOCAL = '#bfdbfe';
const DS    = '#dbeafe';
const JOB   = '#fef3c7';

function resolveHandles(nodes: Node[], edges: Edge[]): Node[] {
  const xOf = new Map(nodes.map(n => [n.id, n.position.x]));
  const sp = new Map<string, Position>();
  const tp = new Map<string, Position>();
  for (const e of edges) {
    const sx = xOf.get(e.source) ?? 0;
    const tx = xOf.get(e.target) ?? 0;
    sp.set(e.source, sx <= tx ? Position.Right : Position.Left);
    tp.set(e.target, sx <= tx ? Position.Left  : Position.Right);
  }
  return nodes.map(n => ({
    ...n,
    sourcePosition: sp.get(n.id) ?? Position.Right,
    targetPosition: tp.get(n.id) ?? Position.Left,
  }));
}

function nodeStyle(isRoot: boolean, isJob: boolean) {
  return {
    background: isRoot ? FOCAL : isJob ? JOB : DS,
    border: isRoot ? '2px solid #3b82f6' : '1px solid #cbd5e1',
    borderRadius: 6,
    padding: '4px 8px',
    fontSize: 10,
    maxWidth: 150,
    fontWeight: isRoot ? 600 : 400,
  };
}

function edgeFrom(e: LineageEdge, i: number): Edge {
  return {
    id: `e-${i}`,
    source: `${e.fromNamespace}/${e.fromName}`,
    target: `${e.toNamespace}/${e.toName}`,
    style: { stroke: '#94a3b8', strokeWidth: 1 },
    animated: e.edgeType === 'DERIVED_FROM',
  };
}

function buildSingle(graph: LineageGraphData, upstream: boolean): { nodes: Node[]; edges: Edge[] } {
  const buckets = new Map<number, LineageNode[]>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!buckets.has(d)) buckets.set(d, []);
    buckets.get(d)!.push(n);
  });
  const rootKey = `${graph.rootNamespace}/${graph.rootName}`;
  const nodes = graph.nodes.map(n => {
    const d = n.depth ?? 0;
    const bucket = buckets.get(d)!;
    return {
      id: `${n.namespace}/${n.name}`,
      data: { label: n.name, namespace: n.namespace, name: n.name },
      position: {
        x: (upstream ? -d : d) * COL,
        y: (bucket.indexOf(n) - (bucket.length - 1) / 2) * ROW,
      },
      style: nodeStyle(`${n.namespace}/${n.name}` === rootKey, n.type === 'Job'),
    };
  });
  const edges = graph.edges.map(edgeFrom);
  return { nodes: resolveHandles(nodes, edges), edges };
}

function buildBoth(upGraph: LineageGraphData, downGraph: LineageGraphData): { nodes: Node[]; edges: Edge[] } {
  const signedDepths = new Map<string, number>();
  const nodeByKey = new Map<string, LineageNode>();

  for (const n of downGraph.nodes) {
    const k = `${n.namespace}/${n.name}`;
    signedDepths.set(k, n.depth ?? 0);
    nodeByKey.set(k, n);
  }
  for (const n of upGraph.nodes) {
    const k = `${n.namespace}/${n.name}`;
    const sd = -(n.depth ?? 0);
    if (sd === 0) continue;
    signedDepths.set(k, sd);
    nodeByKey.set(k, n);
  }

  const cols = new Map<number, string[]>();
  for (const [k, sd] of signedDepths) {
    if (!cols.has(sd)) cols.set(sd, []);
    cols.get(sd)!.push(k);
  }

  const nodes: Node[] = [];
  for (const [k, sd] of signedDepths) {
    const n = nodeByKey.get(k)!;
    const col = cols.get(sd)!;
    nodes.push({
      id: k,
      data: { label: n.name, namespace: n.namespace, name: n.name },
      position: {
        x: sd * COL,
        y: (col.indexOf(k) - (col.length - 1) / 2) * ROW,
      },
      sourcePosition: Position.Left,
      targetPosition: Position.Left,
      style: nodeStyle(sd === 0, n.type === 'Job'),
    });
  }

  const seen = new Set<string>();
  const edges: Edge[] = [];
  for (const e of [...upGraph.edges, ...downGraph.edges]) {
    const eid = `${e.fromNamespace}/${e.fromName}->${e.toNamespace}/${e.toName}`;
    if (!seen.has(eid)) { seen.add(eid); edges.push(edgeFrom(e, edges.length)); }
  }

  return { nodes: resolveHandles(nodes, edges), edges };
}

function FlowCanvas({ nodes, edges, minimap, onNodeDoubleClick }: { nodes: Node[]; edges: Edge[]; minimap?: boolean; onNodeDoubleClick?: NodeMouseHandler }) {
  return (
    <ReactFlow nodes={nodes} edges={edges} fitView onNodeDoubleClick={onNodeDoubleClick} nodesDraggable={false} nodesConnectable={false} elementsSelectable={false} attributionPosition="bottom-right">
      <Background gap={16} color="#f1f5f9" />
      <Controls />
      {minimap && <MiniMap nodeColor={n => n.style?.background as string ?? DS} />}
    </ReactFlow>
  );
}

export default function MiniLineageGraph({ namespace, name, onOpenFull }: MiniLineageGraphProps) {
  const [maximized, setMaximized]   = useState(false);
  const [direction, setDirection]   = useState<Direction>('downstream');
  const navigate = useNavigate();

  const onNodeDoubleClick: NodeMouseHandler = useCallback(async (_evt, node) => {
    const { namespace: ns, name: nodeName } = node.data as { namespace: string; name: string };
    try {
      const { catalogId } = await lineageApi.getCatalogId(ns, nodeName);
      navigate(`/datasets/${catalogId}`);
    } catch { /* no catalog link */ }
  }, [navigate]);

  const { data: downGraph, isLoading: downLoading } = useQuery({
    queryKey: ['lineage-mini', namespace, name, 'downstream'],
    queryFn: () => lineageApi.getDatasetLineage(namespace, name, 'downstream', 3),
    enabled: !!namespace && !!name && (direction === 'downstream' || direction === 'both'),
  });

  const { data: upGraph, isLoading: upLoading } = useQuery({
    queryKey: ['lineage-mini', namespace, name, 'upstream'],
    queryFn: () => lineageApi.getDatasetLineage(namespace, name, 'upstream', 3),
    enabled: !!namespace && !!name && (direction === 'upstream' || direction === 'both'),
  });

  const isLoading =
    direction === 'both'     ? (downLoading || upLoading) :
    direction === 'upstream' ? upLoading :
                               downLoading;

  let flow: { nodes: Node[]; edges: Edge[] } | null = null;
  if (direction === 'downstream' && downGraph)            flow = buildSingle(downGraph, false);
  if (direction === 'upstream'   && upGraph)              flow = buildSingle(upGraph, true);
  if (direction === 'both'       && upGraph && downGraph) flow = buildBoth(upGraph, downGraph);

  const DIRS: { value: Direction; label: string }[] = [
    { value: 'upstream', label: 'Upstream' },
    { value: 'downstream', label: 'Downstream' },
    { value: 'both', label: 'Both' },
  ];

  return (
    <>
      <div>
        <div className="flex items-center gap-1 mb-2">
          {DIRS.map(d => (
            <button
              key={d.value}
              onClick={() => setDirection(d.value)}
              className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${direction === d.value ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
            >
              {d.label}
            </button>
          ))}
        </div>

        <div className="relative h-[440px] rounded-lg border border-gray-200 overflow-hidden">
          <button
            onClick={() => setMaximized(true)}
            title="Maximize"
            className="absolute top-2 right-2 z-10 bg-white border border-gray-200 rounded px-1.5 py-0.5 text-gray-500 hover:text-gray-800 shadow-sm text-xs"
          >
            ⤢
          </button>
          {isLoading && (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">Loading lineage...</div>
          )}
          {!isLoading && flow && flow.nodes.length > 0 && <FlowCanvas nodes={flow.nodes} edges={flow.edges} onNodeDoubleClick={onNodeDoubleClick} />}
          {!isLoading && (!flow || flow.nodes.length === 0) && (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">No lineage data available.</div>
          )}
        </div>

        {onOpenFull && (
          <button onClick={onOpenFull} className="mt-2 text-xs text-blue-600 hover:underline">
            Open full lineage →
          </button>
        )}
      </div>

      {maximized && (
        <div className="fixed inset-0 z-50 bg-white flex flex-col">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 bg-gray-50 flex-shrink-0">
            <div className="flex items-center gap-3">
              <span className="text-sm font-medium text-gray-700">Lineage — {name}</span>
              <div className="flex items-center gap-1">
                {DIRS.map(d => (
                  <button
                    key={d.value}
                    onClick={() => setDirection(d.value)}
                    className={`px-2 py-0.5 rounded text-xs font-medium transition-colors ${direction === d.value ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>
            <button onClick={() => setMaximized(false)} className="text-gray-400 hover:text-gray-700 text-xl leading-none px-1" title="Close">
              ✕
            </button>
          </div>
          <div className="flex-1 min-h-0">
            {isLoading && <div className="h-full flex items-center justify-center text-sm text-gray-400">Loading lineage...</div>}
            {!isLoading && flow && flow.nodes.length > 0 && <FlowCanvas nodes={flow.nodes} edges={flow.edges} minimap onNodeDoubleClick={onNodeDoubleClick} />}
            {!isLoading && (!flow || flow.nodes.length === 0) && (
              <div className="h-full flex items-center justify-center text-sm text-gray-400">No lineage data available.</div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
