import { useMutation, useQuery } from '@tanstack/react-query';
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
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { harvestSourceApi, harvestJobApi } from '@datacatalog/shared';
import type { HarvestJob } from '@datacatalog/shared';

interface FormValues {
  name: string;
  sourceId: string;
  scheduleCron: string;
  fullRefresh: boolean;
  enabled: boolean;
}

interface Props {
  onClose: () => void;
  job?: HarvestJob;
}

export default function HarvestJobForm({ onClose, job }: Props) {
  const isEdit = !!job;

  const { data: sources = [] } = useQuery({
    queryKey: ['harvest-sources'],
    queryFn: () => harvestSourceApi.list(),
  });

  const { register, handleSubmit, control } = useForm<FormValues>({
    defaultValues: job
      ? {
          name: job.name,
          sourceId: job.sourceId,
          scheduleCron: job.scheduleCron ?? '',
          fullRefresh: job.fullRefresh,
          enabled: job.enabled,
        }
      : { sourceId: '', scheduleCron: '', fullRefresh: false, enabled: true },
  });

  const saveMut = useMutation({
    mutationFn: (values: FormValues) => {
      const body = {
        ...values,
        scheduleCron: values.scheduleCron.trim() || undefined,
      };
      return isEdit
        ? harvestJobApi.update(job!.id, { name: body.name, scheduleCron: body.scheduleCron, fullRefresh: body.fullRefresh, enabled: body.enabled, sourceId: job!.sourceId })
        : harvestJobApi.create(body);
    },
    onSuccess: onClose,
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(data => saveMut.mutate(data))}>
        <DialogTitle>{isEdit ? 'Edit Harvest Job' : 'Add Harvest Job'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <TextField {...register('name', { required: true })} label="Name" required size="small" fullWidth placeholder="Daily ECB Harvest" />

          <Controller
            name="sourceId"
            control={control}
            rules={{ required: !isEdit }}
            render={({ field }) => (
              <FormControl size="small" fullWidth required={!isEdit}>
                <InputLabel>Source</InputLabel>
                <Select label="Source" {...field} disabled={isEdit}>
                  {sources.map(s => <MenuItem key={s.id} value={s.id}>{s.name}</MenuItem>)}
                </Select>
              </FormControl>
            )}
          />

          <TextField
            {...register('scheduleCron')}
            label="Schedule (cron)"
            size="small"
            fullWidth
            placeholder="0 2 * * *"
            helperText="Leave blank for manual-only"
          />

          <Controller
            name="fullRefresh"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={<Checkbox checked={field.value} onChange={e => field.onChange(e.target.checked)} />}
                label="Full refresh (re-harvest all entities)"
              />
            )}
          />

          <Controller
            name="enabled"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={<Checkbox checked={field.value} onChange={e => field.onChange(e.target.checked)} />}
                label="Enabled"
              />
            )}
          />

          {saveMut.isError && <Alert severity="error">Error: {String(saveMut.error)}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={saveMut.isPending} sx={{ textTransform: 'none' }}>
            {saveMut.isPending ? 'Saving...' : isEdit ? 'Save Changes' : 'Add Job'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
