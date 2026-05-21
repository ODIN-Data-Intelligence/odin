import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { logicalModelApi, logicalElementApi } from '@datacatalog/shared';
import type { LogicalModel } from '@datacatalog/shared';
import Button from '../ui/Button';
import Badge from '../ui/Badge';

function humanizeIri(iri: string): string {
  const fragment = iri.split(/[/#]/).pop() ?? iri;
  return fragment.replace(/([A-Z])/g, ' $1').trim();
}

const MATCH_TYPE_COLORS: Record<string, string> = {
  exactMatch: 'bg-green-100 text-green-700',
  closeMatch: 'bg-blue-100 text-blue-700',
  relatedMatch: 'bg-purple-100 text-purple-700',
  broadMatch: 'bg-orange-100 text-orange-700',
  narrowMatch: 'bg-yellow-100 text-yellow-700',
};

interface Props {
  datasetId: string;
  models: LogicalModel[];
}

export default function LogicalModelEditor({ datasetId, models }: Props) {
  const qc = useQueryClient();
  const [selectedModelId, setSelectedModelId] = useState<string | null>(models[0]?.id ?? null);
  useEffect(() => {
    if (selectedModelId === null && models.length > 0) {
      setSelectedModelId(models[0].id);
    }
  }, [models, selectedModelId]);

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', selectedModelId],
    queryFn: () => logicalElementApi.list(selectedModelId!),
    enabled: !!selectedModelId,
  });

  const createModelMut = useMutation({
    mutationFn: () => logicalModelApi.create(datasetId, { name: 'Model v1', version: '1.0', status: 'draft' }),
    onSuccess: (m) => { qc.invalidateQueries({ queryKey: ['logical-models', datasetId] }); setSelectedModelId(m.id); },
  });

  const publishMut = useMutation({
    mutationFn: (id: string) => logicalModelApi.patchStatus(id, 'published'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['logical-models', datasetId] }),
  });

  const selectedModel = models.find(m => m.id === selectedModelId);

  if (models.length === 0) {
    return (
      <div className="text-center py-10">
        <p className="text-sm text-gray-500 mb-3">No logical model yet.</p>
        <Button onClick={() => createModelMut.mutate()} disabled={createModelMut.isPending}>
          Create Draft Model
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <select
            value={selectedModelId ?? ''}
            onChange={e => setSelectedModelId(e.target.value)}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            {models.map(m => <option key={m.id} value={m.id}>{m.name} v{m.version} ({m.status})</option>)}
          </select>
          {selectedModel && (
            <Badge
              label={selectedModel.status}
              className={selectedModel.status === 'published' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'}
            />
          )}
        </div>
        <div className="flex gap-2">
          {selectedModel?.status === 'draft' && (
            <Button size="sm" variant="secondary" onClick={() => publishMut.mutate(selectedModel.id)}>
              Publish
            </Button>
          )}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase w-8">#</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Business Name</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Logical Type</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Vocabulary Concepts</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {elements.map(el => (
              <tr key={el.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-gray-400 text-xs">{el.ordinal}</td>
                <td className="px-4 py-3">
                  <p className="font-medium text-gray-900">{el.name}</p>
                  {el.description && <p className="text-xs text-gray-500">{el.description}</p>}
                </td>
                <td className="px-4 py-3">
                  {el.logicalType ? (
                    <Badge label={el.logicalType} className="bg-purple-100 text-purple-700" />
                  ) : <span className="text-gray-400">—</span>}
                </td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {el.vocabMappings?.map(m => (
                      <span
                        key={m.id}
                        title={m.conceptIri}
                        className={`px-2 py-0.5 rounded text-xs font-medium cursor-default ${MATCH_TYPE_COLORS[m.matchType]}`}
                      >
                        {m.conceptLabel || humanizeIri(m.conceptIri)}
                      </span>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
            {elements.length === 0 && (
              <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">No elements in this model</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
