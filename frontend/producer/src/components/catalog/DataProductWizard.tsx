import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { dataProductApi } from '@datacatalog/shared';

const STEPS = ['Details', 'Ports', 'Review'] as const;
type Step = typeof STEPS[number];

interface WizardProps {
  onClose: () => void;
}

interface FormValues {
  title: string;
  description: string;
  lifecycleStatus: string;
  purpose: string;
  informationSensitivity: string;
  keywords: string;
}

export default function DataProductWizard({ onClose }: WizardProps) {
  const [step, setStep] = useState<Step>('Details');
  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>();

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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4">
        <div className="px-6 py-4 border-b flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">New Data Product</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">&times;</button>
        </div>

        <div className="px-6 pt-4">
          <div className="flex items-center gap-2 mb-6">
            {STEPS.map((s, i) => (
              <div key={s} className="flex items-center gap-2">
                <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-semibold ${i <= stepIdx ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-500'}`}>
                  {i + 1}
                </div>
                <span className={`text-sm ${i === stepIdx ? 'font-medium text-gray-900' : 'text-gray-500'}`}>{s}</span>
                {i < STEPS.length - 1 && <div className="w-8 h-px bg-gray-200" />}
              </div>
            ))}
          </div>

          {step === 'Details' && (
            <form className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Title *</label>
                <input {...register('title', { required: true })} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="Customer 360" />
                {errors.title && <p className="text-xs text-red-600 mt-1">Title is required</p>}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea {...register('description')} rows={3} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="Describe this data product..." />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Lifecycle</label>
                  <select {...register('lifecycleStatus')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none">
                    {['Ideation', 'Design', 'Build', 'Deploy', 'Consume'].map(s => <option key={s}>{s}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Sensitivity</label>
                  <select {...register('informationSensitivity')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none">
                    {['Public', 'Internal', 'Confidential', 'Restricted'].map(s => <option key={s}>{s}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Keywords (comma-separated)</label>
                <input {...register('keywords')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="customer, crm, analytics" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Purpose</label>
                <input {...register('purpose')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none" placeholder="Why does this data product exist?" />
              </div>
            </form>
          )}

          {step === 'Ports' && (
            <div className="py-4 text-sm text-gray-500">
              Ports (input/output) can be configured after creation. Click Review to proceed.
            </div>
          )}

          {step === 'Review' && (
            <div className="py-4 text-sm text-gray-600">
              Review your inputs and click Create to finalize.
              {createMut.error && (
                <p className="text-red-600 mt-2">Error: {String(createMut.error)}</p>
              )}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t flex justify-between">
          <button onClick={() => stepIdx > 0 ? setStep(STEPS[stepIdx - 1]) : onClose()} className="text-sm text-gray-600 hover:text-gray-800">
            {stepIdx === 0 ? 'Cancel' : 'Back'}
          </button>
          {stepIdx < STEPS.length - 1 ? (
            <button onClick={() => setStep(STEPS[stepIdx + 1])} className="px-4 py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700">
              Next
            </button>
          ) : (
            <button
              onClick={handleSubmit(data => createMut.mutate(data))}
              disabled={createMut.isPending}
              className="px-4 py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {createMut.isPending ? 'Creating...' : 'Create'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
