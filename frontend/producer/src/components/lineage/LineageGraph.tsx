import { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import ReactFlow, {
  Background, Controls, MiniMap, Handle, Position,
  type Node, type Edge, type NodeMouseHandler, type NodeProps,
} from 'reactflow';
import 'reactflow/dist/style.css';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import { logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { LineageGraph as LineageGraphData } from '@datacatalog/shared';

interface Props {
  graph: LineageGraphData;
  onNodeDoubleClick?: NodeMouseHandler;
  onNavigate?: (catalogId: string) => void;
}

const DATASET_COLOR = '#dbeafe';
const JOB_COLOR     = '#fef3c7';
const FOCAL_COLOR   = '#bfdbfe';
const EXPANDED_EXTRA_HEIGHT = 185;

function DatasetNode({ id, data, sourcePosition = Position.Right, targetPosition = Position.Left }: NodeProps) {
  const expanded = !!(data.expanded as boolean);

  const { data: models } = useQuery({
    queryKey: ['lineage-node-models', data.catalogId],
    queryFn: () => logicalModelApi.list(data.catalogId as string),
    enabled: expanded && !!(data.catalogId),
  });

  const publishedModel = models?.find((m: { status: string }) => m.status === 'published') ?? models?.[0];

  const { data: elements = [], isLoading: elementsLoading } = useQuery({
    queryKey: ['lineage-node-elements', publishedModel?.id],
    queryFn: () => logicalElementApi.list(publishedModel!.id),
    enabled: !!publishedModel,
  });

  return (
    <>
      <Handle type="target" position={targetPosition} />
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {data.label as string}
          </span>
          {data.catalogId && (
            <>
              <button
                onClick={(e) => { e.stopPropagation(); (data.onToggleExpand as ((id: string) => void) | undefined)?.(id); }}
                title={expanded ? 'Collapse' : 'Show data elements'}
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}
              >
                <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                  {expanded
                    ? <path d="M2 6.5L5 3.5L8 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    : <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  }
                </svg>
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); (data.onNavigate as ((cid: string) => void) | undefined)?.(data.catalogId as string); }}
                title="Open in catalog"
                style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }}
                onMouseEnter={(e) => { e.currentTarget.style.opacity = '1'; }}
                onMouseLeave={(e) => { e.currentTarget.style.opacity = '0.5'; }}
              >
                <svg width="10" height="10" viewBox="0 0 12 12" fill="none">
                  <path d="M5 2H2a1 1 0 00-1 1v7a1 1 0 001 1h7a1 1 0 001-1V7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  <path d="M8 1h3m0 0v3m0-3L6 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>
            </>
          )}
        </div>
        {expanded && (
          <div style={{ marginTop: 5, borderTop: '1px solid rgba(0,0,0,0.1)', paddingTop: 4, maxHeight: 160, overflowY: 'auto', minWidth: 140 }}>
            {elementsLoading && <div style={{ fontSize: 9, color: '#94a3b8', textAlign: 'center' }}>…</div>}
            {!elementsLoading && elements.length === 0 && (
              <div style={{ fontSize: 9, color: '#94a3b8', fontStyle: 'italic' }}>No elements</div>
            )}
            {(elements as { id: string; name: string; logicalType?: string }[]).map(el => (
              <div
                key={el.id}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 9, padding: '1.5px 0', gap: 6, cursor: 'default' }}
              >
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{el.name}</span>
                {el.logicalType && <span style={{ color: '#94a3b8', flexShrink: 0, fontSize: 8 }}>{el.logicalType}</span>}
              </div>
            ))}
          </div>
        )}
      </div>
      <Handle type="source" position={sourcePosition} />
    </>
  );
}

const NODE_TYPES = { dataset: DatasetNode };

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

function applyExpansionOffsets(nodes: Node[], expandedNodeIds: Set<string>, extraHeight: number): Node[] {
  if (expandedNodeIds.size === 0) return nodes;
  const byX = new Map<number, Node[]>();
  for (const n of nodes) {
    if (!byX.has(n.position.x)) byX.set(n.position.x, []);
    byX.get(n.position.x)!.push(n);
  }
  const result = [...nodes];
  const idxOf = new Map(result.map((n, i) => [n.id, i]));
  for (const col of byX.values()) {
    col.sort((a, b) => a.position.y - b.position.y);
    let offset = 0;
    for (const node of col) {
      if (offset > 0) {
        const i = idxOf.get(node.id)!;
        result[i] = { ...result[i], position: { ...result[i].position, y: result[i].position.y + offset } };
      }
      if (expandedNodeIds.has(node.id)) offset += extraHeight;
    }
  }
  return result;
}

