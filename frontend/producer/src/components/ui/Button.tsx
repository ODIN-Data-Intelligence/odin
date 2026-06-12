import MuiButton from '@mui/material/Button';
import type { ButtonProps as MuiButtonProps } from '@mui/material/Button';

interface ButtonProps extends Omit<MuiButtonProps, 'variant' | 'size' | 'color'> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
}

const variantMap = {
  primary:   { variant: 'contained' as const, color: 'primary' as const },
  secondary: { variant: 'outlined'  as const, color: 'primary' as const },
  danger:    { variant: 'contained' as const, color: 'error'   as const },
  ghost:     { variant: 'text'      as const, color: 'inherit' as const },
};

const sizeMap = {
  sm: 'small'  as const,
  md: 'medium' as const,
  lg: 'large'  as const,
};

export default function Button({ variant = 'primary', size = 'md', children, ...props }: ButtonProps) {
  const { variant: muiVariant, color } = variantMap[variant];
  return (
    <MuiButton
      variant={muiVariant}
      color={color}
      size={sizeMap[size]}
      sx={{ textTransform: 'none', fontWeight: 500 }}
      {...props}
    >
      {children}
    </MuiButton>
  );
}
