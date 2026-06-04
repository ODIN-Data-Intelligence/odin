import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';

export default function DomainsPage() {
  return (
    <div>
      <PageHeader title="Domains" description="Manage organizational domains" actions={<Button>+ Add Domain</Button>} />
      <div className="p-6">
        <p className="text-sm text-gray-500">Domain hierarchy will be shown here.</p>
      </div>
    </div>
  );
}
