import Chip, { ChipProps } from '@mui/material/Chip';

interface BadgeProps {
  label: string;
  color?: ChipProps['color'];
  sx?: ChipProps['sx'];
}

export default function Badge({ label, color = 'default', sx }: BadgeProps) {
  return <Chip label={label} color={color} size="small" sx={sx} />;
}
