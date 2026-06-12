import { useForm, Controller } from 'react-hook-form';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import type { Dataset } from '@datacatalog/shared';

interface FormValues {
  title: string;
  description: string;
  keywordsRaw: string;
  accrualPeriodicity: string;
  version: string;
  license: string;
  languageRaw: string;
  themesRaw: string;
}

interface Props {
  defaultValues?: Partial<Dataset>;
  onSubmit: (data: Partial<Dataset>) => void;
  isSubmitting?: boolean;
  submitLabel?: string;
  onCancel?: () => void;
}

const PERIODICITY_OPTIONS = [
  '', 'continuous', 'daily', 'weekly', 'monthly', 'quarterly', 'annual', 'irregular',
] as const;

function splitTags(raw: string): string[] {
  return raw.split(',').map(s => s.trim()).filter(Boolean);
}

export default function DatasetForm({ defaultValues, onSubmit, isSubmitting, submitLabel = 'Save', onCancel }: Props) {
  const { register, handleSubmit, control, formState: { errors } } = useForm<FormValues>({
    defaultValues: {
      title: defaultValues?.title ?? '',
      description: defaultValues?.description ?? '',
      keywordsRaw: (defaultValues?.keywords ?? []).join(', '),
      accrualPeriodicity: defaultValues?.accrualPeriodicity ?? '',
      version: defaultValues?.version ?? '',
      license: defaultValues?.license ?? '',
      languageRaw: (defaultValues?.language ?? []).join(', '),
      themesRaw: (defaultValues?.themes ?? []).join(', '),
    },
  });

  function convert(values: FormValues): Partial<Dataset> {
    return {
      title: values.title,
      description: values.description || undefined,
      keywords: splitTags(values.keywordsRaw),
      accrualPeriodicity: values.accrualPeriodicity || undefined,
      version: values.version || undefined,
      license: values.license || undefined,
      language: splitTags(values.languageRaw),
      themes: splitTags(values.themesRaw),
    };
  }

  return (
    <Box component="form" onSubmit={handleSubmit(values => onSubmit(convert(values)))} sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
      <TextField
        {...register('title', { required: 'Title is required' })}
        label="Title"
        required
        size="small"
        fullWidth
        placeholder="e.g. Trade Blotter"
        error={!!errors.title}
        helperText={errors.title?.message}
      />

      <TextField
        {...register('description')}
        label="Description"
        size="small"
        fullWidth
        multiline
        rows={3}
        placeholder="Describe this dataset…"
      />

      <TextField
        {...register('keywordsRaw')}
        label="Keywords"
        size="small"
        fullWidth
        placeholder="trading, blotter, positions"
        InputProps={{ endAdornment: <Typography variant="caption" color="text.disabled" sx={{ whiteSpace: 'nowrap' }}>comma-separated</Typography> }}
      />

      <Controller
        name="accrualPeriodicity"
        control={control}
        render={({ field }) => (
          <FormControl size="small" fullWidth>
            <InputLabel>Accrual Periodicity</InputLabel>
            <Select label="Accrual Periodicity" {...field}>
              {PERIODICITY_OPTIONS.map(opt => (
                <MenuItem key={opt} value={opt}>{opt || '— none —'}</MenuItem>
              ))}
            </Select>
          </FormControl>
        )}
      />

      <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
        <TextField {...register('version')} label="Version" size="small" fullWidth placeholder="1.0" />
        <TextField
          {...register('languageRaw')}
          label="Language"
          size="small"
          fullWidth
          placeholder="en"
          InputProps={{ endAdornment: <Typography variant="caption" color="text.disabled" sx={{ whiteSpace: 'nowrap' }}>e.g. en, fr</Typography> }}
        />
      </Box>

      <TextField {...register('license')} label="License" size="small" fullWidth placeholder="https://creativecommons.org/licenses/by/4.0/" />

      <TextField
        {...register('themesRaw')}
        label="Themes"
        size="small"
        fullWidth
        placeholder="Finance, Risk"
        InputProps={{ endAdornment: <Typography variant="caption" color="text.disabled" sx={{ whiteSpace: 'nowrap' }}>IRIs or labels</Typography> }}
      />

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pt: 0.5 }}>
        <Button type="submit" variant="contained" disabled={isSubmitting} sx={{ textTransform: 'none' }}>
          {isSubmitting ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="outlined" onClick={onCancel} sx={{ textTransform: 'none' }}>Cancel</Button>
        )}
      </Box>
    </Box>
  );
}
