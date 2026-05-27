import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DatasetForm from './DatasetForm';

describe('DatasetForm', () => {
  describe('rendering', () => {
    it('should render all core fields', () => {
      render(<DatasetForm onSubmit={vi.fn()} />);
      expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/keywords/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/accrual periodicity/i)).toBeInTheDocument();
    });

    it('should mark title as required', () => {
      render(<DatasetForm onSubmit={vi.fn()} />);
      expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
      expect(screen.getByText('*')).toBeInTheDocument();
    });

    it('should render submit button with default label "Save"', () => {
      render(<DatasetForm onSubmit={vi.fn()} />);
      expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
    });

    it('should render custom submitLabel', () => {
      render(<DatasetForm onSubmit={vi.fn()} submitLabel="Create Dataset" />);
      expect(screen.getByRole('button', { name: 'Create Dataset' })).toBeInTheDocument();
    });

    it('should render Cancel button when onCancel provided', () => {
      render(<DatasetForm onSubmit={vi.fn()} onCancel={vi.fn()} />);
      expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument();
    });

    it('should not render Cancel button when onCancel not provided', () => {
      render(<DatasetForm onSubmit={vi.fn()} />);
      expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
    });

    it('should show "Saving…" when isSubmitting is true', () => {
      render(<DatasetForm onSubmit={vi.fn()} isSubmitting />);
      expect(screen.getByRole('button', { name: 'Saving…' })).toBeInTheDocument();
    });

    it('should disable submit button while isSubmitting', () => {
      render(<DatasetForm onSubmit={vi.fn()} isSubmitting />);
      expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    });
  });

  describe('pre-filling from defaultValues', () => {
    it('should pre-fill title from defaultValues', () => {
      render(<DatasetForm onSubmit={vi.fn()} defaultValues={{ title: 'Trade Blotter' }} />);
      expect(screen.getByLabelText(/title/i)).toHaveValue('Trade Blotter');
    });

    it('should join keywords array into comma-separated string', () => {
      render(
        <DatasetForm onSubmit={vi.fn()} defaultValues={{ keywords: ['trading', 'blotter', 'risk'] }} />,
      );
      expect(screen.getByLabelText(/keywords/i)).toHaveValue('trading, blotter, risk');
    });

    it('should pre-fill description', () => {
      render(<DatasetForm onSubmit={vi.fn()} defaultValues={{ description: 'A test description' }} />);
      expect(screen.getByLabelText(/description/i)).toHaveValue('A test description');
    });
  });

  describe('validation', () => {
    it('should show error when submitting without a title', async () => {
      render(<DatasetForm onSubmit={vi.fn()} submitLabel="Save" />);
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));
      await waitFor(() => {
        expect(screen.getByText('Title is required')).toBeInTheDocument();
      });
    });

    it('should not call onSubmit when title is missing', async () => {
      const onSubmit = vi.fn();
      render(<DatasetForm onSubmit={onSubmit} submitLabel="Save" />);
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));
      await waitFor(() => screen.getByText('Title is required'));
      expect(onSubmit).not.toHaveBeenCalled();
    });
  });

  describe('submission', () => {
    it('should call onSubmit with correct payload on valid submit', async () => {
      const onSubmit = vi.fn();
      render(<DatasetForm onSubmit={onSubmit} submitLabel="Save" />);

      await userEvent.type(screen.getByLabelText(/title/i), 'My Dataset');
      await userEvent.type(screen.getByLabelText(/description/i), 'A description');
      await userEvent.type(screen.getByLabelText(/keywords/i), 'alpha, beta');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledOnce();
        const payload = onSubmit.mock.calls[0][0];
        expect(payload.title).toBe('My Dataset');
        expect(payload.description).toBe('A description');
        expect(payload.keywords).toEqual(['alpha', 'beta']);
      });
    });

    it('should convert comma-separated language string to array', async () => {
      const onSubmit = vi.fn();
      render(
        <DatasetForm onSubmit={onSubmit} submitLabel="Save" defaultValues={{ title: 'T' }} />,
      );
      const langInput = screen.getByLabelText(/language/i);
      await userEvent.clear(langInput);
      await userEvent.type(langInput, 'en, fr, de');
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() => {
        expect(onSubmit.mock.calls[0][0].language).toEqual(['en', 'fr', 'de']);
      });
    });

    it('should omit empty optional fields (description stays undefined when blank)', async () => {
      const onSubmit = vi.fn();
      render(<DatasetForm onSubmit={onSubmit} submitLabel="Save" defaultValues={{ title: 'T' }} />);
      await userEvent.click(screen.getByRole('button', { name: 'Save' }));

      await waitFor(() => {
        const payload = onSubmit.mock.calls[0][0];
        expect(payload.description).toBeUndefined();
        expect(payload.accrualPeriodicity).toBeUndefined();
        expect(payload.version).toBeUndefined();
      });
    });
  });

  describe('interactions', () => {
    it('should call onCancel when Cancel button is clicked', async () => {
      const onCancel = vi.fn();
      render(<DatasetForm onSubmit={vi.fn()} onCancel={onCancel} />);
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(onCancel).toHaveBeenCalledOnce();
    });
  });
});
