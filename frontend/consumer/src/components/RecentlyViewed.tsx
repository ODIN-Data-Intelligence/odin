import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';

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
    <Box>
      <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1.5 }}>Recently Viewed</Typography>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
        {entries.map(e => (
          <Chip
            key={e.id}
            label={e.title}
            variant="outlined"
            onClick={() => navigate(`/search?ds=${e.id}`)}
            size="small"
            sx={{ cursor: 'pointer' }}
          />
        ))}
      </Box>
    </Box>
  );
}
