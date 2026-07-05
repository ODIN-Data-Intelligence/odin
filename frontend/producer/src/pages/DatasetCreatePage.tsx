import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import { datasetApi, PageHeader } from '@datacatalog/shared';
import type { Dataset } from '@datacatalog/shared';
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
    <Box>
      <PageHeader title="New Dataset" description="Register a new dataset in the catalog" />
      <Box sx={{ p: 3, maxWidth: 640 }}>
        <DatasetForm
          onSubmit={data => mutation.mutate(data)}
          isSubmitting={mutation.isPending}
          submitLabel="Create Dataset"
          onCancel={() => navigate(`/${tenant}/datasets`)}
        />
        {mutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>Failed to create dataset. Please try again.</Alert>
        )}
      </Box>
    </Box>
  );
}
