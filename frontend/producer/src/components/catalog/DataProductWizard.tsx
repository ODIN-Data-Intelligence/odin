import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useForm, Controller } from 'react-hook-form';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Alert from '@mui/material/Alert';
import Stepper from '@mui/material/Stepper';
import Step from '@mui/material/Step';
import StepLabel from '@mui/material/StepLabel';
import CloseIcon from '@mui/icons-material/Close';
import { dataProductApi } from '@datacatalog/shared';

const STEPS = ['Details', 'Ports', 'Review'] as const;
type Step = typeof STEPS[number];

interface FormValues {
  title: string;
  description: string;
  lifecycleStatus: string;
  purpose: string;
  informationSensitivity: string;
  keywords: string;
}

interface Props {
  onClose: () => void;
}

export default function DataProductWizard({ onClose }: Props) {
  const [step, setStep] = useState<Step>('Details');
  const { register, handleSubmit, control, formState: { errors } } = useForm<FormValues>({
    defaultValues: { lifecycleStatus: 'Ideation', informationSensitivity: 'Internal' },
  });

  const createMut = useMutation({
    mutationFn: (values: FormValues) =>
      dataProductApi.create({
        ...values,
        keywords: values.keywords ? values.keywords.split(',').map(k => k.trim()) : [],
        lifecycleStatus: values.lifecycleStatus as 'Ideation',
      }),
    onSuccess: onClose,
  });

  const stepIdx = STEPS.indexOf(step);

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        New Data Product
        <IconButton size="small" onClick={onClose}><CloseIcon fontSize="small" /></IconButton>
      </DialogTitle>
      <DialogContent sx={{ pt: '8px !important' }}>
        <Stepper activeStep={stepIdx} sx={{ mb: 3 }}>
          {STEPS.map(s => (
            <Step key={s}>
              <StepLabel>{s}</StepLabel>
            </Step>
          ))}
        </Stepper>

        {step === 'Details' && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <TextField
              {...register('title', { required: 'Title is required' })}
              label="Title"
              required
              size="small"
              fullWidth
              placeholder="Customer 360"
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
              placeholder="Describe this data product…"
            />
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
              <Controller
                name="lifecycleStatus"
                control={control}
                render={({ field }) => (
                  <FormControl size="small" fullWidth>
                    <InputLabel>Lifecycle</InputLabel>
                    <Select label="Lifecycle" {...field}>
                      {['Ideation', 'Design', 'Build', 'Deploy', 'Consume'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                    </Select>
                  </FormControl>
                )}
              />
              <Controller
                name="informationSensitivity"
                control={control}
                render={({ field }) => (
                  <FormControl size="small" fullWidth>
                    <InputLabel>Sensitivity</InputLabel>
                    <Select label="Sensitivity" {...field}>
                      {['Public', 'Internal', 'Confidential', 'Restricted'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                    </Select>
                  </FormControl>
                )}
              />
            </Box>
            <TextField {...register('keywords')} label="Keywords (comma-separated)" size="small" fullWidth placeholder="customer, crm, analytics" />
            <TextField {...register('purpose')} label="Purpose" size="small" fullWidth placeholder="Why does this data product exist?" />
          </Box>
        )}

        {step === 'Ports' && (
          <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
            Ports (input/output) can be configured after creation. Click Review to proceed.
          </Typography>
        )}

        {step === 'Review' && (
          <Box sx={{ py: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Review your inputs and click Create to finalize.
            </Typography>
            {createMut.error && (
              <Alert severity="error" sx={{ mt: 2 }}>Error: {String(createMut.error)}</Alert>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ justifyContent: 'space-between' }}>
        <Button
          onClick={() => stepIdx > 0 ? setStep(STEPS[stepIdx - 1]) : onClose()}
          sx={{ textTransform: 'none' }}
        >
          {stepIdx === 0 ? 'Cancel' : 'Back'}
        </Button>
        {stepIdx < STEPS.length - 1 ? (
          <Button variant="contained" onClick={() => setStep(STEPS[stepIdx + 1])} sx={{ textTransform: 'none' }}>
            Next
          </Button>
        ) : (
          <Button
            variant="contained"
            disabled={createMut.isPending}
            onClick={handleSubmit(data => createMut.mutate(data))}
            sx={{ textTransform: 'none' }}
          >
            {createMut.isPending ? 'Creating…' : 'Create'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
