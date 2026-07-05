import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Table from '@mui/material/Table';
import TableHead from '@mui/material/TableHead';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';

interface DiffEntry {
  key: string;
  before: unknown;
  after: unknown;
  status: 'added' | 'removed' | 'changed' | 'unchanged';
}

export function computeDiff(
  before: Record<string, unknown>,
  after: Record<string, unknown>,
): DiffEntry[] {
  const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
  const entries: DiffEntry[] = [];

  for (const key of keys) {
    const hasB = key in before;
    const hasA = key in after;
    const bVal = before[key];
    const aVal = after[key];

    if (!hasB) {
      entries.push({ key, before: undefined, after: aVal, status: 'added' });
    } else if (!hasA) {
      entries.push({ key, before: bVal, after: undefined, status: 'removed' });
    } else if (JSON.stringify(bVal) !== JSON.stringify(aVal)) {
      entries.push({ key, before: bVal, after: aVal, status: 'changed' });
    } else {
      entries.push({ key, before: bVal, after: aVal, status: 'unchanged' });
    }
  }

  return entries.sort((a, b) => {
    const order = { changed: 0, added: 1, removed: 2, unchanged: 3 };
    return order[a.status] - order[b.status];
  });
}

function renderArrayItem(item: unknown): string {
  if (item === null || item === undefined) return '—';
  if (typeof item !== 'object') return String(item);
  const obj = item as Record<string, unknown>;
  const label = obj.conceptLabel ?? obj.name ?? obj.label ?? obj.title;
  const iri = obj.conceptIri ?? obj.iri;
  if (label != null) return iri != null ? `${label} (${iri})` : String(label);
  if (iri != null) return String(iri);
  return JSON.stringify(obj);
}

function renderValue(v: unknown): string {
  if (v === undefined || v === null) return '—';
  if (Array.isArray(v)) return v.length === 0 ? '(none)' : v.map(renderArrayItem).join(', ');
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

const ROW_BG: Record<DiffEntry['status'], string> = {
  changed:   'warning.50',
  added:     'success.50',
  removed:   'error.50',
  unchanged: 'transparent',
};

interface JsonDiffViewProps {
  before: string | null | undefined;
  after: string | null | undefined;
}

export default function JsonDiffView({ before, after }: JsonDiffViewProps) {
  let beforeObj: Record<string, unknown> = {};
  let afterObj: Record<string, unknown> = {};

  try { if (before) beforeObj = JSON.parse(before); } catch { /* ignore */ }
  try { if (after) afterObj = JSON.parse(after); } catch { /* ignore */ }

  const diff = computeDiff(beforeObj, afterObj);
  const changed = diff.filter(d => d.status !== 'unchanged');
  const unchanged = diff.filter(d => d.status === 'unchanged');

  if (!before && !after) {
    return <Typography variant="caption" color="text.disabled" fontStyle="italic">No snapshot available</Typography>;
  }

  return (
    <Box sx={{ overflowX: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Table size="small">
        <TableHead>
          <TableRow sx={{ bgcolor: 'grey.50' }}>
            <TableCell sx={{ fontWeight: 600, width: '25%' }}>Field</TableCell>
            <TableCell sx={{ fontWeight: 600, width: '37.5%' }}>Before</TableCell>
            <TableCell sx={{ fontWeight: 600, width: '37.5%' }}>After</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {changed.map(entry => (
            <TableRow key={entry.key} sx={{ bgcolor: ROW_BG[entry.status] }}>
              <TableCell sx={{ fontWeight: 500 }}>{entry.key}</TableCell>
              <TableCell sx={{
                fontFamily: 'monospace',
                fontSize: 12,
                color: entry.status === 'changed' || entry.status === 'removed' ? 'error.main' : 'text.disabled',
                textDecoration: entry.status === 'removed' ? 'line-through' : 'none',
              }}>{renderValue(entry.before)}</TableCell>
              <TableCell sx={{
                fontFamily: 'monospace',
                fontSize: 12,
                color: entry.status === 'changed' || entry.status === 'added' ? 'success.main' : 'text.disabled',
                fontWeight: entry.status !== 'unchanged' ? 500 : 400,
              }}>{renderValue(entry.after)}</TableCell>
            </TableRow>
          ))}
          {unchanged.length > 0 && changed.length > 0 && (
            <TableRow>
              <TableCell colSpan={3} sx={{ bgcolor: 'grey.50', color: 'text.disabled', fontSize: 12, py: 0.75 }}>
                {unchanged.length} unchanged {unchanged.length === 1 ? 'field' : 'fields'}
              </TableCell>
            </TableRow>
          )}
          {unchanged.map(entry => (
            <TableRow key={entry.key} sx={{ opacity: 0.5 }}>
              <TableCell sx={{ fontWeight: 500, color: 'text.secondary' }}>{entry.key}</TableCell>
              <TableCell sx={{ fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>{renderValue(entry.before)}</TableCell>
              <TableCell sx={{ fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>{renderValue(entry.after)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
