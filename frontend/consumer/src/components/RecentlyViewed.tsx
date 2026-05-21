import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

interface RecentEntry {
  id: string;
  title: string;
  viewedAt: number;
}

const KEY = 'dc_recently_viewed';
const MAX = 5;

export function recordRecentlyViewed(id: string, title: string) {
  try {
    const existing: RecentEntry[] = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    const filtered = existing.filter(e => e.id !== id);
    const updated = [{ id, title, viewedAt: Date.now() }, ...filtered].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(updated));
  } catch { /* ignore */ }
}

export default function RecentlyViewed() {
  const navigate = useNavigate();
  const [entries, setEntries] = useState<RecentEntry[]>([]);

  useEffect(() => {
    try {
      setEntries(JSON.parse(localStorage.getItem(KEY) ?? '[]'));
    } catch { /* ignore */ }
  }, []);

  if (entries.length === 0) return null;

  return (
    <div>
      <h2 className="text-base font-semibold text-gray-800 mb-3">Recently Viewed</h2>
      <div className="flex flex-wrap gap-2">
        {entries.map(e => (
          <button
            key={e.id}
            onClick={() => navigate(`/search?ds=${e.id}`)}
            className="px-3 py-1.5 bg-white border border-gray-200 text-sm text-gray-700 rounded-full hover:border-blue-300 transition-colors"
          >
            {e.title}
          </button>
        ))}
      </div>
    </div>
  );
}
