import { useForm } from 'react-hook-form';
import type { Dataset } from '@datacatalog/shared';
import { cn } from '../../lib/utils';
import { Button } from '@datacatalog/shared';

interface FormValues {
  title: string;
  description: string;
  keywordsRaw: string;
  accrualPeriodicity: string;
  version: string;
  license: string;
  languageRaw: string;
  themesRaw: string;
}

interface Props {
  defaultValues?: Partial<Dataset>;
  onSubmit: (data: Partial<Dataset>) => void;
  isSubmitting?: boolean;
  submitLabel?: string;
  onCancel?: () => void;
}

const PERIODICITY_OPTIONS = [
  '', 'continuous', 'daily', 'weekly', 'monthly', 'quarterly', 'annual', 'irregular',
] as const;

function splitTags(raw: string): string[] {
  return raw.split(',').map(s => s.trim()).filter(Boolean);
}

export default function DatasetForm({
  defaultValues,
  onSubmit,
  isSubmitting,
  submitLabel = 'Save',
  onCancel,
}: Props) {
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    defaultValues: {
      title: defaultValues?.title ?? '',
      description: defaultValues?.description ?? '',
      keywordsRaw: (defaultValues?.keywords ?? []).join(', '),
      accrualPeriodicity: defaultValues?.accrualPeriodicity ?? '',
      version: defaultValues?.version ?? '',
      license: defaultValues?.license ?? '',
      languageRaw: (defaultValues?.language ?? []).join(', '),
      themesRaw: (defaultValues?.themes ?? []).join(', '),
    },
  });

  function convert(values: FormValues): Partial<Dataset> {
    return {
      title: values.title,
      description: values.description || undefined,
      keywords: splitTags(values.keywordsRaw),
      accrualPeriodicity: values.accrualPeriodicity || undefined,
      version: values.version || undefined,
      license: values.license || undefined,
      language: splitTags(values.languageRaw),
      themes: splitTags(values.themesRaw),
    };
  }

  return (
    <form onSubmit={handleSubmit(values => onSubmit(convert(values)))} className="space-y-5">
      <Field id="title" label="Title" required error={errors.title?.message}>
        <input
          id="title"
          {...register('title', { required: 'Title is required' })}
          className={inputCls(!!errors.title)}
          placeholder="e.g. Trade Blotter"
        />
      </Field>

      <Field id="description" label="Description">
        <textarea
          id="description"
          {...register('description')}
          rows={3}
          className={inputCls(false)}
          placeholder="Describe this dataset…"
        />
      </Field>

      <Field id="keywordsRaw" label="Keywords" hint="comma-separated">
        <input
          id="keywordsRaw"
          {...register('keywordsRaw')}
          className={inputCls(false)}
          placeholder="trading, blotter, positions"
        />
      </Field>

      <Field id="accrualPeriodicity" label="Accrual Periodicity">
        <select id="accrualPeriodicity" {...register('accrualPeriodicity')} className={inputCls(false)}>
          {PERIODICITY_OPTIONS.map(opt => (
            <option key={opt} value={opt}>{opt || '— none —'}</option>
          ))}
        </select>
      </Field>

      <div className="grid grid-cols-2 gap-4">
        <Field id="version" label="Version">
          <input id="version" {...register('version')} className={inputCls(false)} placeholder="1.0" />
        </Field>
        <Field id="languageRaw" label="Language" hint="comma-separated, e.g. en, fr">
          <input id="languageRaw" {...register('languageRaw')} className={inputCls(false)} placeholder="en" />
        </Field>
      </div>

      <Field id="license" label="License">
        <input
          id="license"
          {...register('license')}
          className={inputCls(false)}
          placeholder="https://creativecommons.org/licenses/by/4.0/"
        />
      </Field>

      <Field id="themesRaw" label="Themes" hint="comma-separated IRIs or labels">
        <input id="themesRaw" {...register('themesRaw')} className={inputCls(false)} placeholder="Finance, Risk" />
      </Field>

      <div className="flex items-center gap-3 pt-2">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}

function inputCls(invalid: boolean) {
  return cn(
    'w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500',
    invalid ? 'border-red-400' : 'border-gray-300',
  );
}

function Field({
  id,
  label,
  required,
  hint,
  error,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
        {hint && <span className="text-gray-400 text-xs font-normal ml-1.5">({hint})</span>}
      </label>
      {children}
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  );
}
