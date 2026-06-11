import { useState, useEffect, useRef } from 'react';
import { vocabularyApi } from '../api/catalog';
import { iriFragment } from '../utils/iri';
import { loadCache, mergeIntoCache } from '../utils/iriCache';

/**
 * Resolves a list of RDF IRIs to human-readable labels using:
 *   1. localStorage cache (instant, persists across sessions)
 *   2. POST /api/v1/vocabularies/translate (batch, stored in cache)
 *   3. iriFragment() fallback while the API call is in-flight
 *
 * Returns a record mapping every requested IRI to its best available label.
 * Components should use: `translations[iri] ?? iriFragment(iri)`
 */
export function useIriTranslations(iris: string[]): Record<string, string> {
  const [translations, setTranslations] = useState<Record<string, string>>(loadCache);
  const pending = useRef<Set<string>>(new Set());

  // Stable dep key — re-run only when the actual IRI values change
  const key = iris.filter(Boolean).sort().join('\0');

  useEffect(() => {
    if (!key) return;

    const cache = loadCache();
    const uncached = iris.filter(
      iri => iri && !cache[iri] && !pending.current.has(iri),
    );

    if (uncached.length === 0) {
      // Everything in cache; sync state if needed
      setTranslations(prev => {
        const needsSync = iris.some(iri => iri && cache[iri] && !prev[iri]);
        return needsSync ? { ...prev, ...cache } : prev;
      });
      return;
    }

    uncached.forEach(iri => pending.current.add(iri));

    vocabularyApi
      .translateBatch(uncached)
      .then(({ translations: fresh }) => {
        uncached.forEach(iri => pending.current.delete(iri));
        const updated = mergeIntoCache(fresh);
        setTranslations(updated);
      })
      .catch(() => {
        uncached.forEach(iri => pending.current.delete(iri));
        // Fall back to iriFragment — no state update needed, callers already use it as fallback
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return translations;
}

/** Convenience: resolve a single IRI using the translations map from useIriTranslations. */
export function resolveLabel(
  translations: Record<string, string>,
  iri: string,
  storedLabel?: string | null,
  storedDescription?: string | null,
): string {
  if (storedLabel?.trim()) return storedLabel.trim();
  if (storedDescription?.trim()) return storedDescription.trim();
  return translations[iri] ?? iriFragment(iri);
}
