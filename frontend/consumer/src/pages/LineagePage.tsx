import { useState, useCallback } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Alert from '@mui/material/Alert';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { lineageApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { LineageGraph as LineageGraphData, LineageNode, LineageEdge } from '@datacatalog/shared';
import ReactFlow, { Background, Controls, MiniMap, Handle, Position, type Node, type Edge, type NodeMouseHandler, type NodeProps } from 'reactflow';
import 'reactflow/dist/style.css';

type Direction = 'upstream' | 'downstream' | 'both';

const COL_WIDTH  = 200;
const ROW_HEIGHT = 90;
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
          <div style={{ marginTop: 5, borderTop: '1px solid rgba(0,0,0,0.1)', paddingTop: 4, maxHeight: 160, overflowY: 'auto', minWidth: 130 }}>
            {elementsLoading && <div style={{ fontSize: 9, color: '#94a3b8', textAlign: 'center' }}>…</div>}
            {!elementsLoading && elements.length === 0 && (
              <div style={{ fontSize: 9, color: '#94a3b8', fontStyle: 'italic' }}>No elements</div>
            )}
            {(elements as { id: string; name: string; logicalType?: string }[]).map(el => (
              <div
                key={el.id}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 9, padding: '1.5px 0', gap: 6, cursor: 'default' }}
                onMouseEnter={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(id)}
                onMouseLeave={() => (data.onHighlightNode as ((nid: string | null) => void) | undefined)?.(null)}
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

function edgeFromLineage(e: LineageEdge, i: number, highlightedNodeId?: string | null): Edge {
  const highlighted = !!highlightedNodeId && e.toId === highlightedNodeId;
  return {
    id: `e-${i}`,
    source: e.fromId,
    target: e.toId,
    style: highlighted ? { stroke: '#3b82f6', strokeWidth: 2.5 } : { stroke: '#94a3b8', strokeWidth: 1.5 },
    animated: e.edgeType === 'DERIVED_FROM',
  };
}

function buildSingleElements(
  graph: LineageGraphData,
  upstream = false,
  onNavigate?: (catalogId: string) => void,
  highlightedNodeId?: string | null,
  onHighlightNode?: (id: string | null) => void,
  expandedNodeIds?: Set<string>,
  onToggleExpand?: (id: string) => void,
): { nodes: Node[]; edges: Edge[] } {
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
      id: n.id,
      type: 'dataset',
      data: {
        label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId,
        onNavigate, onHighlightNode,
        expanded: expandedNodeIds?.has(n.id) ?? false,
        onToggleExpand,
      },
      position: {
        x: (upstream ? -d : d) * COL_WIDTH,
        y: (rowIdx - (total - 1) / 2) * ROW_HEIGHT,
      },
      style: {
        background: n.id === graph.rootId ? FOCAL_COLOR : n.type === 'Job' ? JOB_COLOR : DATASET_COLOR,
        border: n.id === graph.rootId ? '2px solid #3b82f6' : '1px solid #cbd5e1',
        borderRadius: 6,
        padding: '6px 10px',
        fontSize: 11,
        maxWidth: 170,
        fontWeight: n.id === graph.rootId ? 600 : 400,
      },
    };
  });

  const edges: Edge[] = graph.edges.map((e, i) => edgeFromLineage(e, i, highlightedNodeId));
  const withHandles = resolveHandles(nodes, edges);
  return {
    nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles,
    edges,
  };
}

