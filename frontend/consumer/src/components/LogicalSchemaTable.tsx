import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { logicalModelApi, logicalElementApi, useIriTranslations, resolveLabel } from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';
import VocabConceptBadge from './VocabConceptBadge';

type ClassificationLevel = NonNullable<LogicalDataElement['classification']>;

const CLASSIFICATION_STYLES: Record<ClassificationLevel, { badge: string; label: string }> = {
  PUBLIC:           { badge: 'bg-green-100 text-green-700 border-green-200',  label: 'Public' },
  INTERNAL:         { badge: 'bg-blue-100 text-blue-700 border-blue-200',     label: 'Internal' },
  CONFIDENTIAL:     { badge: 'bg-amber-100 text-amber-700 border-amber-200',  label: 'Confidential' },
  HIGH_CONFIDENTIAL:{ badge: 'bg-red-100 text-red-700 border-red-200',        label: 'High Confidential' },
};

function ClassificationBadge({ level, reasoning }: { level?: ClassificationLevel; reasoning?: string }) {
  if (!level) return <span className="text-gray-400 text-xs">—</span>;
  const { badge, label } = CLASSIFICATION_STYLES[level];
  return (
    <span
      title={reasoning ?? undefined}
      className={`inline-flex items-center px-1.5 py-0.5 rounded border text-xs font-medium cursor-default ${badge} ${reasoning ? 'cursor-help' : ''}`}
    >
      {label}
    </span>
  );
}

interface LogicalSchemaTableProps {
  datasetId: string;
}

export default function LogicalSchemaTable({ datasetId }: LogicalSchemaTableProps) {
  const { data: models = [] } = useQuery({
    queryKey: ['logical-models', datasetId],
    queryFn: () => logicalModelApi.list(datasetId),
  });

  const publishedModel = models.find(m => m.status === 'published') ?? models[0];

  const { data: elements = [] } = useQuery({
    queryKey: ['logical-elements', publishedModel?.id],
    queryFn: () => logicalElementApi.list(publishedModel!.id),
    enabled: !!publishedModel,
  });

  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  // Collect IRIs that have no stored label/description — translate those via API
  const unmappedIris = elements
    .flatMap(el => el.vocabMappings ?? [])
    .filter(m => !m.conceptLabel && !m.conceptDefinition)
    .map(m => m.conceptIri);
  const translations = useIriTranslations(unmappedIris);

  function toggleExpand(id: string) {
    setExpandedIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  if (!publishedModel) {
    return <p className="text-sm text-gray-400 text-center py-6">No logical model available.</p>;
  }

  const classifiedCount = elements.filter(el => el.classification).length;

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <p className="text-xs text-gray-500">
          {publishedModel.name} v{publishedModel.version}
          <span className="ml-2 px-1.5 py-0.5 bg-green-100 text-green-700 rounded text-xs">published</span>
        </p>
        <div className="flex items-center gap-3 text-xs text-gray-400">
          {classifiedCount > 0 && (
            <span>{classifiedCount}/{elements.length} classified</span>
          )}
          <span>{elements.length} elements</span>
        </div>
      </div>

      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500 uppercase">Business Name</th>
              <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500 uppercase">Logical Type</th>
              <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500 uppercase">Classification</th>
              <th className="px-3 py-2.5 text-left text-xs font-medium text-gray-500 uppercase">Vocabulary Concept</th>
              <th className="px-3 py-2.5" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {elements.map(el => (
              <>
                <tr key={el.id} className="hover:bg-gray-50">
                  <td className="px-3 py-2.5">
                    <Link
                      to={`/search?q=${encodeURIComponent(el.name)}`}
                      title={`Search all datasets with element "${el.name}"`}
                      className="font-medium text-gray-900 hover:text-blue-600 hover:underline"
                    >
                      {el.name}
                    </Link>
                    {el.label && el.label !== el.name && <p className="text-xs text-gray-500">{el.label}</p>}
                  </td>
                  <td className="px-3 py-2.5">
                    {el.logicalType ? (
                      <span className="px-1.5 py-0.5 bg-purple-100 text-purple-700 rounded text-xs font-medium">{el.logicalType}</span>
                    ) : <span className="text-gray-400 text-xs">—</span>}
                  </td>
                  <td className="px-3 py-2.5">
                    <ClassificationBadge level={el.classification} reasoning={el.classificationReasoning} />
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="flex flex-wrap gap-1">
                      {el.vocabMappings?.slice(0, 2).map(m => (
                        <VocabConceptBadge
                          key={m.id}
                          iri={m.conceptIri}
                          label={resolveLabel(translations, m.conceptIri, m.conceptLabel, m.conceptDefinition)}
                          matchType={m.matchType}
                        />
                      ))}
                      {(el.vocabMappings?.length ?? 0) > 2 && (
                        <span className="text-xs text-gray-400">+{(el.vocabMappings?.length ?? 0) - 2} more</span>
                      )}
                    </div>
                  </td>
                  <td className="px-3 py-2.5 text-right">
                    {(el.physicalColumn || el.physicalColumnRef) && (
                      <button
                        onClick={() => toggleExpand(el.id)}
                        className="text-xs text-gray-400 hover:text-gray-600"
                      >
                        {expandedIds.has(el.id) ? 'hide physical ▲' : 'show physical ▼'}
                      </button>
                    )}
                  </td>
                </tr>
                {expandedIds.has(el.id) && (
                  <tr key={`${el.id}-physical`} className="bg-blue-50">
                    <td colSpan={5} className="px-4 py-2">
                      <div className="flex items-center gap-4 text-xs text-gray-600">
                        <span className="font-mono font-medium">
                          {el.physicalColumn?.name ?? el.physicalColumnRef?.column ?? '—'}
                        </span>
                        <span className="text-gray-400">
                          {el.physicalColumn?.datatype ?? el.physicalColumnRef?.type ?? ''}
                        </span>
                        {el.physicalColumn?.description && (
                          <span className="text-gray-500">{el.physicalColumn.description}</span>
                        )}
                        {(el.physicalColumn?.required || el.isRequired) && (
                          <span className="px-1 py-0.5 bg-red-100 text-red-600 rounded">required</span>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </>
            ))}
            {elements.length === 0 && (
              <tr><td colSpan={5} className="px-3 py-6 text-center text-gray-400 text-xs">No elements defined</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
