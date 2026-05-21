import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { lineageApi } from '@datacatalog/shared';
import ReactFlow, { Background, Controls, MiniMap, type Node, type Edge } from 'reactflow';
import 'reactflow/dist/style.css';

interface MiniLineageGraphProps {
  namespace: string;
  name: string;
  onOpenFull?: () => void;
}

function FlowCanvas({ nodes, edges, minimap }: { nodes: Node[]; edges: Edge[]; minimap?: boolean }) {
  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      fitView
      nodesDraggable={false}
      nodesConnectable={false}
      elementsSelectable={false}
      attributionPosition="bottom-right"
    >
      <Background gap={16} color="#f1f5f9" />
      <Controls />
      {minimap && <MiniMap nodeColor={n => n.style?.background as string ?? '#dbeafe'} />}
    </ReactFlow>
  );
}

export default function MiniLineageGraph({ namespace, name, onOpenFull }: MiniLineageGraphProps) {
  const [maximized, setMaximized] = useState(false);

  const { data: graph, isLoading } = useQuery({
    queryKey: ['lineage-mini', namespace, name],
    queryFn: () => lineageApi.getDatasetLineage(namespace, name, 'downstream', 3),
    enabled: !!namespace && !!name,
  });

  if (isLoading) return <div className="h-[480px] flex items-center justify-center text-xs text-gray-400">Loading lineage...</div>;
  if (!graph || graph.nodes.length === 0) return <p className="text-xs text-gray-400 text-center py-6">No lineage data available.</p>;

  const depthBuckets = new Map<number, typeof graph.nodes>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!depthBuckets.has(d)) depthBuckets.set(d, []);
    depthBuckets.get(d)!.push(n);
  });

  const nodes: Node[] = graph.nodes.map(n => {
    const d = n.depth ?? 0;
    const bucket = depthBuckets.get(d)!;
    const rowIdx = bucket.indexOf(n);
    const totalInDepth = bucket.length;
    return {
      id: `${n.namespace}/${n.name}`,
      data: { label: n.name },
      position: {
        x: d * 170,
        y: (rowIdx - (totalInDepth - 1) / 2) * 80,
      },
      style: {
        background: n.type === 'Job' ? '#fef3c7' : '#dbeafe',
        border: '1px solid #cbd5e1',
        borderRadius: 6,
        padding: '4px 8px',
        fontSize: 10,
        maxWidth: 150,
      },
    };
  });

  const edges: Edge[] = graph.edges.map((e, i) => ({
    id: `e-${i}`,
    source: `${e.fromNamespace}/${e.fromName}`,
    target: `${e.toNamespace}/${e.toName}`,
    style: { stroke: '#94a3b8', strokeWidth: 1 },
    animated: e.edgeType === 'DERIVED_FROM',
  }));

  return (
    <>
      <div>
        <div className="relative h-[480px] rounded-lg border border-gray-200 overflow-hidden">
          <button
            onClick={() => setMaximized(true)}
            title="Maximize"
            className="absolute top-2 right-2 z-10 bg-white border border-gray-200 rounded px-1.5 py-0.5 text-gray-500 hover:text-gray-800 shadow-sm text-xs"
          >
            ⤢
          </button>
          <FlowCanvas nodes={nodes} edges={edges} />
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
            <span className="text-sm font-medium text-gray-700">Lineage — {name}</span>
            <button
              onClick={() => setMaximized(false)}
              className="text-gray-400 hover:text-gray-700 text-xl leading-none px-1"
              title="Close"
            >
              ✕
            </button>
          </div>
          <div className="flex-1 min-h-0">
            <FlowCanvas nodes={nodes} edges={edges} minimap />
          </div>
        </div>
      )}
    </>
  );
}
