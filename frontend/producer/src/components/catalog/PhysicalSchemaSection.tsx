import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { datasetApi, logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { CsvwColumn, LogicalDataElement, ColumnElementSuggestion } from '@datacatalog/shared';

interface Props {
  distributionId: string;
  datasetId: string;
  tenant: string;
  canAction: boolean;
}

// ── Suggest Mappings Modal ────────────────────────────────────────────────────

function SuggestMappingsModal({
  suggestions,
  columns,
  elements,
  onApply,
  onClose,
  isPending,
}: {
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
    setChecked(prev =>
      prev.size === suggestions.length ? new Set() : new Set(suggestions.map(s => s.columnId))
    );

  const colMap = new Map(columns.map(c => [c.id, c]));
  const elMap = new Map(elements.map(e => [e.id, e]));
  const selected = suggestions.filter(s => checked.has(s.columnId));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 flex flex-col max-h-[80vh]">
        {/* Header */}
        <div className="px-6 py-4 border-b flex items-center justify-between">
          <div>
            <h2 className="text-base font-semibold text-gray-900">AI Mapping Suggestions</h2>
            <p className="text-xs text-gray-500 mt-0.5">
              {suggestions.length} suggestion{suggestions.length !== 1 ? 's' : ''} based on column name similarity
            </p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">&times;</button>
        </div>

        {/* Body */}
        <div className="overflow-y-auto flex-1">
          {suggestions.length === 0 ? (
            <p className="px-6 py-8 text-sm text-gray-400 text-center">
              No unmapped columns could be matched to logical elements.
            </p>
          ) : (
            <table className="min-w-full text-sm">
              <thead className="sticky top-0 bg-gray-50 border-b border-gray-100">
                <tr>
                  <th className="pl-6 pr-3 py-2.5 w-8">
                    <input
                      type="checkbox"
                      checked={checked.size === suggestions.length}
                      onChange={toggleAll}
                      className="rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                    />
                  </th>
                  <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500">Physical Column</th>
                  <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500">Suggested Logical Element</th>
                  <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500">Confidence</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {suggestions.map(s => {
                  const col = colMap.get(s.columnId);
                  const el = elMap.get(s.suggestedElementId);
                  const pct = Math.round(s.confidence * 100);
                  const isChecked = checked.has(s.columnId);
                  return (
                    <tr
                      key={s.columnId}
                      onClick={() => toggle(s.columnId)}
                      className={`cursor-pointer transition-colors ${isChecked ? 'bg-purple-50' : 'hover:bg-gray-50'}`}
                    >
                      <td className="pl-6 pr-3 py-3" onClick={e => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggle(s.columnId)}
                          className="rounded border-gray-300 text-purple-600 focus:ring-purple-500"
                        />
                      </td>
                      <td className="px-3 py-3">
                        <span className="font-mono text-gray-900">{col?.name ?? s.columnId}</span>
                        {col?.datatype && (
                          <span className="ml-2 px-1 py-0.5 bg-blue-50 text-blue-600 rounded text-xs font-mono">
                            {col.datatype}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <span className="font-medium text-gray-800">{el?.name ?? s.suggestedElementName}</span>
                        {el?.logicalType && (
                          <span className="ml-2 text-xs text-gray-400">{el.logicalType}</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-1.5 rounded-full bg-gray-200 overflow-hidden">
                            <div
                              className={`h-full rounded-full ${pct >= 70 ? 'bg-green-500' : pct >= 40 ? 'bg-yellow-400' : 'bg-orange-400'}`}
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                          <span className={`text-xs font-medium ${pct >= 70 ? 'text-green-700' : pct >= 40 ? 'text-yellow-700' : 'text-orange-600'}`}>
                            {pct}%
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t bg-gray-50 flex items-center justify-between gap-3">
          <p className="text-xs text-gray-500">
            {checked.size} of {suggestions.length} selected
          </p>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-lg hover:bg-gray-100"
            >
              Cancel
            </button>
            <button
              onClick={() => onApply(selected)}
              disabled={isPending || selected.length === 0}
              className="px-4 py-2 text-sm bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50 font-medium"
            >
              {isPending ? 'Applying…' : `Apply ${selected.length} mapping${selected.length !== 1 ? 's' : ''}`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Mapping cell ──────────────────────────────────────────────────────────────

function MappingCell({
  col,
  elements,
  canAction,
  onBind,
  onUnbind,
}: {
  col: CsvwColumn;
  elements: LogicalDataElement[];
  canAction: boolean;
  onBind: (elementId: string, colId: string) => void;
  onUnbind: (elementId: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [selected, setSelected] = useState('');

  if (col.logicalDataElementId && !editing) {
    const el = elements.find(e => e.id === col.logicalDataElementId);
    return (
      <div className="flex items-center gap-2">
        <span className="text-green-700 font-medium">{el?.name ?? '✓ Mapped'}</span>
        {canAction && (
          <>
            <button
              onClick={() => { setSelected(col.logicalDataElementId!); setEditing(true); }}
              className="text-xs text-blue-600 hover:text-blue-700"
            >
              Change
            </button>
            <button
              onClick={() => onUnbind(col.logicalDataElementId!)}
              className="text-xs text-red-500 hover:text-red-600"
            >
              Unmap
            </button>
          </>
        )}
      </div>
    );
  }

  if (!canAction) {
    return <span className="text-gray-300">—</span>;
  }

  return (
    <div className="flex items-center gap-1.5">
      <select
        value={selected}
        onChange={e => setSelected(e.target.value)}
        className="border border-gray-200 rounded px-1.5 py-0.5 text-gray-600 max-w-[160px] focus:outline-none focus:ring-1 focus:ring-blue-400"
      >
        <option value="">— select —</option>
        {elements.map(e => (
          <option key={e.id} value={e.id}>{e.name}</option>
        ))}
      </select>
      {selected && (
        <button
          onClick={() => { onBind(selected, col.id); setSelected(''); setEditing(false); }}
          className="text-blue-600 hover:text-blue-700 text-xs font-medium"
        >
          Bind
        </button>
      )}
      {editing && (
        <button
          onClick={() => { setEditing(false); setSelected(''); }}
          className="text-gray-400 hover:text-gray-600 text-xs"
        >
          Cancel
        </button>
      )}
    </div>
  );
}

// ── Main section ──────────────────────────────────────────────────────────────

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

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100 bg-gray-50 flex items-center justify-between gap-4">
          <p className="text-sm font-semibold text-gray-800">
            Physical Schema
            {columns.length > 0 && <span className="ml-2 text-gray-400 font-normal text-xs">({columns.length} columns)</span>}
          </p>
          <div className="flex items-center gap-3">
            {hasElements && columns.length > 0 && canAction && (
              <button
                onClick={() => suggestMut.mutate()}
                disabled={suggestMut.isPending}
                className="text-xs text-purple-700 hover:text-purple-900 font-medium disabled:opacity-50"
              >
                {suggestMut.isPending ? 'Analysing…' : '✦ AI Suggest Mappings'}
              </button>
            )}
            {!hasElements && logicalModels.length === 0 && columns.length > 0 && (
              <p className="text-xs text-gray-400">
                No logical model —{' '}
                <Link to={`/${tenant}/datasets/${datasetId}`} className="text-blue-600 hover:underline">
                  create one on the Schema tab
                </Link>
              </p>
            )}
          </div>
        </div>

        {columns.length === 0 ? (
          <p className="px-4 py-3 text-sm text-gray-400">No physical schema available.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-xs">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50">
                  <th className="px-4 py-2 text-left text-gray-500 font-medium w-8">#</th>
                  <th className="px-4 py-2 text-left text-gray-500 font-medium">Column</th>
                  <th className="px-4 py-2 text-left text-gray-500 font-medium">Type</th>
                  <th className="px-4 py-2 text-left text-gray-500 font-medium">Nullable</th>
                  <th className="px-4 py-2 text-left text-gray-500 font-medium">Description</th>
                  <th className="px-4 py-2 text-left text-gray-500 font-medium">Logical Data Element</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {columns.map((col: CsvwColumn) => (
                  <tr key={col.id} className="hover:bg-gray-50 align-top">
                    <td className="px-4 py-2.5 text-gray-400">{col.ordinal}</td>
                    <td className="px-4 py-2.5 font-mono font-medium text-gray-900">{col.name}</td>
                    <td className="px-4 py-2.5">
                      {col.datatype && (
                        <span className="px-1.5 py-0.5 bg-blue-50 text-blue-700 rounded font-mono">{col.datatype}</span>
                      )}
                    </td>
                    <td className="px-4 py-2.5">
                      {col.required
                        ? <span className="text-red-600 font-medium">NOT NULL</span>
                        : <span className="text-gray-400">nullable</span>}
                    </td>
                    <td className="px-4 py-2.5 text-gray-500">{col.description ?? ''}</td>
                    <td className="px-4 py-2.5">
                      {hasElements ? (
                        <MappingCell
                          col={col}
                          elements={elements}
                          canAction={canAction}
                          onBind={(elementId, colId) => bindMut.mutate({ elementId, colId })}
                          onUnbind={(elementId) => unbindMut.mutate(elementId)}
                        />
                      ) : (
                        <span className="text-gray-300">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
