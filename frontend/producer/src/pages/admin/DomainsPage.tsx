import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import { PageHeader } from '@datacatalog/shared';

export default function DomainsPage() {
  return (
    <Box>
      <PageHeader title="Domains" description="Manage organizational domains" actions={
        <Button variant="contained" size="small" sx={{ textTransform: 'none' }}>+ Add Domain</Button>
      } />
      <Box sx={{ p: 3 }}>
        <Typography variant="body2" color="text.secondary">Domain hierarchy will be shown here.</Typography>
      </Box>
    </Box>
  );
}
