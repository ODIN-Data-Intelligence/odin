import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { harvestSourceApi } from '@datacatalog/shared';
import type { SourceType } from '@datacatalog/shared';

const SOURCE_TYPES: { value: SourceType; label: string }[] = [
  { value: 'dcat_http', label: 'DCAT HTTP' },
  { value: 'aws_glue', label: 'AWS Glue' },
  { value: 'snowflake', label: 'Snowflake' },
  { value: 'teradata', label: 'Teradata' },
];

interface FormValues {
  name: string;
  sourceType: SourceType;
  baseUrl?: string;
  region?: string;
  databaseName?: string;
  schemaFilter?: string;
}

interface Props {
  onClose: () => void;
}

export default function HarvestSourceForm({ onClose }: Props) {
  const { register, handleSubmit, watch, formState: { errors } } = useForm<FormValues>({ defaultValues: { sourceType: 'dcat_http' } });
  const sourceType = watch('sourceType');

  const createMut = useMutation({
    mutationFn: (values: FormValues) =>
      harvestSourceApi.create({
        ...values,
        schemaFilter: values.schemaFilter ? values.schemaFilter.split(',').map(s => s.trim()) : [],
      }),
    onSuccess: onClose,
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4">
        <div className="px-6 py-4 border-b flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">Add Harvest Source</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">&times;</button>
        </div>
        <form onSubmit={handleSubmit(data => createMut.mutate(data))} className="px-6 py-5 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name *</label>
            <input {...register('name', { required: true })} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="Production Snowflake" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Source Type *</label>
            <select {...register('sourceType')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none">
              {SOURCE_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </div>

          {(sourceType === 'dcat_http') && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Base URL *</label>
              <input {...register('baseUrl')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="https://catalog.example.com/dcat" />
            </div>
          )}

          {sourceType === 'aws_glue' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">AWS Region *</label>
              <input {...register('region')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="us-east-1" />
            </div>
          )}

          {(sourceType === 'snowflake' || sourceType === 'teradata') && (
            <>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">JDBC URL / Account *</label>
                <input {...register('baseUrl')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder={sourceType === 'snowflake' ? 'account.snowflakecomputing.com' : 'jdbc:teradata://host'} />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Database</label>
                <input {...register('databaseName')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="MY_DATABASE" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Schema Filter (comma-separated)</label>
                <input {...register('schemaFilter')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="PUBLIC,ANALYTICS" />
              </div>
            </>
          )}

          {createMut.error && <p className="text-xs text-red-600">Error: {String(createMut.error)}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancel</button>
            <button type="submit" disabled={createMut.isPending} className="px-4 py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              {createMut.isPending ? 'Saving...' : 'Add Source'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