function buildNodesEdges(
  graph: LineageGraphData,
  onNavigate?: (catalogId: string) => void,
  highlightedNodeId?: string | null,
  onHighlightNode?: (id: string | null) => void,
  expandedNodeIds?: Set<string>,
  onToggleExpand?: (id: string) => void,
): { nodes: Node[]; edges: Edge[] } {
  const depthBuckets = new Map<number, typeof graph.nodes>();
  graph.nodes.forEach(n => {
    const d = n.depth ?? 0;
    if (!depthBuckets.has(d)) depthBuckets.set(d, []);
    depthBuckets.get(d)!.push(n);
  });

  const COL_WIDTH = 240;
  const ROW_HEIGHT = 100;
  const isUpstream = graph.direction === 'upstream';

  const nodes: Node[] = graph.nodes.map(n => {
    const d = n.depth ?? 0;
    const isRoot = n.id === graph.rootId;
    const bucket = depthBuckets.get(d)!;
    const rowIdx = bucket.indexOf(n);
    const totalInDepth = bucket.length;
    return {
      id: n.id,
      type: 'dataset',
      data: {
        label: n.name, namespace: n.namespace, name: n.name, type: n.type, catalogId: n.catalogId,
        onNavigate, onHighlightNode,
        expanded: expandedNodeIds?.has(n.id) ?? false,
        onToggleExpand,
      },
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

  const edges: Edge[] = graph.edges.map((e, i) => {
    const highlighted = !!highlightedNodeId && e.toId === highlightedNodeId;
    return {
      id: `e-${i}`,
      source: e.fromId,
      target: e.toId,
      label: e.edgeType,
      labelStyle: { fontSize: 10 },
      style: highlighted ? { stroke: '#3b82f6', strokeWidth: 2.5 } : { stroke: '#94a3b8', strokeWidth: 1.5 },
      animated: e.edgeType === 'DERIVED_FROM',
    };
  });

  const withHandles = resolveHandles(nodes, edges);
  return {
    nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles,
    edges,
  };
}

function FlowCanvas({ nodes, edges, onNodeDoubleClick }: { nodes: Node[]; edges: Edge[]; onNodeDoubleClick?: NodeMouseHandler }) {
  return (
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES} fitView onNodeDoubleClick={onNodeDoubleClick} attributionPosition="bottom-right">
      <Background gap={20} color="#e2e8f0" />
      <Controls />
      <MiniMap nodeColor={n => n.style?.background as string ?? DATASET_COLOR} />
    </ReactFlow>
  );
}

export default function LineageGraph({ graph, onNodeDoubleClick, onNavigate }: Props) {
  const [maximized, setMaximized] = useState(false);
  const [highlightedNodeId, setHighlightedNodeId] = useState<string | null>(null);
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());

  const handleToggleExpand = useCallback((nodeId: string) => {
    setExpandedNodeIds(prev => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId);
      return next;
    });
  }, []);

  const { nodes, edges } = buildNodesEdges(
    graph, onNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand,
  );

  return (
    <>
      <Box sx={{ position: 'relative', width: '100%', height: '100%' }}>
        <Box
          component="button"
          onClick={() => setMaximized(true)}
          title="Maximize"
          sx={{
            position: 'absolute', top: 8, right: 8, zIndex: 10,
            bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 0.5,
            px: 0.75, py: 0.25, cursor: 'pointer', typography: 'caption', color: 'text.secondary',
            boxShadow: 1, '&:hover': { color: 'text.primary' },
          }}
        >
          ⤢
        </Box>
        <FlowCanvas nodes={nodes} edges={edges} onNodeDoubleClick={onNodeDoubleClick} />
      </Box>

      <Dialog open={maximized} onClose={() => setMaximized(false)} maxWidth={false} fullScreen PaperProps={{ sx: { display: 'flex', flexDirection: 'column' } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 2, py: 1, borderBottom: 1, borderColor: 'divider', bgcolor: 'grey.50', flexShrink: 0 }}>
          <Typography variant="body2" fontWeight={600}>Lineage Graph</Typography>
          <IconButton size="small" onClick={() => setMaximized(false)} title="Close"><CloseIcon fontSize="small" /></IconButton>
        </Box>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <FlowCanvas nodes={nodes} edges={edges} onNodeDoubleClick={onNodeDoubleClick} />
        </Box>
      </Dialog>
    </>
  );
}
