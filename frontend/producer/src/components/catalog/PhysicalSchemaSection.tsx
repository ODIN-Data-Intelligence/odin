import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Checkbox from '@mui/material/Checkbox';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import LinearProgress from '@mui/material/LinearProgress';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { datasetApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { CsvwColumn, LogicalDataElement, ColumnElementSuggestion } from '@datacatalog/shared';

interface Props {
  distributionId: string;
  datasetId: string;
  tenant: string;
  canAction: boolean;
}

function SuggestMappingsModal({ suggestions, columns, elements, onApply, onClose, isPending }: {
  suggestions: ColumnElementSuggestion[];
  columns: CsvwColumn[];
  elements: LogicalDataElement[];
  onApply: (selected: ColumnElementSuggestion[]) => void;
  onClose: () => void;
  isPending: boolean;
}) {
  const [checked, setChecked] = useState<Set<string>>(
    () => new Set(suggestions.map(s => s.columnId))
  );

  const toggle = (columnId: string) =>
    setChecked(prev => {
      const next = new Set(prev);
      next.has(columnId) ? next.delete(columnId) : next.add(columnId);
      return next;
    });

  const toggleAll = () =>
    setChecked(prev => prev.size === suggestions.length ? new Set() : new Set(suggestions.map(s => s.columnId)));

  const colMap = new Map(columns.map(c => [c.id, c]));
  const elMap = new Map(elements.map(e => [e.id, e]));
  const selected = suggestions.filter(s => checked.has(s.columnId));

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        AI Mapping Suggestions
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
          {suggestions.length} suggestion{suggestions.length !== 1 ? 's' : ''} based on column name similarity
        </Typography>
      </DialogTitle>
      <DialogContent sx={{ p: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column', maxHeight: '60vh' }}>
        {suggestions.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ px: 3, py: 5, textAlign: 'center' }}>
            No unmapped columns could be matched to logical elements.
          </Typography>
        ) : (
          <Box sx={{ overflow: 'auto', flex: 1 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox" sx={{ pl: 3 }}>
                    <Checkbox
                      size="small"
                      checked={checked.size === suggestions.length}
                      indeterminate={checked.size > 0 && checked.size < suggestions.length}
                      onChange={toggleAll}
                    />
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Physical Column</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Suggested Logical Element</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Confidence</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {suggestions.map(s => {
                  const col = colMap.get(s.columnId);
                  const el = elMap.get(s.suggestedElementId);
                  const pct = Math.round(s.confidence * 100);
                  const isChecked = checked.has(s.columnId);
                  return (
                    <TableRow
                      key={s.columnId}
                      onClick={() => toggle(s.columnId)}
                      sx={{ cursor: 'pointer', bgcolor: isChecked ? 'secondary.50' : undefined }}
                      hover
                    >
                      <TableCell padding="checkbox" sx={{ pl: 3 }} onClick={e => e.stopPropagation()}>
                        <Checkbox size="small" checked={isChecked} onChange={() => toggle(s.columnId)} />
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" fontFamily="monospace" fontWeight={600}>{col?.name ?? s.columnId}</Typography>
                        {col?.datatype && <Chip label={col.datatype} size="small" color="info" sx={{ ml: 1, height: 16, fontSize: 10, fontFamily: 'monospace' }} />}
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>{el?.name ?? s.suggestedElementName}</Typography>
                        {el?.logicalType && <Typography variant="caption" color="text.secondary"> {el.logicalType}</Typography>}
                      </TableCell>
                      <TableCell sx={{ minWidth: 100 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <LinearProgress
                            variant="determinate"
                            value={pct}
                            color={pct >= 70 ? 'success' : pct >= 40 ? 'warning' : 'error'}
                            sx={{ flex: 1, borderRadius: 1, height: 4 }}
                          />
                          <Typography variant="caption" fontWeight={500} color={pct >= 70 ? 'success.main' : pct >= 40 ? 'warning.main' : 'error.main'}>
                            {pct}%
                          </Typography>
                        </Box>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ justifyContent: 'space-between', px: 3 }}>
        <Typography variant="caption" color="text.secondary">{checked.size} of {suggestions.length} selected</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button variant="contained" color="secondary" onClick={() => onApply(selected)} disabled={isPending || selected.length === 0} sx={{ textTransform: 'none' }}>
            {isPending ? 'Applying…' : `Apply ${selected.length} mapping${selected.length !== 1 ? 's' : ''}`}
          </Button>
        </Box>
      </DialogActions>
    </Dialog>
  );
}

function MappingCell({ col, elements, canAction, onBind, onUnbind }: {
  col: CsvwColumn; elements: LogicalDataElement[]; canAction: boolean;
  onBind: (elementId: string, colId: string) => void; onUnbind: (elementId: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [selected, setSelected] = useState('');

  if (col.logicalDataElementId && !editing) {
    const el = elements.find(e => e.id === col.logicalDataElementId);
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="caption" color="success.main" fontWeight={600}>{el?.name ?? '✓ Mapped'}</Typography>
        {canAction && (
          <>
            <Button size="small" sx={{ textTransform: 'none', fontSize: 11, p: 0, minWidth: 0 }} onClick={() => { setSelected(col.logicalDataElementId!); setEditing(true); }}>Change</Button>
            <Button size="small" color="error" sx={{ textTransform: 'none', fontSize: 11, p: 0, minWidth: 0 }} onClick={() => onUnbind(col.logicalDataElementId!)}>Unmap</Button>
          </>
        )}
      </Box>
    );
  }

  if (!canAction) return <Typography variant="caption" color="text.disabled">—</Typography>;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
      <Select
        value={selected}
        onChange={e => setSelected(e.target.value)}
        size="small"
        displayEmpty
        sx={{ fontSize: 12, minWidth: 160, height: 24, '.MuiSelect-select': { py: 0 } }}
      >
        <MenuItem value=""><em>— select —</em></MenuItem>
        {elements.map(e => <MenuItem key={e.id} value={e.id} sx={{ fontSize: 12 }}>{e.name}</MenuItem>)}
      </Select>
      {selected && (
        <Button size="small" sx={{ textTransform: 'none', fontSize: 11, p: '0 4px', minWidth: 0 }} onClick={() => { onBind(selected, col.id); setSelected(''); setEditing(false); }}>
          Bind
        </Button>
      )}
      {editing && (
        <Button size="small" sx={{ textTransform: 'none', fontSize: 11, p: '0 4px', minWidth: 0 }} onClick={() => { setEditing(false); setSelected(''); }}>
          Cancel
        </Button>
      )}
    </Box>
  );
}

export default function PhysicalSchemaSection({ distributionId, datasetId, tenant, canAction }: Props) {
  const qc = useQueryClient();
  const [modalSuggestions, setModalSuggestions] = useState<ColumnElementSuggestion[] | null>(null);

  const { data: columns = [] } = useQuery({
    queryKey: ['distribution-schema', distributionId],
    queryFn: () => datasetApi.getDistributionPhysicalSchema(distributionId),
  });

  const { data: logicalModels = [] } = useQuery({
    queryKey: ['logical-models', datasetId],
    queryFn: () => logicalModelApi.list(datasetId),
  });

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', logicalModels[0]?.id],
    queryFn: () => logicalElementApi.list(logicalModels[0]!.id),
    enabled: logicalModels.length > 0,
  });

  const bindMut = useMutation({
    mutationFn: ({ elementId, colId }: { elementId: string; colId: string }) =>
      logicalElementApi.bind(elementId, colId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['distribution-schema', distributionId] }),
  });

  const unbindMut = useMutation({
    mutationFn: (elementId: string) => logicalElementApi.unbind(elementId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['distribution-schema', distributionId] }),
  });

  const suggestMut = useMutation({
    mutationFn: () => datasetApi.suggestElementMappings(distributionId, logicalModels[0]!.id),
    onSuccess: (data) => setModalSuggestions(data),
  });

  const applyMut = useMutation({
    mutationFn: async (selected: ColumnElementSuggestion[]) => {
      for (const s of selected) {
        await logicalElementApi.bind(s.suggestedElementId, s.columnId);
      }
    },
    onSuccess: () => {
      setModalSuggestions(null);
      qc.invalidateQueries({ queryKey: ['distribution-schema', distributionId] });
    },
  });

  const hasElements = elements.length > 0;

  return (
    <>
      {modalSuggestions !== null && (
        <SuggestMappingsModal
          suggestions={modalSuggestions}
          columns={columns}
          elements={elements}
          onApply={(selected) => applyMut.mutate(selected)}
          onClose={() => setModalSuggestions(null)}
          isPending={applyMut.isPending}
        />
      )}

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider', bgcolor: 'grey.50', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
          <Typography variant="body2" fontWeight={600}>
            Physical Schema
            {columns.length > 0 && <Typography component="span" variant="caption" color="text.disabled" sx={{ ml: 1 }}>({columns.length} columns)</Typography>}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
            {hasElements && columns.length > 0 && canAction && (
              <Button
                size="small"
                color="secondary"
                startIcon={<AutoAwesomeIcon sx={{ fontSize: '14px !important' }} />}
                onClick={() => suggestMut.mutate()}
                disabled={suggestMut.isPending}
                sx={{ textTransform: 'none', fontSize: 12 }}
              >
                {suggestMut.isPending ? 'Analysing…' : 'AI Suggest Mappings'}
              </Button>
            )}
            {!hasElements && logicalModels.length === 0 && columns.length > 0 && (
              <Typography variant="caption" color="text.secondary">
                No logical model —{' '}
                <Typography
                  component={Link}
                  to={`/${tenant}/datasets/${datasetId}`}
                  variant="caption"
                  color="primary"
                  sx={{ textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                >
                  create one on the Schema tab
                </Typography>
              </Typography>
            )}
          </Box>
        </Box>

        {columns.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ px: 2, py: 2 }}>No physical schema available.</Typography>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: 'grey.50' }}>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11, width: 32 }}>#</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Column</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Type</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Nullable</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Description</TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: 11 }}>Logical Data Element</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {columns.map((col: CsvwColumn) => (
                  <TableRow key={col.id} hover sx={{ verticalAlign: 'top' }}>
                    <TableCell><Typography variant="caption" color="text.disabled">{col.ordinal}</Typography></TableCell>
                    <TableCell><Typography variant="caption" fontFamily="monospace" fontWeight={600}>{col.name}</Typography></TableCell>
                    <TableCell>
                      {col.datatype && <Chip label={col.datatype} size="small" color="info" sx={{ height: 16, fontSize: 10, fontFamily: 'monospace' }} />}
                    </TableCell>
                    <TableCell>
                      {col.required
                        ? <Typography variant="caption" color="error" fontWeight={600}>NOT NULL</Typography>
                        : <Typography variant="caption" color="text.disabled">nullable</Typography>}
                    </TableCell>
                    <TableCell><Typography variant="caption" color="text.secondary">{col.description ?? ''}</Typography></TableCell>
                    <TableCell>
                      {hasElements ? (
                        <MappingCell
                          col={col}
                          elements={elements}
                          canAction={canAction}
                          onBind={(elementId, colId) => bindMut.mutate({ elementId, colId })}
                          onUnbind={(elementId) => unbindMut.mutate(elementId)}
                        />
                      ) : (
                        <Typography variant="caption" color="text.disabled">—</Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Paper>
    </>
  );
}
