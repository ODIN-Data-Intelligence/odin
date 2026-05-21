import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { vocabularyApi } from '@datacatalog/shared';
import type { VocabularyConcept } from '@datacatalog/shared';

interface VocabConceptPickerProps {
  vocabularyId: string;
  vocabularyName: string;
  onSelect: (concept: VocabularyConcept) => void;
  onClose: () => void;
}

export default function VocabConceptPicker({ vocabularyId, vocabularyName, onSelect, onClose }: VocabConceptPickerProps) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(t);
  }, [query]);

  const { data: concepts = [], isLoading } = useQuery({
    queryKey: ['vocab-concepts', vocabularyId, debouncedQuery],
    queryFn: () => vocabularyApi.searchConcepts(vocabularyId, debouncedQuery),
    enabled: debouncedQuery.length >= 2,
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4">
        <div className="px-5 py-4 border-b flex items-center justify-between">
          <h3 className="font-semibold text-gray-900">Search {vocabularyName} Concepts</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <div className="px-5 py-3">
          <input
            autoFocus
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Type to search concepts (min 2 chars)..."
            className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
        </div>
        <div className="px-5 pb-5 max-h-80 overflow-y-auto">
          {isLoading && <p className="text-sm text-gray-400 text-center py-4">Searching...</p>}
          {!isLoading && debouncedQuery.length >= 2 && concepts.length === 0 && (
            <p className="text-sm text-gray-400 text-center py-4">No concepts found</p>
          )}
          {concepts.map(c => (
            <button
              key={c.iri}
              onClick={() => { onSelect(c); onClose(); }}
              className="w-full text-left px-3 py-2.5 rounded hover:bg-blue-50 transition-colors"
            >
              <p className="text-sm font-medium text-gray-900">{c.label}</p>
              <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{c.iri}</p>
              {c.definition && <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{c.definition}</p>}
            </button>
          ))}
          {debouncedQuery.length < 2 && (
            <p className="text-xs text-gray-400 text-center py-4">Type at least 2 characters to search</p>
          )}
        </div>
      </div>
    </div>
  );
}
