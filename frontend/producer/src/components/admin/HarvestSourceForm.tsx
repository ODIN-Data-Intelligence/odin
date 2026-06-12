import { useMutation } from '@tanstack/react-query';
import { useForm, Controller } from 'react-hook-form';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { harvestSourceApi } from '@datacatalog/shared';
import type { HarvestSource, SourceType } from '@datacatalog/shared';

const SOURCE_TYPES: { value: SourceType; label: string }[] = [
  { value: 'dcat_http', label: 'DCAT HTTP' },
  { value: 'aws_glue', label: 'AWS Glue' },
  { value: 'snowflake', label: 'Snowflake' },
  { value: 'teradata', label: 'Teradata' },
];

interface FormValues {
  name: string;
  sourceType: SourceType;
  baseUrl?: string;
  region?: string;
  databaseName?: string;
  schemaFilter?: string;
}

interface Props {
  onClose: () => void;
  source?: HarvestSource;
}

export default function HarvestSourceForm({ onClose, source }: Props) {
  const isEdit = !!source;

  const { register, handleSubmit, control, watch } = useForm<FormValues>({
    defaultValues: source
      ? {
          name: source.name,
          sourceType: source.sourceType,
          baseUrl: source.baseUrl ?? '',
          region: source.region ?? '',
          databaseName: source.databaseName ?? '',
          schemaFilter: source.schemaFilter?.join(', ') ?? '',
        }
      : { sourceType: 'dcat_http' },
  });

  const sourceType = watch('sourceType');

  const saveMut = useMutation({
    mutationFn: (values: FormValues) => {
      const body = {
        ...values,
        schemaFilter: values.schemaFilter ? values.schemaFilter.split(',').map(s => s.trim()) : [],
      };
      return isEdit ? harvestSourceApi.update(source!.id, body) : harvestSourceApi.create(body);
    },
    onSuccess: onClose,
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(data => saveMut.mutate(data))}>
        <DialogTitle>{isEdit ? 'Edit Harvest Source' : 'Add Harvest Source'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <TextField {...register('name', { required: true })} label="Name" required size="small" fullWidth placeholder="Production Snowflake" />

          <Controller
            name="sourceType"
            control={control}
            render={({ field }) => (
              <FormControl size="small" fullWidth required>
                <InputLabel>Source Type</InputLabel>
                <Select label="Source Type" {...field} disabled={isEdit}>
                  {SOURCE_TYPES.map(t => <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>)}
                </Select>
              </FormControl>
            )}
          />

          {sourceType === 'dcat_http' && (
            <TextField {...register('baseUrl')} label="Base URL" size="small" fullWidth placeholder="https://catalog.example.com/dcat" />
          )}

          {sourceType === 'aws_glue' && (
            <TextField {...register('region')} label="AWS Region" size="small" fullWidth placeholder="us-east-1" />
          )}

          {(sourceType === 'snowflake' || sourceType === 'teradata') && (
            <>
              <TextField
                {...register('baseUrl')}
                label="JDBC URL / Account"
                size="small"
                fullWidth
                placeholder={sourceType === 'snowflake' ? 'account.snowflakecomputing.com' : 'jdbc:teradata://host'}
              />
              <TextField {...register('databaseName')} label="Database" size="small" fullWidth placeholder="MY_DATABASE" />
              <TextField {...register('schemaFilter')} label="Schema Filter" size="small" fullWidth placeholder="PUBLIC,ANALYTICS" helperText="comma-separated" />
            </>
          )}

          {saveMut.isError && <Alert severity="error">Error: {String(saveMut.error)}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
            {saveMut.isPending ? 'Saving...' : isEdit ? 'Save Changes' : 'Add Source'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
