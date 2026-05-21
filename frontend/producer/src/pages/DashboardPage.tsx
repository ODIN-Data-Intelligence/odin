import { useQuery } from '@tanstack/react-query';
import { harvestRunApi } from '@datacatalog/shared';
import PageHeader from '../components/ui/PageHeader';
import Badge from '../components/ui/Badge';
import { formatDateTime, RUN_STATUS_COLORS } from '../lib/utils';

export default function DashboardPage() {
  const { data: runs } = useQuery({
    queryKey: ['harvest-runs-recent'],
    queryFn: () => harvestRunApi.get('recent'),
    enabled: false, // placeholder — replace with actual recent runs endpoint
  });

  return (
    <div>
      <PageHeader title="Dashboard" description="Overview of your data catalog" />
      <div className="p-6 grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Datasets" value="—" />
        <StatCard label="Data Products" value="—" />
        <StatCard label="Harvest Runs Today" value="—" />
      </div>
      <div className="px-6">
        <h2 className="text-base font-semibold text-gray-800 mb-3">Recent Activity</h2>
        <p className="text-sm text-gray-500">No recent activity.</p>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-3xl font-semibold text-gray-900 mt-1">{value}</p>
    </div>
  );
}
