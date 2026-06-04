import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { dataProductApi } from '@datacatalog/shared';
import { PageHeader } from '@datacatalog/shared';
import { Button } from '@datacatalog/shared';
import { Badge } from '@datacatalog/shared';
import { LIFECYCLE_COLORS, formatDate } from '../lib/utils';
import DataProductWizard from '../components/catalog/DataProductWizard';

export default function DataProductsPage() {
  const { tenant } = useParams();
  const [showWizard, setShowWizard] = useState(false);
  const [lifecycleFilter, setLifecycleFilter] = useState('');

  const { data: productPage, refetch } = useQuery({
    queryKey: ['data-products', lifecycleFilter],
    queryFn: () => dataProductApi.list(lifecycleFilter ? { lifecycleStatus: lifecycleFilter } : {}),
  });
  const products = productPage?.content ?? [];

  return (
    <div>
      <PageHeader
        title="Data Products"
        description="Browse and manage data products across your organization"
        actions={<Button onClick={() => setShowWizard(true)}>+ New Data Product</Button>}
      />

      <div className="p-6">
        <div className="flex gap-2 mb-4 flex-wrap">
          {['', 'Ideation', 'Design', 'Build', 'Deploy', 'Consume'].map(s => (
            <button
              key={s}
              onClick={() => setLifecycleFilter(s)}
              className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${lifecycleFilter === s ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-300 hover:border-blue-400'}`}
            >
              {s || 'All'}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {products.map(dp => (
            <Link
              key={dp.id}
              to={`/${tenant}/data-products/${dp.id}`}
              className="bg-white border border-gray-200 rounded-lg p-4 hover:border-blue-400 hover:shadow-sm transition-all"
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-medium text-gray-900 line-clamp-2">{dp.title}</h3>
                <Badge label={dp.lifecycleStatus} className={LIFECYCLE_COLORS[dp.lifecycleStatus]} />
              </div>
              {dp.description && (
                <p className="mt-2 text-sm text-gray-500 line-clamp-2">{dp.description}</p>
              )}
              {dp.keywords && dp.keywords.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {dp.keywords.slice(0, 3).map(kw => (
                    <span key={kw} className="px-1.5 py-0.5 bg-gray-100 text-gray-600 text-xs rounded">{kw}</span>
                  ))}
                </div>
              )}
              <p className="mt-3 text-xs text-gray-400">Updated {formatDate(dp.updatedAt)}</p>
            </Link>
          ))}
          {products.length === 0 && (
            <p className="col-span-3 text-sm text-gray-400 text-center py-12">
              No data products found. Create your first one.
            </p>
          )}
        </div>
      </div>

      {showWizard && <DataProductWizard onClose={() => { setShowWizard(false); refetch(); }} />}
    </div>
  );
}
