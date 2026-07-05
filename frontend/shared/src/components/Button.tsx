import { forwardRef } from 'react';
import MuiButton, { ButtonProps as MuiButtonProps } from '@mui/material/Button';

export interface ButtonProps extends Omit<MuiButtonProps, 'variant' | 'size'> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
}

const variantMap: Record<NonNullable<ButtonProps['variant']>, MuiButtonProps['variant']> = {
  primary:   'contained',
  secondary: 'outlined',
  danger:    'contained',
  ghost:     'text',
};

const sizeMap: Record<NonNullable<ButtonProps['size']>, MuiButtonProps['size']> = {
  sm: 'small',
  md: 'medium',
  lg: 'large',
};

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', color, ...props }, ref) => {
    const muiColor: MuiButtonProps['color'] = variant === 'danger' ? 'error' : (color ?? 'primary');
    return (
      <MuiButton
        ref={ref}
        variant={variantMap[variant]}
        size={sizeMap[size]}
        color={muiColor}
        {...props}
      />
    );
  }
);

Button.displayName = 'Button';
export default Button;
