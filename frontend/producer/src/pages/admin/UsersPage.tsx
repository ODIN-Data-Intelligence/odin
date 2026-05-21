import PageHeader from '../../components/ui/PageHeader';
import Button from '../../components/ui/Button';

export default function UsersPage() {
  return (
    <div>
      <PageHeader title="Users" description="Manage users and roles" actions={<Button>Invite User</Button>} />
      <div className="p-6">
        <p className="text-sm text-gray-500">User management will be shown here.</p>
      </div>
    </div>
  );
}
