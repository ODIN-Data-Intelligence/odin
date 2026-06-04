import { useQuery } from '@tanstack/react-query';
import { datasetApi, TermsOfUseDisplay } from '@datacatalog/shared';

export default function TermsOfUseTab({ datasetId }: { datasetId: string }) {
  const { data: terms, isLoading } = useQuery({
    queryKey: ['dataset-terms', datasetId],
    queryFn: () => datasetApi.getTermsOfUse(datasetId),
    enabled: !!datasetId,
    staleTime: 60_000,
  });

  if (isLoading) return <div className="p-4 text-sm text-gray-400">Loading terms of use…</div>;
  if (!terms) return <div className="p-4 text-sm text-gray-500">Terms of use could not be loaded.</div>;

  return (
    <div className="p-4">
      <TermsOfUseDisplay terms={terms} />
    </div>
  );
}
