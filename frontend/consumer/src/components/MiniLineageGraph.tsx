import { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import OpenInFullIcon from '@mui/icons-material/OpenInFull';
import CloseIcon from '@mui/icons-material/Close';
import { lineageApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
import ReactFlow, { Background, Controls, MiniMap, Handle, Position, type Node, type Edge, type NodeMouseHandler, type NodeProps } from 'reactflow';
import 'reactflow/dist/style.css';

interface MiniLineageGraphProps {
  lineageId: string;
  onOpenFull?: () => void;
}

type Direction = 'upstream' | 'downstream' | 'both';

const COL   = 170;
const ROW   = 80;
const FOCAL = '#bfdbfe';
const DS    = '#dbeafe';
const JOB   = '#fef3c7';
const EXPANDED_EXTRA_HEIGHT = 140;

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
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {data.label as string}
          </span>
          {data.catalogId && (
            <>
              <button onClick={(e) => { e.stopPropagation(); (data.onToggleExpand as ((id: string) => void) | undefined)?.(id); }} title={expanded ? 'Collapse' : 'Show data elements'} style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }} onMouseEnter={e => { e.currentTarget.style.opacity = '1'; }} onMouseLeave={e => { e.currentTarget.style.opacity = '0.5'; }}>
                <svg width="9" height="9" viewBox="0 0 10 10" fill="none">{expanded ? <path d="M2 6.5L5 3.5L8 6.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/> : <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>}</svg>
              </button>
              <button onClick={(e) => { e.stopPropagation(); (data.onNavigate as ((cid: string) => void) | undefined)?.(data.catalogId as string); }} title="Open in catalog" style={{ flexShrink: 0, cursor: 'pointer', opacity: 0.5, lineHeight: 1, padding: 0, background: 'none', border: 'none', color: 'inherit' }} onMouseEnter={e => { e.currentTarget.style.opacity = '1'; }} onMouseLeave={e => { e.currentTarget.style.opacity = '0.5'; }}>
                <svg width="9" height="9" viewBox="0 0 12 12" fill="none"><path d="M5 2H2a1 1 0 00-1 1v7a1 1 0 001 1h7a1 1 0 001-1V7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/><path d="M8 1h3m0 0v3m0-3L6 6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
              </button>
            </>
          )}
        </div>
        {expanded && (
          <div style={{ marginTop: 4, borderTop: '1px solid rgba(0,0,0,0.1)', paddingTop: 3, maxHeight: 120, overflowY: 'auto', minWidth: 110 }}>
            {elementsLoading && <div style={{ fontSize: 8, color: '#94a3b8', textAlign: 'center' }}>…</div>}
            {!elementsLoading && elements.length === 0 && <div style={{ fontSize: 8, color: '#94a3b8', fontStyle: 'italic' }}>No elements</div>}
            {(elements as { id: string; name: string; logicalType?: string }[]).map(el => (
              <div key={el.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 8, padding: '1px 0', gap: 4 }}>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>{el.name}</span>
                {el.logicalType && <span style={{ color: '#94a3b8', flexShrink: 0, fontSize: 7 }}>{el.logicalType}</span>}
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
  return nodes.map(n => ({ ...n, sourcePosition: sp.get(n.id) ?? Position.Right, targetPosition: tp.get(n.id) ?? Position.Left }));
}

function applyExpansionOffsets(nodes: Node[], expandedNodeIds: Set<string>, extraHeight: number): Node[] {
  if (expandedNodeIds.size === 0) return nodes;
  const byX = new Map<number, Node[]>();
  for (const n of nodes) { if (!byX.has(n.position.x)) byX.set(n.position.x, []); byX.get(n.position.x)!.push(n); }
  const result = [...nodes];
  const idxOf = new Map(result.map((n, i) => [n.id, i]));
  for (const col of byX.values()) {
    col.sort((a, b) => a.position.y - b.position.y);
    let offset = 0;
    for (const node of col) {
      if (offset > 0) { const i = idxOf.get(node.id)!; result[i] = { ...result[i], position: { ...result[i].position, y: result[i].position.y + offset } }; }
      if (expandedNodeIds.has(node.id)) offset += extraHeight;
    }
  }
  return result;
}

function nodeStyle(isRoot: boolean, isJob: boolean) {
  return { background: isRoot ? FOCAL : isJob ? JOB : DS, border: isRoot ? '2px solid #3b82f6' : '1px solid #cbd5e1', borderRadius: 6, padding: '4px 8px', fontSize: 10, maxWidth: 150, fontWeight: isRoot ? 600 : 400 };
}

function edgeFrom(e: LineageEdge, i: number, highlightedNodeId?: string | null): Edge {
  const highlighted = !!highlightedNodeId && e.toId === highlightedNodeId;
  return { id: `e-${i}`, source: e.fromId, target: e.toId, style: highlighted ? { stroke: '#3b82f6', strokeWidth: 2 } : { stroke: '#94a3b8', strokeWidth: 1 }, animated: e.edgeType === 'DERIVED_FROM' };
}

function buildSingle(graph: LineageGraphData, upstream: boolean, onNavigate?: (catalogId: string) => void, highlightedNodeId?: string | null, onHighlightNode?: (id: string | null) => void, expandedNodeIds?: Set<string>, onToggleExpand?: (id: string) => void): { nodes: Node[]; edges: Edge[] } {
  const buckets = new Map<number, LineageNode[]>();
  graph.nodes.forEach(n => { const d = n.depth ?? 0; if (!buckets.has(d)) buckets.set(d, []); buckets.get(d)!.push(n); });
  const nodes = graph.nodes.map(n => { const d = n.depth ?? 0; const bucket = buckets.get(d)!; return { id: n.id, type: 'dataset', data: { label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId, onNavigate, onHighlightNode, expanded: expandedNodeIds?.has(n.id) ?? false, onToggleExpand }, position: { x: (upstream ? -d : d) * COL, y: (bucket.indexOf(n) - (bucket.length - 1) / 2) * ROW }, style: nodeStyle(n.id === graph.rootId, n.type === 'Job') }; });
  const edges = graph.edges.map((e, i) => edgeFrom(e, i, highlightedNodeId));
  const withHandles = resolveHandles(nodes, edges);
  return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges };
}

function buildBoth(upGraph: LineageGraphData, downGraph: LineageGraphData, onNavigate?: (catalogId: string) => void, highlightedNodeId?: string | null, onHighlightNode?: (id: string | null) => void, expandedNodeIds?: Set<string>, onToggleExpand?: (id: string) => void): { nodes: Node[]; edges: Edge[] } {
  const signedDepths = new Map<string, number>();
  const nodeById = new Map<string, LineageNode>();
  for (const n of downGraph.nodes) { signedDepths.set(n.id, n.depth ?? 0); nodeById.set(n.id, n); }
  for (const n of upGraph.nodes) { const sd = -(n.depth ?? 0); if (sd === 0) continue; signedDepths.set(n.id, sd); nodeById.set(n.id, n); }
  const cols = new Map<number, string[]>();
  for (const [id, sd] of signedDepths) { if (!cols.has(sd)) cols.set(sd, []); cols.get(sd)!.push(id); }
  const nodes: Node[] = [];
  for (const [id, sd] of signedDepths) { const n = nodeById.get(id)!; const col = cols.get(sd)!; nodes.push({ id, type: 'dataset', data: { label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId, onNavigate, onHighlightNode, expanded: expandedNodeIds?.has(id) ?? false, onToggleExpand }, position: { x: sd * COL, y: (col.indexOf(id) - (col.length - 1) / 2) * ROW }, style: nodeStyle(sd === 0, n.type === 'Job') }); }
  const seen = new Set<string>(); const edges: Edge[] = [];
  for (const e of [...upGraph.edges, ...downGraph.edges]) { const eid = `${e.fromId}->${e.toId}`; if (!seen.has(eid)) { seen.add(eid); edges.push(edgeFrom(e, edges.length, highlightedNodeId)); } }
  const withHandles = resolveHandles(nodes, edges);
  return { nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles, edges };
}

function FlowCanvas({ nodes, edges, minimap, onNodeDoubleClick }: { nodes: Node[]; edges: Edge[]; minimap?: boolean; onNodeDoubleClick?: NodeMouseHandler }) {
  return (
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES} fitView onNodeDoubleClick={onNodeDoubleClick} nodesDraggable={false} nodesConnectable={false} elementsSelectable={false} attributionPosition="bottom-right">
      <Background gap={16} color="#f1f5f9" />
      <Controls />
      {minimap && <MiniMap nodeColor={n => n.style?.background as string ?? DS} />}
    </ReactFlow>
  );
}

export default function MiniLineageGraph({ lineageId, onOpenFull }: MiniLineageGraphProps) {
  const [maximized, setMaximized] = useState(false);
  const [direction, setDirection] = useState<Direction>('downstream');
  const [highlightedNodeId, setHighlightedNodeId] = useState<string | null>(null);
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());
  const navigate = useNavigate();

  const handleToggleExpand = useCallback((nodeId: string) => {
    setExpandedNodeIds(prev => { const next = new Set(prev); if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId); return next; });
  }, []);

  const handleNavigate = useCallback((catalogId: string) => { navigate(`/datasets/${catalogId}`); }, [navigate]);

  const onNodeDoubleClick: NodeMouseHandler = useCallback((_evt, node) => {
    const { catalogId } = node.data as { catalogId?: string };
    if (catalogId) navigate(`/datasets/${catalogId}`);
  }, [navigate]);

  const { data: downGraph, isLoading: downLoading } = useQuery({
    queryKey: ['lineage-mini', lineageId, 'downstream'],
    queryFn: () => lineageApi.getDatasetLineage(lineageId, 'downstream', 3),
    enabled: !!lineageId && (direction === 'downstream' || direction === 'both'),
  });

  const { data: upGraph, isLoading: upLoading } = useQuery({
    queryKey: ['lineage-mini', lineageId, 'upstream'],
    queryFn: () => lineageApi.getDatasetLineage(lineageId, 'upstream', 3),
    enabled: !!lineageId && (direction === 'upstream' || direction === 'both'),
  });

  const isLoading = direction === 'both' ? (downLoading || upLoading) : direction === 'upstream' ? upLoading : downLoading;

  let flow: { nodes: Node[]; edges: Edge[] } | null = null;
  if (direction === 'downstream' && downGraph) flow = buildSingle(downGraph, false, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);
  if (direction === 'upstream' && upGraph) flow = buildSingle(upGraph, true, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);
  if (direction === 'both' && upGraph && downGraph) flow = buildBoth(upGraph, downGraph, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);

  const displayName = downGraph?.rootName ?? upGraph?.rootName ?? '';

  const directionButtons = (
    <ToggleButtonGroup value={direction} exclusive onChange={(_, v) => v && setDirection(v)} size="small">
      <ToggleButton value="upstream" sx={{ px: 1.5, py: 0.25, fontSize: 12 }}>Upstream</ToggleButton>
      <ToggleButton value="downstream" sx={{ px: 1.5, py: 0.25, fontSize: 12 }}>Downstream</ToggleButton>
      <ToggleButton value="both" sx={{ px: 1.5, py: 0.25, fontSize: 12 }}>Both</ToggleButton>
    </ToggleButtonGroup>
  );

  return (
    <>
      <Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          {directionButtons}
        </Box>

        <Paper variant="outlined" sx={{ height: 440, position: 'relative', overflow: 'hidden', borderRadius: 2 }}>
          <IconButton size="small" onClick={() => setMaximized(true)} title="Maximize" sx={{ position: 'absolute', top: 8, right: 8, zIndex: 10, bgcolor: 'background.paper', boxShadow: 1 }}>
            <OpenInFullIcon fontSize="small" />
          </IconButton>
          {isLoading && (
            <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography variant="caption" color="text.secondary">Loading lineage...</Typography>
            </Box>
          )}
          {!isLoading && flow && flow.nodes.length > 0 && <FlowCanvas nodes={flow.nodes} edges={flow.edges} onNodeDoubleClick={onNodeDoubleClick} />}
          {!isLoading && (!flow || flow.nodes.length === 0) && (
            <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography variant="caption" color="text.secondary">No lineage data available.</Typography>
            </Box>
          )}
        </Paper>

        {onOpenFull && (
          <Button size="small" onClick={onOpenFull} sx={{ mt: 1, textTransform: 'none', fontSize: 13 }}>
            Open full lineage →
          </Button>
        )}
      </Box>

      <Dialog open={maximized} onClose={() => setMaximized(false)} fullScreen>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <Typography variant="subtitle2">Lineage — {displayName}</Typography>
            {directionButtons}
          </Box>
          <IconButton onClick={() => setMaximized(false)} size="small">
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ p: 0, height: '100%' }}>
          {isLoading && <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Typography color="text.secondary">Loading lineage...</Typography></Box>}
          {!isLoading && flow && flow.nodes.length > 0 && <FlowCanvas nodes={flow.nodes} edges={flow.edges} minimap onNodeDoubleClick={onNodeDoubleClick} />}
          {!isLoading && (!flow || flow.nodes.length === 0) && <Box sx={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Typography color="text.secondary">No lineage data available.</Typography></Box>}
        </DialogContent>
      </Dialog>
    </>
  );
}
