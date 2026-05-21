import { Outlet, NavLink, useParams } from 'react-router-dom';
import { cn } from '../../lib/utils';

const navItems = [
  { label: 'Dashboard', to: '' },
  { label: 'Data Products', to: 'data-products' },
  { label: 'Datasets', to: 'datasets' },
  { label: 'Lineage', to: 'lineage' },
];

const adminItems = [
  { label: 'Harvest', to: 'admin/harvest' },
  { label: 'Domains', to: 'admin/domains' },
  { label: 'Users', to: 'admin/users' },
  { label: 'Settings', to: 'admin/settings' },
];

export default function AppLayout() {
  const { tenant } = useParams();
  const base = `/${tenant}`;

  return (
    <div className="flex h-screen overflow-hidden">
      <aside className="w-56 flex-shrink-0 bg-gray-900 text-gray-100 flex flex-col">
        <div className="px-4 py-5 border-b border-gray-700">
          <span className="text-lg font-semibold tracking-tight">Data Catalog</span>
          <p className="text-xs text-gray-400 mt-0.5">{tenant}</p>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navItems.map(item => (
            <NavLink
              key={item.to}
              to={`${base}/${item.to}`}
              end={item.to === ''}
              className={({ isActive }) =>
                cn('block px-3 py-2 rounded text-sm font-medium transition-colors',
                  isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-700')
              }
            >
              {item.label}
            </NavLink>
          ))}
          <p className="px-3 pt-4 pb-1 text-xs font-semibold text-gray-500 uppercase tracking-wider">Admin</p>
          {adminItems.map(item => (
            <NavLink
              key={item.to}
              to={`${base}/${item.to}`}
              className={({ isActive }) =>
                cn('block px-3 py-2 rounded text-sm font-medium transition-colors',
                  isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-700')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
