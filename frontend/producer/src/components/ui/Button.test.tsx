import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Button from './Button';

describe('Button', () => {
  describe('rendering', () => {
    it('should render children', () => {
      render(<Button>Click me</Button>);
      expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
    });

    it('should render as a <button> element', () => {
      render(<Button>Save</Button>);
      expect(screen.getByRole('button')).toBeInTheDocument();
    });
  });

  describe('variant classes', () => {
    it('primary (default) should have blue background', () => {
      render(<Button>Primary</Button>);
      expect(screen.getByRole('button')).toHaveClass('bg-blue-600', 'text-white');
    });

    it('secondary should have white background and gray border', () => {
      render(<Button variant="secondary">Secondary</Button>);
      expect(screen.getByRole('button')).toHaveClass('bg-white', 'text-gray-700', 'border-gray-300');
    });

    it('danger should have red background', () => {
      render(<Button variant="danger">Delete</Button>);
      expect(screen.getByRole('button')).toHaveClass('bg-red-600', 'text-white');
    });

    it('ghost should have transparent border and gray text', () => {
      render(<Button variant="ghost">Cancel</Button>);
      expect(screen.getByRole('button')).toHaveClass('text-gray-600', 'hover:bg-gray-100');
    });
  });

  describe('size classes', () => {
    it('sm should use small padding and text', () => {
      render(<Button size="sm">Small</Button>);
      expect(screen.getByRole('button')).toHaveClass('px-2.5', 'py-1.5', 'text-xs');
    });

    it('md (default) should use medium padding and text', () => {
      render(<Button>Medium</Button>);
      expect(screen.getByRole('button')).toHaveClass('px-4', 'py-2', 'text-sm');
    });

    it('lg should use large padding and text', () => {
      render(<Button size="lg">Large</Button>);
      expect(screen.getByRole('button')).toHaveClass('px-6', 'py-3', 'text-base');
    });
  });

  describe('disabled state', () => {
    it('should be disabled when disabled prop is true', () => {
      render(<Button disabled>Disabled</Button>);
      expect(screen.getByRole('button')).toBeDisabled();
    });

    it('should apply disabled styling classes', () => {
      render(<Button disabled>Disabled</Button>);
      expect(screen.getByRole('button')).toHaveClass('disabled:opacity-50', 'disabled:cursor-not-allowed');
    });
  });

  describe('interactions', () => {
    it('should call onClick handler when clicked', async () => {
      const onClick = vi.fn();
      render(<Button onClick={onClick}>Click</Button>);
      await userEvent.click(screen.getByRole('button'));
      expect(onClick).toHaveBeenCalledOnce();
    });

    it('should not call onClick when disabled', async () => {
      const onClick = vi.fn();
      render(<Button onClick={onClick} disabled>Click</Button>);
      await userEvent.click(screen.getByRole('button'));
      expect(onClick).not.toHaveBeenCalled();
    });
  });

  describe('custom className', () => {
    it('should merge custom className with base classes', () => {
      render(<Button className="my-custom-class">Custom</Button>);
      expect(screen.getByRole('button')).toHaveClass('my-custom-class');
    });
  });
});