function buildBothElements(
  upGraph: LineageGraphData,
  downGraph: LineageGraphData,
  onNavigate?: (catalogId: string) => void,
  highlightedNodeId?: string | null,
  onHighlightNode?: (id: string | null) => void,
  expandedNodeIds?: Set<string>,
  onToggleExpand?: (id: string) => void,
): { nodes: Node[]; edges: Edge[] } {
  const signedDepths = new Map<string, number>();
  const nodeById    = new Map<string, LineageNode>();

  for (const n of downGraph.nodes) {
    signedDepths.set(n.id, n.depth ?? 0);
    nodeById.set(n.id, n);
  }
  for (const n of upGraph.nodes) {
    const sd  = -(n.depth ?? 0);
    if (sd === 0) continue;
    signedDepths.set(n.id, sd);
    nodeById.set(n.id, n);
  }

  const colBuckets = new Map<number, string[]>();
  for (const [id, sd] of signedDepths) {
    if (!colBuckets.has(sd)) colBuckets.set(sd, []);
    colBuckets.get(sd)!.push(id);
  }

  const nodes: Node[] = [];
  for (const [id, sd] of signedDepths) {
    const n   = nodeById.get(id)!;
    const col = colBuckets.get(sd)!;
    const rowIdx = col.indexOf(id);
    const total  = col.length;
    nodes.push({
      id,
      type: 'dataset',
      data: {
        label: n.name, namespace: n.namespace, name: n.name, catalogId: n.catalogId,
        onNavigate, onHighlightNode,
        expanded: expandedNodeIds?.has(id) ?? false,
        onToggleExpand,
      },
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
    const eid = `${e.fromId}->${e.toId}`;
    if (seen.has(eid)) continue;
    seen.add(eid);
    edges.push(edgeFromLineage(e, edges.length, highlightedNodeId));
  }

  const withHandles = resolveHandles(nodes, edges);
  return {
    nodes: expandedNodeIds ? applyExpansionOffsets(withHandles, expandedNodeIds, EXPANDED_EXTRA_HEIGHT) : withHandles,
    edges,
  };
}

export default function LineagePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const initNs        = searchParams.get('ns')        ?? '';
  const initName      = searchParams.get('name')      ?? '';
  const initDirection = (searchParams.get('direction') ?? 'downstream') as Direction;

  const [namespace, setNamespace] = useState(initNs);
  const [name, setName]           = useState(initName);
  const [direction, setDirection] = useState<Direction>(initDirection);
  const [submitted, setSubmitted] = useState<{ id: string } | null>(null);
  const [lookupError, setLookupError] = useState(false);
  const [highlightedNodeId, setHighlightedNodeId] = useState<string | null>(null);
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());

  const handleToggleExpand = useCallback((nodeId: string) => {
    setExpandedNodeIds(prev => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId); else next.add(nodeId);
      return next;
    });
  }, []);

  const handleExplore = async () => {
    setLookupError(false);
    try {
      const { id } = await lineageApi.lookupDataset(namespace, name);
      setSubmitted({ id });
    } catch {
      setLookupError(true);
    }
  };

  const handleNavigate = useCallback((catalogId: string) => {
    navigate(`/datasets/${catalogId}`);
  }, [navigate]);

  const { data: downGraph, isLoading: downLoading } = useQuery({
    queryKey: ['lineage-full', submitted?.id, 'downstream'],
    queryFn:  () => lineageApi.getDatasetLineage(submitted!.id, 'downstream', 5),
    enabled:  !!submitted && (direction === 'downstream' || direction === 'both'),
  });

  const { data: upGraph, isLoading: upLoading } = useQuery({
    queryKey: ['lineage-full', submitted?.id, 'upstream'],
    queryFn:  () => lineageApi.getDatasetLineage(submitted!.id, 'upstream', 5),
    enabled:  !!submitted && (direction === 'upstream' || direction === 'both'),
  });

  const isLoading =
    direction === 'both'     ? (upLoading || downLoading) :
    direction === 'upstream' ? upLoading :
                               downLoading;

  let flowElements: { nodes: Node[]; edges: Edge[] } | null = null;
  if (direction === 'both'       && upGraph && downGraph) flowElements = buildBothElements(upGraph, downGraph, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);
  if (direction === 'upstream'   && upGraph)              flowElements = buildSingleElements(upGraph, true, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);
  if (direction === 'downstream' && downGraph)            flowElements = buildSingleElements(downGraph, false, handleNavigate, highlightedNodeId, setHighlightedNodeId, expandedNodeIds, handleToggleExpand);

  const onNodeDoubleClick: NodeMouseHandler = useCallback((_evt, node) => {
    const { catalogId } = node.data as { catalogId?: string };
    if (catalogId) navigate(`/datasets/${catalogId}`);
  }, [navigate]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      {/* Header */}
      <Paper square elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', px: 3, py: 1.5, display: 'flex', alignItems: 'center', gap: 2, flexShrink: 0 }}>
        <Button
          component={Link}
          to="/search"
          startIcon={<ArrowBackIcon fontSize="small" />}
          size="small"
          sx={{ fontSize: 13, textTransform: 'none', color: 'text.secondary' }}
        >
          Back to search
        </Button>
        <Box sx={{ width: 1, height: 16, bgcolor: 'divider' }} />
        <Typography variant="body2" fontWeight={600} color="text.primary">Lineage Explorer</Typography>
        {direction === 'both' && flowElements && (
          <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
            upstream ← <Box component="span" sx={{ fontWeight: 600, color: 'primary.main' }}>dataset</Box> → downstream
          </Typography>
        )}
      </Paper>

      {/* Controls */}
      <Paper square elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', px: 3, py: 2, display: 'flex', alignItems: 'flex-end', gap: 2, flexShrink: 0, flexWrap: 'wrap' }}>
        <TextField
          label="Namespace"
          value={namespace}
          onChange={e => setNamespace(e.target.value)}
          placeholder="e.g. snowflake://my-account/mydb"
          size="small"
          sx={{ width: 320 }}
          variant="outlined"
        />
        <TextField
          label="Dataset name"
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="e.g. trades"
          size="small"
          sx={{ width: 220 }}
          variant="outlined"
        />
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>Direction</InputLabel>
          <Select
            label="Direction"
            value={direction}
            onChange={e => setDirection(e.target.value as Direction)}
          >
            <MenuItem value="downstream">Downstream</MenuItem>
            <MenuItem value="upstream">Upstream</MenuItem>
            <MenuItem value="both">Both</MenuItem>
          </Select>
        </FormControl>
        <Box>
          <Button
            variant="contained"
            onClick={handleExplore}
            disabled={!namespace || !name}
            sx={{ textTransform: 'none' }}
          >
            Explore
          </Button>
          {lookupError && (
            <Alert severity="error" sx={{ mt: 0.75, py: 0.25, fontSize: 12 }}>
              Dataset not found in lineage graph
            </Alert>
          )}
        </Box>
      </Paper>

      {/* Graph */}
      <Box sx={{ flex: 1, position: 'relative', bgcolor: 'grey.50' }}>
        {isLoading && (
          <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">Loading lineage...</Typography>
          </Box>
        )}
        {flowElements && (
          <ReactFlow
            nodes={flowElements.nodes}
            edges={flowElements.edges}
            nodeTypes={NODE_TYPES}
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
          <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Enter a dataset namespace and name to explore its lineage
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
}
