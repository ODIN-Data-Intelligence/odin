import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { datasetApi, TermsOfUseDisplay } from '@datacatalog/shared';

export default function TermsOfUseTab({ datasetId }: { datasetId: string }) {
  const { data: terms, isLoading } = useQuery({
    queryKey: ['dataset-terms', datasetId],
    queryFn: () => datasetApi.getTermsOfUse(datasetId),
    enabled: !!datasetId,
    staleTime: 60_000,
  });

  if (isLoading) {
    return <Typography variant="body2" color="text.disabled" sx={{ p: 2 }}>Loading terms of use…</Typography>;
  }
  if (!terms) {
    return <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>Terms of use could not be loaded.</Typography>;
  }

  return (
    <Box>
      <TermsOfUseDisplay terms={terms} />
    </Box>
  );
}
