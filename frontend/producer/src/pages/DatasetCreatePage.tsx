import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { datasetApi } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import DatasetForm from '../components/catalog/DatasetForm';

export default function DatasetCreatePage() {
  const { tenant } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const mutation = useMutation({
    mutationFn: (data: Partial<Dataset>) => datasetApi.create(data),
    onSuccess: (dataset) => {
      qc.invalidateQueries({ queryKey: ['datasets'] });
      navigate(`/${tenant}/datasets/${dataset.id}`);
    },
  });

  return (
    <div>
      <PageHeader
        title="New Dataset"
        description="Register a new dataset in the catalog"
      />
      <div className="p-6 max-w-2xl">
        <DatasetForm
          onSubmit={data => mutation.mutate(data)}
          isSubmitting={mutation.isPending}
          submitLabel="Create Dataset"
          onCancel={() => navigate(`/${tenant}/datasets`)}
        />
        {mutation.isError && (
          <p className="mt-3 text-sm text-red-600">Failed to create dataset. Please try again.</p>
        )}
      </div>
    </div>
  );
}
