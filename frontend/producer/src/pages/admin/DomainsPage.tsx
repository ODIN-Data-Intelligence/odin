import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';

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
