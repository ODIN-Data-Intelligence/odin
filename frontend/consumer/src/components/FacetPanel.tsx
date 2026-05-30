import { useState } from 'react';
import type { SearchFacets } from '@datacatalog/shared';
import { iriFragment, useIriTranslations } from '@datacatalog/shared';
import { useSearchStore } from '../store/searchStore';

export const humanizeConcept = iriFragment;

interface FacetPanelProps {
  facets: SearchFacets;
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  DATASET: 'Dataset',
  DATA_PRODUCT: 'Data Product',
  DISTRIBUTION: 'Distribution',
};

export default function FacetPanel({ facets }: FacetPanelProps) {
  const { filters, setFilter, clearFilters } = useSearchStore();
  const [collapsed, setCollapsed] = useState(false);

  // Collect all IRI-valued facet keys for batch translation
  const iriKeys = [
    ...(facets.fiboConcepts ?? []).map(f => f.key),
    ...(facets.vocabConcepts ?? []).map(f => f.key),
    ...(facets.themes ?? []).map(f => f.key),
  ];
  const translations = useIriTranslations(iriKeys);
  const label = (k: string) => translations[k] ?? iriFragment(k);

  const hasFilters = Object.values(filters).some(v => v !== undefined && v !== '');

  if (collapsed) {
    return (
      <aside className="w-8 flex-shrink-0">
        <button onClick={() => setCollapsed(false)} className="text-gray-400 hover:text-gray-600 p-1" title="Expand filters">▶</button>
      </aside>
    );
  }

  return (
    <aside className="w-56 flex-shrink-0 space-y-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-700">Filters</h2>
        <div className="flex items-center gap-2">
          {hasFilters && (
            <button onClick={clearFilters} className="text-xs text-blue-600 hover:underline">Clear all</button>
          )}
          <button onClick={() => setCollapsed(true)} className="text-gray-400 hover:text-gray-600" title="Collapse">◀</button>
        </div>
      </div>

      <FacetGroup
        title="Type"
        items={facets.entityTypes ?? []}
        selected={filters.type}
        getLabel={k => ENTITY_TYPE_LABELS[k] ?? k}
        onSelect={v => setFilter('type', v === filters.type ? undefined : v)}
      />

      <FacetGroup
        title="Format"
        items={facets.formats ?? []}
        selected={filters.format}
        getLabel={k => k}
        onSelect={v => setFilter('format', v === filters.format ? undefined : v)}
      />

      <FacetGroup
        title="Lifecycle"
        items={facets.lifecycleStatuses ?? []}
        selected={filters.lifecycleStatus}
        getLabel={k => k}
        onSelect={v => setFilter('lifecycleStatus', v === filters.lifecycleStatus ? undefined : v)}
      />

      <FacetGroup
        title="Vocabulary"
        items={facets.vocabularyTypes ?? []}
        selected={filters.vocab}
        getLabel={k => k.charAt(0).toUpperCase() + k.slice(1)}
        onSelect={v => setFilter('vocab', v === filters.vocab ? undefined : v)}
      />

      {(facets.semanticTypes ?? []).length > 0 && (
        <FacetGroup
          title="Semantic Types"
          items={facets.semanticTypes ?? []}
          selected={filters.semanticType}
          getLabel={k => k}
          onSelect={v => setFilter('semanticType', v === filters.semanticType ? undefined : v)}
        />
      )}

      {(facets.fiboConcepts ?? []).length > 0 && (
        <FacetGroup
          title="FIBO Concepts"
          items={facets.fiboConcepts ?? []}
          selected={filters.fiboConcept}
          getLabel={label}
          onSelect={v => setFilter('fiboConcept', v === filters.fiboConcept ? undefined : v)}
        />
      )}

      <FacetGroup
        title="Keywords"
        items={facets.keywords ?? []}
        selected={filters.keyword}
        getLabel={k => k}
        onSelect={v => setFilter('keyword', v === filters.keyword ? undefined : v)}
      />

      <FacetGroup
        title="Themes"
        items={facets.themes ?? []}
        selected={filters.theme}
        getLabel={label}
        onSelect={v => setFilter('theme', v === filters.theme ? undefined : v)}
      />

      <FacetGroup
        title="Vocabulary Concepts"
        items={facets.vocabConcepts ?? []}
        selected={filters.vocabConcept}
        getLabel={label}
        onSelect={v => setFilter('vocabConcept', v === filters.vocabConcept ? undefined : v)}
      />

      <div>
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Has Lineage</p>
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={!!filters.hasLineage}
            onChange={e => setFilter('hasLineage', e.target.checked ? true : undefined)}
            className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          <span className="text-sm text-gray-700">With lineage</span>
        </label>
      </div>
    </aside>
  );
}

function FacetGroup({
  title, items, selected, getLabel, onSelect,
}: {
  title: string;
  items: { key: string; count: number }[];
  selected?: string;
  getLabel: (k: string) => string;
  onSelect: (k: string) => void;
}) {
  if (items.length === 0) return null;
  return (
    <div>
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">{title}</p>
      <ul className="space-y-1">
        {items.map(f => (
          <li key={f.key}>
            <button
              onClick={() => onSelect(f.key)}
              className={`w-full text-left flex items-center justify-between px-2 py-1 rounded text-sm transition-colors ${selected === f.key ? 'bg-blue-100 text-blue-700 font-medium' : 'text-gray-600 hover:bg-gray-100'}`}
            >
              <span className="truncate">{getLabel(f.key)}</span>
              <span className={`text-xs ml-2 ${selected === f.key ? 'text-blue-500' : 'text-gray-400'}`}>{f.count}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
