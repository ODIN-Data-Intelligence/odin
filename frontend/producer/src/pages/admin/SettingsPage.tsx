import { useQuery } from '@tanstack/react-query';
import { vocabularyApi } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';

const VOCAB_TYPE_COLORS: Record<string, string> = {
  general: 'bg-gray-100 text-gray-700',
  financial: 'bg-yellow-100 text-yellow-700',
  healthcare: 'bg-green-100 text-green-700',
  geospatial: 'bg-blue-100 text-blue-700',
  custom: 'bg-purple-100 text-purple-700',
};

export default function SettingsPage() {
  const { data: vocabs = [] } = useQuery({
    queryKey: ['vocabularies'],
    queryFn: () => vocabularyApi.list(),
  });

  return (
    <div>
      <PageHeader title="Settings" description="API keys, LLM providers, vocabulary registry" />
      <div className="p-6 space-y-8">
        <section>
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-base font-semibold text-gray-800">Registered Vocabularies</h2>
            <Button size="sm" variant="secondary">+ Register Vocabulary</Button>
          </div>
          <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Prefix</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Base IRI</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {vocabs.map(v => (
                  <tr key={v.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">
                      {v.name}
                      {v.isSystem && <span className="ml-2 text-xs text-gray-400">(system)</span>}
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">{v.prefix}</td>
                    <td className="px-4 py-3">
                      <Badge label={v.vocabularyType} className={VOCAB_TYPE_COLORS[v.vocabularyType] ?? 'bg-gray-100 text-gray-700'} />
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 truncate max-w-xs">{v.baseIri}</td>
                  </tr>
                ))}
                {vocabs.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">No vocabularies registered</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        <section>
          <h2 className="text-base font-semibold text-gray-800 mb-3">API Keys</h2>
          <div className="bg-white border border-gray-200 rounded-lg p-4">
            <p className="text-sm text-gray-500">API key management will be shown here.</p>
            <Button size="sm" className="mt-3">Generate API Key</Button>
          </div>
        </section>
      </div>
    </div>
  );
}
