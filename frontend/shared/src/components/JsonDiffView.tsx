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

function renderValue(v: unknown): string {
  if (v === undefined || v === null) return '—';
  if (Array.isArray(v)) return v.join(', ');
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

const STATUS_ROW: Record<DiffEntry['status'], string> = {
  changed:   'bg-amber-50',
  added:     'bg-green-50',
  removed:   'bg-red-50',
  unchanged: '',
};

const STATUS_CELL_BEFORE: Record<DiffEntry['status'], string> = {
  changed:   'text-red-700',
  removed:   'text-red-700 line-through',
  added:     'text-gray-300',
  unchanged: 'text-gray-700',
};

const STATUS_CELL_AFTER: Record<DiffEntry['status'], string> = {
  changed:   'text-green-700 font-medium',
  added:     'text-green-700 font-medium',
  removed:   'text-gray-300',
  unchanged: 'text-gray-700',
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
    return <p className="text-xs text-gray-400 italic">No snapshot available</p>;
  }

  return (
    <div className="overflow-x-auto rounded border border-gray-200 text-xs">
      <table className="w-full border-collapse">
        <thead>
          <tr className="bg-gray-50 border-b border-gray-200">
            <th className="px-3 py-2 text-left font-medium text-gray-500 w-1/4">Field</th>
            <th className="px-3 py-2 text-left font-medium text-gray-500 w-[37.5%]">Before</th>
            <th className="px-3 py-2 text-left font-medium text-gray-500 w-[37.5%]">After</th>
          </tr>
        </thead>
        <tbody>
          {changed.map(entry => (
            <tr key={entry.key} className={STATUS_ROW[entry.status]}>
              <td className="px-3 py-1.5 font-medium text-gray-600 border-b border-gray-100">{entry.key}</td>
              <td className={`px-3 py-1.5 border-b border-gray-100 font-mono ${STATUS_CELL_BEFORE[entry.status]}`}>
                {renderValue(entry.before)}
              </td>
              <td className={`px-3 py-1.5 border-b border-gray-100 font-mono ${STATUS_CELL_AFTER[entry.status]}`}>
                {renderValue(entry.after)}
              </td>
            </tr>
          ))}
          {unchanged.length > 0 && changed.length > 0 && (
            <tr>
              <td colSpan={3} className="px-3 py-1 text-gray-400 text-xs bg-gray-50 border-b border-gray-100">
                {unchanged.length} unchanged {unchanged.length === 1 ? 'field' : 'fields'}
              </td>
            </tr>
          )}
          {unchanged.map(entry => (
            <tr key={entry.key} className="opacity-50">
              <td className="px-3 py-1.5 font-medium text-gray-500 border-b border-gray-100">{entry.key}</td>
              <td className="px-3 py-1.5 border-b border-gray-100 font-mono text-gray-500">
                {renderValue(entry.before)}
              </td>
              <td className="px-3 py-1.5 border-b border-gray-100 font-mono text-gray-500">
                {renderValue(entry.after)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
