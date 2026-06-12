import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm, Controller } from 'react-hook-form';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import FormGroup from '@mui/material/FormGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { userApi } from '@datacatalog/shared';
import type { UserInviteRequest } from '@datacatalog/shared';

const ROLES = [
  { value: 'administrator',   label: 'Administrator' },
  { value: 'data-governance', label: 'Data Governance' },
  { value: 'data-owner',      label: 'Data Owner' },
  { value: 'data-steward',    label: 'Data Steward' },
];

interface Props { onClose: () => void; }

export default function UserInviteForm({ onClose }: Props) {
  const qc = useQueryClient();
  const { register, handleSubmit, control, formState: { errors } } = useForm<UserInviteRequest>({
    defaultValues: { roles: [] },
  });

  const inviteMut = useMutation({
    mutationFn: (values: UserInviteRequest) => userApi.invite(values),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['users'] }); onClose(); },
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit(data => inviteMut.mutate(data))}>
        <DialogTitle>Invite User</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '12px !important' }}>
          <TextField
            {...register('email', { required: 'Email is required' })}
            label="Email"
            type="email"
            required
            size="small"
            fullWidth
            placeholder="jane.smith@example.com"
            error={!!errors.email}
            helperText={errors.email?.message}
          />

          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField {...register('firstName')} label="First name" size="small" fullWidth placeholder="Jane" />
            <TextField {...register('lastName')} label="Last name" size="small" fullWidth placeholder="Smith" />
          </Box>

          <Box>
            <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>Role *</Typography>
            <Controller
              name="roles"
              control={control}
              rules={{ validate: v => (v && v.length > 0) || 'At least one role is required' }}
              render={({ field }) => (
                <FormGroup>
                  {ROLES.map(r => (
                    <FormControlLabel
                      key={r.value}
                      control={
                        <Checkbox
                          size="small"
                          checked={(field.value ?? []).includes(r.value)}
                          onChange={e => {
                            const current = field.value ?? [];
                            field.onChange(
                              e.target.checked
                                ? [...current, r.value]
                                : current.filter((v: string) => v !== r.value)
                            );
                          }}
                        />
                      }
                      label={<Typography variant="body2">{r.label}</Typography>}
                    />
                  ))}
                </FormGroup>
              )}
            />
            {errors.roles && <Typography variant="caption" color="error">{errors.roles.message}</Typography>}
          </Box>

          {inviteMut.isError && (
            <Alert severity="error">Failed to invite user. The email address may already be registered.</Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} sx={{ textTransform: 'none' }}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={inviteMut.isPending} sx={{ textTransform: 'none' }}>
            {inviteMut.isPending ? 'Inviting…' : 'Send Invite'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
