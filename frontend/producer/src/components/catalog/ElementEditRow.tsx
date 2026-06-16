import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Chip from '@mui/material/Chip';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import AddIcon from '@mui/icons-material/Add';
import { vocabMappingApi, resolveLabel, useIriTranslations } from '@datacatalog/shared';
import type { LogicalDataElement, LogicalElementVocabMapping } from '@datacatalog/shared';
import VocabMappingDialog from './VocabMappingDialog';

const CLASSIFICATION_VALUES = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'HIGH_CONFIDENTIAL'] as const;
const LOGICAL_TYPES = ['Text', 'Date', 'Measure', 'Count', 'Flag'];

const MATCH_TYPE_COLORS: Record<string, 'success' | 'primary' | 'secondary' | 'warning' | 'info'> = {
  exactMatch: 'success',
  closeMatch: 'primary',
  relatedMatch: 'secondary',
  broadMatch: 'warning',
  narrowMatch: 'info',
};

interface Props {
  element: LogicalDataElement;
  onSave: (id: string, updates: Partial<LogicalDataElement>) => void;
  onCancel: () => void;
  saving: boolean;
}

export default function ElementEditRow({ element, onSave, onCancel, saving }: Props) {
  const [name, setName] = useState(element.name);
  const [description, setDescription] = useState(element.description ?? '');
  const [logicalType, setLogicalType] = useState(element.logicalType ?? '');
  const [classification, setClassification] = useState<string>(element.classification ?? '');
  const [isPersonalInformation, setIsPersonalInformation] = useState(element.isPersonalInformation);
  const [isDirectIdentifier, setIsDirectIdentifier] = useState(element.isDirectIdentifier);
  const [vocabMappings, setVocabMappings] = useState<LogicalElementVocabMapping[]>(element.vocabMappings ?? []);
  const [showVocabDialog, setShowVocabDialog] = useState(false);

  const unmappedIris = vocabMappings.filter(m => !m.conceptLabel).map(m => m.conceptIri);
  const translations = useIriTranslations(unmappedIris);

  const deleteMappingMut = useMutation({
    mutationFn: (id: string) => vocabMappingApi.delete(id),
    onSuccess: (_, id) => setVocabMappings(prev => prev.filter(m => m.id !== id)),
  });

  const logicalTypeOptions = logicalType && !LOGICAL_TYPES.includes(logicalType)
    ? [...LOGICAL_TYPES, logicalType]
    : LOGICAL_TYPES;

  const handleSave = () => {
    if (!name.trim()) return;
    onSave(element.id, {
      name: name.trim(),
      description: description.trim() || undefined,
      logicalType: logicalType || undefined,
      classification: (classification as LogicalDataElement['classification']) || undefined,
      isPersonalInformation,
      isDirectIdentifier,
    });
  };

  return (
    <TableRow sx={{ bgcolor: 'action.selected' }}>
      <TableCell colSpan={7} sx={{ py: 1.5, px: 2 }}>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>

          {/* Scalar fields — left */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, flex: '1 1 220px', minWidth: 180 }}>
            <TextField
              label="Business Name"
              value={name}
              onChange={e => setName(e.target.value)}
              size="small"
              fullWidth
              required
              error={!name.trim()}
              helperText={!name.trim() ? 'Required' : undefined}
            />
            <TextField
              label="Description"
              value={description}
              onChange={e => setDescription(e.target.value)}
              size="small"
              fullWidth
              multiline
              minRows={2}
              maxRows={4}
            />
            <FormControl size="small" fullWidth>
              <InputLabel>Logical Type</InputLabel>
              <Select value={logicalType} onChange={e => setLogicalType(e.target.value)} label="Logical Type">
                <MenuItem value=""><em>None</em></MenuItem>
                {logicalTypeOptions.map(t => <MenuItem key={t} value={t}>{t}</MenuItem>)}
              </Select>
            </FormControl>
          </Box>

          {/* Controlled-vocabulary fields — right */}
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, flex: '0 1 200px', minWidth: 160 }}>
            <FormControl size="small" fullWidth>
              <InputLabel>Classification</InputLabel>
              <Select value={classification} onChange={e => setClassification(e.target.value)} label="Classification">
                <MenuItem value=""><em>None</em></MenuItem>
                {CLASSIFICATION_VALUES.map(v => <MenuItem key={v} value={v}>{v}</MenuItem>)}
              </Select>
            </FormControl>
            <Box sx={{ pl: 0.5, display: 'flex', flexDirection: 'column' }}>
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={isPersonalInformation}
                    onChange={e => setIsPersonalInformation(e.target.checked)}
                    color="error"
                  />
                }
                label={<Typography variant="caption">Personal Information (PII)</Typography>}
                sx={{ mr: 0 }}
              />
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={isDirectIdentifier}
                    onChange={e => setIsDirectIdentifier(e.target.checked)}
                    color="warning"
                  />
                }
                label={<Typography variant="caption">Direct Identifier</Typography>}
                sx={{ mr: 0 }}
              />
            </Box>
          </Box>

          {/* Vocab concepts */}
          <Box sx={{ flex: '1 1 180px', minWidth: 140 }}>
            <Typography variant="caption" color="text.secondary"
              sx={{ display: 'block', mb: 0.75, fontWeight: 600, textTransform: 'uppercase', fontSize: 10 }}>
              Vocabulary Concepts
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, alignItems: 'center' }}>
              {vocabMappings.map(m => (
                <Chip
                  key={m.id}
                  label={resolveLabel(translations, m.conceptIri, m.conceptLabel, m.conceptDefinition)}
                  color={MATCH_TYPE_COLORS[m.matchType] ?? 'default'}
                  size="small"
                  title={`${m.conceptIri} (${m.matchType})`}
                  onDelete={() => deleteMappingMut.mutate(m.id)}
                  disabled={deleteMappingMut.isPending}
                  sx={{ height: 20, fontSize: 11 }}
                />
              ))}
              <Chip
                icon={<AddIcon sx={{ fontSize: 12 }} />}
                label="Add concept"
                size="small"
                variant="outlined"
                onClick={() => setShowVocabDialog(true)}
                sx={{ height: 20, fontSize: 11, cursor: 'pointer' }}
              />
            </Box>
          </Box>
        </Box>

        {/* Save / Cancel */}
        <Box sx={{ display: 'flex', gap: 1, mt: 1.5, justifyContent: 'flex-end' }}>
          <Button size="small" onClick={onCancel} disabled={saving} sx={{ textTransform: 'none' }}>
            Cancel
          </Button>
          <Button
            size="small"
            variant="contained"
            onClick={handleSave}
            disabled={saving || !name.trim()}
            startIcon={saving ? <CircularProgress size={12} color="inherit" /> : undefined}
            sx={{ textTransform: 'none' }}
          >
            Save
          </Button>
        </Box>

        {showVocabDialog && (
          <VocabMappingDialog
            elementId={element.id}
            onAdded={mapping => setVocabMappings(prev => [...prev, mapping])}
            onClose={() => setShowVocabDialog(false)}
          />
        )}
      </TableCell>
    </TableRow>
  );
}
