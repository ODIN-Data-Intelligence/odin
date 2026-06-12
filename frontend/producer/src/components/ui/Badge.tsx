import Chip from '@mui/material/Chip';
import type { ChipProps } from '@mui/material/Chip';

interface BadgeProps {
  label: string;
  color?: ChipProps['color'];
  variant?: ChipProps['variant'];
}

export default function Badge({ label, color = 'default', variant = 'filled' }: BadgeProps) {
  return <Chip label={label} color={color} variant={variant} size="small" sx={{ height: 20, fontSize: 11 }} />;
}
