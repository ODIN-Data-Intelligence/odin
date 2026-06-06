import { useState, useCallback } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { lineageApi } from '@datacatalog/shared';
import type { LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
import ReactFlow, { Background, Controls, MiniMap, Position, type Node, type Edge, type NodeMouseHandler } from 'reactflow';
import 'reactflow/dist/style.css';

type Direction = 'upstream' | 'downstream' | 'both';

const COL_WIDTH  = 200;
const ROW_HEIGHT = 90;
const DATASET_COLOR = '#dbeafe';
const JOB_COLOR     = '#fef3c7';
const FOCAL_COLOR   = '#bfdbfe';

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

// ── Single-direction layout ───────────────────────────────────────────────────
// upstream=true  → root on the right, ancestors extend left  (x = -depth * COL_WIDTH)
// upstream=false → root on the left,  dependents extend right (x =  depth * COL_WIDTH)
function buildSingleElements(graph: LineageGraphData, upstream = false): { nodes: Node[]; edges: Edge[] } {
  const buckets = new Map<number, LineageNode[]>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!buckets.has(d)) buckets.set(d, []);
    buckets.get(d)!.push(n);
  });

  const nodes: Node[] = graph.nodes.map(n => {
    const d = n.depth ?? 0;
    const bucket = buckets.get(d)!;
    const rowIdx = bucket.indexOf(n);
    const total  = bucket.length;
    return {
      id: `${n.namespace}/${n.name}`,
      data: { label: n.name, namespace: n.namespace, name: n.name },
      position: {
        x: (upstream ? -d : d) * COL_WIDTH,
        y: (rowIdx - (total - 1) / 2) * ROW_HEIGHT,
      },
      style: {
        background: d === 0 ? FOCAL_COLOR : n.type === 'Job' ? JOB_COLOR : DATASET_COLOR,
        border: d === 0 ? '2px solid #3b82f6' : '1px solid #cbd5e1',
        borderRadius: 6,
        padding: '6px 10px',
        fontSize: 11,
        maxWidth: 170,
        fontWeight: d === 0 ? 600 : 400,
      },
    };
  });

  const edges: Edge[] = graph.edges.map((e, i) => edgeFromLineage(e, i));
  return { nodes: resolveHandles(nodes, edges), edges };
}

// ── Both-direction layout (signed depth: upstream < 0, focal = 0, downstream > 0) ──
function buildBothElements(
  upGraph: LineageGraphData,
  downGraph: LineageGraphData,
): { nodes: Node[]; edges: Edge[] } {
  const signedDepths = new Map<string, number>();
  const nodeByKey    = new Map<string, LineageNode>();

  for (const n of downGraph.nodes) {
    const key = `${n.namespace}/${n.name}`;
    signedDepths.set(key, n.depth ?? 0);
    nodeByKey.set(key, n);
  }
  for (const n of upGraph.nodes) {
    const key = `${n.namespace}/${n.name}`;
    const sd  = -(n.depth ?? 0);
    if (sd === 0) continue;
    signedDepths.set(key, sd);
    nodeByKey.set(key, n);
  }

  const colBuckets = new Map<number, string[]>();
  for (const [key, sd] of signedDepths) {
    if (!colBuckets.has(sd)) colBuckets.set(sd, []);
    colBuckets.get(sd)!.push(key);
  }

  const nodes: Node[] = [];
  for (const [key, sd] of signedDepths) {
    const n   = nodeByKey.get(key)!;
    const col = colBuckets.get(sd)!;
    const rowIdx = col.indexOf(key);
    const total  = col.length;
    nodes.push({
      id: key,
      data: { label: n.name, namespace: n.namespace, name: n.name },
      position: {
        x: sd * COL_WIDTH,
        y: (rowIdx - (total - 1) / 2) * ROW_HEIGHT,
      },
      style: {
        background: sd === 0 ? FOCAL_COLOR : n.type === 'Job' ? JOB_COLOR : DATASET_COLOR,
        border: sd === 0 ? '2px solid #3b82f6' : '1px solid #cbd5e1',
        borderRadius: 6,
        padding: '6px 10px',
        fontSize: 11,
        maxWidth: 170,
        fontWeight: sd === 0 ? 600 : 400,
      },
    });
  }

  const seen  = new Set<string>();
  const edges: Edge[] = [];
  for (const e of [...upGraph.edges, ...downGraph.edges]) {
    const eid = `${e.fromNamespace}/${e.fromName}->${e.toNamespace}/${e.toName}`;
    if (seen.has(eid)) continue;
    seen.add(eid);
    edges.push(edgeFromLineage(e, edges.length));
  }

  return { nodes: resolveHandles(nodes, edges), edges };
}

