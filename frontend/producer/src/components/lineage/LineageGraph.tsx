import { useState } from 'react';
import ReactFlow, {
  Background, Controls, MiniMap,
  type Node, type Edge,
} from 'reactflow';
import 'reactflow/dist/style.css';
import type { LineageGraph as LineageGraphData } from '@datacatalog/shared';

interface Props {
  graph: LineageGraphData;
}

const DATASET_COLOR = '#dbeafe';
const JOB_COLOR = '#fef3c7';

function buildNodesEdges(graph: LineageGraphData): { nodes: Node[]; edges: Edge[] } {
  const depthBuckets = new Map<number, typeof graph.nodes>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!depthBuckets.has(d)) depthBuckets.set(d, []);
    depthBuckets.get(d)!.push(n);
  });

  const COL_WIDTH = 240;
  const ROW_HEIGHT = 100;

  const nodes: Node[] = graph.nodes.map(n => {
    const key = `${n.namespace}/${n.name}`;
    const d = n.depth ?? 0;
    const bucket = depthBuckets.get(d)!;
    const rowIdx = bucket.indexOf(n);
    const totalInDepth = bucket.length;
    return {
      id: key,
      data: { label: n.name, namespace: n.namespace, type: n.type },
      position: {
        x: d * COL_WIDTH,
        y: (rowIdx - (totalInDepth - 1) / 2) * ROW_HEIGHT,
      },
      style: {
        background: n.type === 'Job' ? JOB_COLOR : DATASET_COLOR,
        border: '1px solid #cbd5e1',
        borderRadius: 8,
        padding: '8px 12px',
        fontSize: 12,
        maxWidth: 200,
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

  return { nodes, edges };
}

function FlowCanvas({ nodes, edges }: { nodes: Node[]; edges: Edge[] }) {
  return (
    <ReactFlow nodes={nodes} edges={edges} fitView attributionPosition="bottom-right">
      <Background gap={20} color="#e2e8f0" />
      <Controls />
      <MiniMap nodeColor={n => n.style?.background as string ?? DATASET_COLOR} />
    </ReactFlow>
  );
}

export default function LineageGraph({ graph }: Props) {
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
        <FlowCanvas nodes={nodes} edges={edges} />
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
            <FlowCanvas nodes={nodes} edges={edges} />
          </div>
        </div>
      )}
    </>
  );
}
