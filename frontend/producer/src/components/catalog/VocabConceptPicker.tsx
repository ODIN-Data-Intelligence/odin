import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import { vocabularyApi, iriFragment } from '@datacatalog/shared';
import type { VocabularyConcept } from '@datacatalog/shared';

interface Props {
  vocabularyId: string;
  vocabularyName: string;
  onSelect: (concept: VocabularyConcept) => void;
  onClose: () => void;
}

export default function VocabConceptPicker({ vocabularyId, vocabularyName, onSelect, onClose }: Props) {
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
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Search {vocabularyName} Concepts</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, pt: '8px !important', pb: 0 }}>
        <TextField
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="Type to search concepts (min 2 chars)…"
          size="small"
          fullWidth
          autoFocus
        />
        <Box sx={{ maxHeight: 320, overflowY: 'auto', mx: -3, px: 3, pb: 2 }}>
          {isLoading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={20} />
            </Box>
          )}
          {!isLoading && debouncedQuery.length >= 2 && concepts.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>No concepts found</Typography>
          )}
          {concepts.map(c => (
            <Box
              key={c.iri}
              component="button"
              onClick={() => { onSelect(c); onClose(); }}
              sx={{
                width: '100%', textAlign: 'left', px: 1.5, py: 1.25, border: 'none',
                cursor: 'pointer', borderRadius: 1, display: 'block', bgcolor: 'transparent',
                '&:hover': { bgcolor: 'primary.50' },
              }}
            >
              <Typography variant="body2" fontWeight={600}>{c.label}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={c.iri}>
                {iriFragment(c.iri)}
              </Typography>
              {c.definition && (
                <Typography variant="caption" color="text.secondary" sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                  {c.definition}
                </Typography>
              )}
            </Box>
          ))}
          {debouncedQuery.length < 2 && (
            <Typography variant="caption" color="text.disabled" sx={{ textAlign: 'center', display: 'block', py: 4 }}>
              Type at least 2 characters to search
            </Typography>
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}