function edgeFromLineage(e: LineageEdge, i: number): Edge {
  return {
    id: `e-${i}`,
    source: `${e.fromNamespace}/${e.fromName}`,
    target: `${e.toNamespace}/${e.toName}`,
    style: { stroke: '#94a3b8', strokeWidth: 1.5 },
    animated: e.edgeType === 'DERIVED_FROM',
  };
}

// ── Page ─────────────────────────────────────────────────────────────────────
export default function LineagePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const initNs        = searchParams.get('ns')        ?? '';
  const initName      = searchParams.get('name')      ?? '';
  const initDirection = (searchParams.get('direction') ?? 'downstream') as Direction;

  const [namespace, setNamespace] = useState(initNs);
  const [name, setName]           = useState(initName);
  const [direction, setDirection] = useState<Direction>(initDirection);
  const [submitted, setSubmitted] = useState<{ ns: string; name: string } | null>(
    initNs && initName ? { ns: initNs, name: initName } : null,
  );

  const { data: downGraph, isLoading: downLoading } = useQuery({
    queryKey: ['lineage-full', submitted?.ns, submitted?.name, 'downstream'],
    queryFn:  () => lineageApi.getDatasetLineage(submitted!.ns, submitted!.name, 'downstream', 5),
    enabled:  !!submitted && (direction === 'downstream' || direction === 'both'),
  });

  const { data: upGraph, isLoading: upLoading } = useQuery({
    queryKey: ['lineage-full', submitted?.ns, submitted?.name, 'upstream'],
    queryFn:  () => lineageApi.getDatasetLineage(submitted!.ns, submitted!.name, 'upstream', 5),
    enabled:  !!submitted && (direction === 'upstream' || direction === 'both'),
  });

  const isLoading =
    direction === 'both'     ? (upLoading || downLoading) :
    direction === 'upstream' ? upLoading :
                               downLoading;

  let flowElements: { nodes: Node[]; edges: Edge[] } | null = null;
  if (direction === 'both'       && upGraph && downGraph) flowElements = buildBothElements(upGraph, downGraph);
  if (direction === 'upstream'   && upGraph)              flowElements = buildSingleElements(upGraph, true);
  if (direction === 'downstream' && downGraph)            flowElements = buildSingleElements(downGraph);

  const onNodeDoubleClick: NodeMouseHandler = useCallback(async (_evt, node) => {
    const { namespace, name: nodeName } = node.data as { namespace: string; name: string };
    try {
      const { catalogId } = await lineageApi.getCatalogId(namespace, nodeName);
      navigate(`/datasets/${catalogId}`);
    } catch { /* no catalog link */ }
  }, [navigate]);

  return (
    <div className="flex flex-col h-screen">
      {/* Header */}
      <div className="bg-white border-b px-6 py-3 flex items-center gap-4 flex-shrink-0">
        <Link to="/search" className="text-sm text-gray-500 hover:text-gray-700">← Back to search</Link>
        <div className="h-4 w-px bg-gray-200" />
        <h1 className="text-sm font-semibold text-gray-800">Lineage Explorer</h1>
        {direction === 'both' && flowElements && (
          <span className="text-xs text-gray-400 ml-2">
            upstream ← <span className="font-medium text-blue-600">dataset</span> → downstream
          </span>
        )}
      </div>

      {/* Controls */}
      <div className="bg-white border-b px-6 py-3 flex items-end gap-3 flex-shrink-0">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Namespace</label>
          <input
            value={namespace}
            onChange={e => setNamespace(e.target.value)}
            placeholder="e.g. snowflake://my-account/mydb"
            className="border border-gray-300 rounded px-3 py-1.5 text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Dataset name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="e.g. trades"
            className="border border-gray-300 rounded px-3 py-1.5 text-sm w-52 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Direction</label>
          <select
            value={direction}
            onChange={e => setDirection(e.target.value as Direction)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="downstream">Downstream</option>
            <option value="upstream">Upstream</option>
            <option value="both">Both</option>
          </select>
        </div>
        <button
          onClick={() => setSubmitted({ ns: namespace, name })}
          disabled={!namespace || !name}
          className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          Explore
        </button>
      </div>

      {/* Graph */}
      <div className="flex-1 relative bg-gray-50">
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
            Loading lineage...
          </div>
        )}
        {flowElements && (
          <ReactFlow
            nodes={flowElements.nodes}
            edges={flowElements.edges}
            fitView
            onNodeDoubleClick={onNodeDoubleClick}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={false}
            attributionPosition="bottom-right"
          >
            <Background gap={16} color="#e2e8f0" />
            <Controls />
            <MiniMap nodeColor={n => n.style?.background as string ?? DATASET_COLOR} />
          </ReactFlow>
        )}
        {!flowElements && !isLoading && (
          <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
            Enter a dataset namespace and name to explore its lineage
          </div>
        )}
      </div>
    </div>
  );
}
