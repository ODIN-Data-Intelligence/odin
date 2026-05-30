const CACHE_KEY = 'odin:iri-translations';
const MAX_ENTRIES = 2000;

export function loadCache(): Record<string, string> {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    return raw ? (JSON.parse(raw) as Record<string, string>) : {};
  } catch {
    return {};
  }
}

export function mergeIntoCache(fresh: Record<string, string>): Record<string, string> {
  try {
    const existing = loadCache();
    const merged = { ...existing, ...fresh };
    // Trim to cap: keep the most recently added entries
    const entries = Object.entries(merged);
    const trimmed = entries.length > MAX_ENTRIES
      ? Object.fromEntries(entries.slice(entries.length - MAX_ENTRIES))
      : merged;
    localStorage.setItem(CACHE_KEY, JSON.stringify(trimmed));
    return trimmed;
  } catch {
    return { ...loadCache(), ...fresh };
  }
}
