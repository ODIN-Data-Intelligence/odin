import { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import { vocabularyApi, vocabMappingApi, iriFragment } from '@datacatalog/shared';
import type { VocabularyConcept, LogicalElementVocabMapping } from '@datacatalog/shared';

const MATCH_TYPES = ['exactMatch', 'closeMatch', 'relatedMatch', 'broadMatch', 'narrowMatch'] as const;

interface Props {
  elementId: string;
  onAdded: (mapping: LogicalElementVocabMapping) => void;
  onClose: () => void;
}

export default function VocabMappingDialog({ elementId, onAdded, onClose }: Props) {
  const [vocabId, setVocabId] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [selectedConcept, setSelectedConcept] = useState<VocabularyConcept | null>(null);
  const [matchType, setMatchType] = useState<string>('exactMatch');
  const [showManual, setShowManual] = useState(false);
  const [manualIri, setManualIri] = useState('');
  const [iriError, setIriError] = useState('');

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(t);
  }, [query]);

  const { data: vocabularies = [] } = useQuery({
    queryKey: ['vocabularies'],
    queryFn: () => vocabularyApi.list(),
    staleTime: 300_000,
  });

  const { data: concepts = [], isLoading: conceptsLoading } = useQuery({
    queryKey: ['vocab-concepts', vocabId, debouncedQuery],
    queryFn: () => vocabularyApi.searchConcepts(vocabId, debouncedQuery),
    enabled: !!vocabId && debouncedQuery.length >= 2,
  });

  const addMut = useMutation({
    mutationFn: (body: Partial<LogicalElementVocabMapping>) => vocabMappingApi.create(elementId, body),
    onSuccess: mapping => { onAdded(mapping); onClose(); },
  });

  const selectedVocab = vocabularies.find(v => v.id === vocabId);

  const handleAdd = () => {
    if (showManual) {
      if (!/^https?:\/\/.+/.test(manualIri)) {
        setIriError('Must be a valid http:// or https:// IRI');
        return;
      }
      addMut.mutate({
        conceptIri: manualIri,
        matchType: matchType as LogicalElementVocabMapping['matchType'],
      });
    } else {
      if (!selectedConcept) return;
      addMut.mutate({
        conceptIri: selectedConcept.iri,
        conceptLabel: selectedConcept.label,
        conceptDefinition: selectedConcept.definition,
        matchType: matchType as LogicalElementVocabMapping['matchType'],
      });
    }
  };

  const canAdd = showManual ? !!manualIri : !!selectedConcept;

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ pb: 1 }}>Add Vocabulary Concept</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '8px !important' }}>

        <FormControl size="small" fullWidth>
          <InputLabel>Vocabulary</InputLabel>
          <Select
            value={vocabId}
            onChange={e => { setVocabId(e.target.value); setSelectedConcept(null); setQuery(''); }}
            label="Vocabulary"
          >
            {vocabularies.map(v => <MenuItem key={v.id} value={v.id}>{v.name}</MenuItem>)}
          </Select>
        </FormControl>

        {vocabId && !showManual && (
          <>
            <TextField
              value={query}
              onChange={e => { setQuery(e.target.value); setSelectedConcept(null); }}
              placeholder={`Search ${selectedVocab?.name ?? 'vocabulary'} concepts (min 2 chars)…`}
              size="small"
              fullWidth
              autoFocus
            />
            <Box sx={{ maxHeight: 220, overflowY: 'auto', border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
              {conceptsLoading && (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                  <CircularProgress size={18} />
                </Box>
              )}
              {!conceptsLoading && debouncedQuery.length >= 2 && concepts.length === 0 && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', py: 3 }}>
                  No concepts found
                </Typography>
              )}
              {!conceptsLoading && debouncedQuery.length < 2 && (
                <Typography variant="caption" color="text.disabled" sx={{ display: 'block', textAlign: 'center', py: 3 }}>
                  Type at least 2 characters to search
                </Typography>
              )}
              {concepts.map(c => (
                <Box
                  key={c.iri}
                  component="button"
                  onClick={() => setSelectedConcept(c)}
                  sx={{
                    width: '100%', textAlign: 'left', px: 1.5, py: 1, border: 'none', cursor: 'pointer',
                    borderRadius: 0, display: 'block',
                    bgcolor: selectedConcept?.iri === c.iri ? 'primary.50' : 'transparent',
                    '&:hover': { bgcolor: 'action.hover' },
                  }}
                >
                  <Typography variant="body2" fontWeight={600}>{c.label}</Typography>
                  <Typography variant="caption" color="text.secondary" title={c.iri}
                    sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', display: 'block' }}>
                    {iriFragment(c.iri)}
                  </Typography>
                  {c.definition && (
                    <Typography variant="caption" color="text.secondary"
                      sx={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                      {c.definition}
                    </Typography>
                  )}
                </Box>
              ))}
            </Box>
            {selectedConcept && (
              <Typography variant="caption" color="success.main">
                Selected: {selectedConcept.label} ({iriFragment(selectedConcept.iri)})
              </Typography>
            )}
          </>
        )}

        {showManual && (
          <TextField
            value={manualIri}
            onChange={e => { setManualIri(e.target.value); setIriError(''); }}
            placeholder="https://example.org/ontology#Concept"
            size="small"
            fullWidth
            autoFocus
            error={!!iriError}
            helperText={iriError || 'Must be a valid http:// or https:// IRI'}
            label="Concept IRI"
          />
        )}

        <Typography
          variant="caption"
          color="primary"
          sx={{ cursor: 'pointer', alignSelf: 'flex-start' }}
          onClick={() => { setShowManual(m => !m); setManualIri(''); setIriError(''); setSelectedConcept(null); setQuery(''); }}
        >
          {showManual ? 'Search by label instead' : 'Enter IRI manually'}
        </Typography>

        <FormControl size="small" fullWidth>
          <InputLabel>Match Type</InputLabel>
          <Select value={matchType} onChange={e => setMatchType(e.target.value)} label="Match Type">
            {MATCH_TYPES.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </Select>
        </FormControl>

      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} size="small">Cancel</Button>
        <Button
          variant="contained"
          size="small"
          onClick={handleAdd}
          disabled={!canAdd || addMut.isPending}
          startIcon={addMut.isPending ? <CircularProgress size={12} color="inherit" /> : undefined}
        >
          Add
        </Button>
      </DialogActions>
    </Dialog>
  );
}
