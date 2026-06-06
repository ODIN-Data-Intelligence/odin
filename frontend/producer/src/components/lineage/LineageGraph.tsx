import { useState } from 'react';
import ReactFlow, {
  Background, Controls, MiniMap, Position,
  type Node, type Edge, type NodeMouseHandler,
} from 'reactflow';
import 'reactflow/dist/style.css';
import type { LineageGraph as LineageGraphData } from '@datacatalog/shared';

interface Props {
  graph: LineageGraphData;
  onNodeDoubleClick?: NodeMouseHandler;
}

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

function buildNodesEdges(graph: LineageGraphData): { nodes: Node[]; edges: Edge[] } {
  const depthBuckets = new Map<number, typeof graph.nodes>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!depthBuckets.has(d)) depthBuckets.set(d, []);
    depthBuckets.get(d)!.push(n);
  });

  const COL_WIDTH = 240;
  const ROW_HEIGHT = 100;
  const rootKey = `${graph.rootNamespace}/${graph.rootName}`;
  const isUpstream = graph.direction === 'upstream';

  const nodes: Node[] = graph.nodes.map(n => {
    const key = `${n.namespace}/${n.name}`;
    const d = n.depth ?? 0;
    const isRoot = key === rootKey;
    const bucket = depthBuckets.get(d)!;
    const rowIdx = bucket.indexOf(n);
    const totalInDepth = bucket.length;
    return {
      id: key,
      data: { label: n.name, namespace: n.namespace, name: n.name, type: n.type },
      position: {
        x: (isUpstream ? -d : d) * COL_WIDTH,
        y: (rowIdx - (totalInDepth - 1) / 2) * ROW_HEIGHT,
      },
      style: {
        background: isRoot ? FOCAL_COLOR : n.type === 'Job' ? JOB_COLOR : DATASET_COLOR,
        border: isRoot ? '2px solid #3b82f6' : '1px solid #cbd5e1',
        borderRadius: 8,
        padding: '8px 12px',
        fontSize: 12,
        maxWidth: 200,
        fontWeight: isRoot ? 600 : 400,
      },
    };
  });

  const edges: Edge[] = graph.edges.map((e, i) => ({
    id: `e-${i}`,
    source: `${e.fromNamespace}/${e.fromName}`,
    target: `${e.toNamespace}/${e.toName}`,
    label: e.edgeType,
    labelStyle: { fontSize: 10 },
    style: { stroke: '#94a3b8' },
    animated: e.edgeType === 'DERIVED_FROM',
  }));

  return { nodes: resolveHandles(nodes, edges), edges };
}

function FlowCanvas({ nodes, edges, onNodeDoubleClick }: { nodes: Node[]; edges: Edge[]; onNodeDoubleClick?: NodeMouseHandler }) {
  return (
    <ReactFlow nodes={nodes} edges={edges} fitView onNodeDoubleClick={onNodeDoubleClick} attributionPosition="bottom-right">
      <Background gap={20} color="#e2e8f0" />
      <Controls />
      <MiniMap nodeColor={n => n.style?.background as string ?? DATASET_COLOR} />
    </ReactFlow>
  );
}

export default function LineageGraph({ graph, onNodeDoubleClick }: Props) {
  const [maximized, setMaximized] = useState(false);
  const { nodes, edges } = buildNodesEdges(graph);

  return (
    <>
      <div className="relative w-full h-full">
        <button
          onClick={() => setMaximized(true)}
          title="Maximize"
          className="absolute top-2 right-2 z-10 bg-white border border-gray-200 rounded px-1.5 py-0.5 text-gray-500 hover:text-gray-800 shadow-sm text-xs"
        >
          ⤢
        </button>
        <FlowCanvas nodes={nodes} edges={edges} onNodeDoubleClick={onNodeDoubleClick} />
      </div>

      {maximized && (
        <div className="fixed inset-0 z-50 bg-white flex flex-col">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-200 bg-gray-50 flex-shrink-0">
            <span className="text-sm font-medium text-gray-700">Lineage Graph</span>
            <button
              onClick={() => setMaximized(false)}
              className="text-gray-400 hover:text-gray-700 text-xl leading-none px-1"
              title="Close"
            >
              ✕
            </button>
          </div>
          <div className="flex-1 min-h-0">
            <FlowCanvas nodes={nodes} edges={edges} onNodeDoubleClick={onNodeDoubleClick} />
          </div>
        </div>
      )}
    </>
  );
}
