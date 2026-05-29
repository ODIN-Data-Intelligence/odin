import { Outlet, NavLink, useParams } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { useAuthStore } from '../../store/authStore';
import { keycloak } from '../../lib/keycloak';

const navItems = [
  { label: 'Dashboard',     to: '' },
  { label: 'Search',        to: 'search' },
  { label: 'Data Products', to: 'data-products' },
  { label: 'Datasets',      to: 'datasets' },
  { label: 'Distributions', to: 'distributions' },
  { label: 'Lineage',       to: 'lineage' },
];

// Each admin item optionally restricted to specific roles.
// If `roles` is omitted the item is visible to all authenticated users.
const adminItems = [
  { label: 'Harvest',  to: 'admin/harvest',  roles: ['administrator'] },
  { label: 'Domains',  to: 'admin/domains',  roles: ['administrator', 'data-governance'] },
  { label: 'Users',    to: 'admin/users',    roles: ['administrator'] },
  { label: 'Settings', to: 'admin/settings', roles: ['administrator'] },
];

export default function AppLayout() {
  const { tenant } = useParams();
  const base = `/${tenant}`;
  const displayName = useAuthStore(s => s.displayName);
  const email       = useAuthStore(s => s.email);
  const hasAnyRole  = useAuthStore(s => s.hasAnyRole);

  const visibleAdminItems = adminItems.filter(item =>
    !item.roles || hasAnyRole(item.roles)
  );

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

          {visibleAdminItems.length > 0 && (
            <>
              <p className="px-3 pt-4 pb-1 text-xs font-semibold text-gray-500 uppercase tracking-wider">Admin</p>
              {visibleAdminItems.map(item => (
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
            </>
          )}
        </nav>

        {/* User identity + logout */}
        <div className="px-4 py-3 border-t border-gray-700">
          <p className="text-xs font-medium text-gray-200 truncate">{displayName ?? email ?? 'User'}</p>
          {email && displayName && (
            <p className="text-xs text-gray-400 truncate">{email}</p>
          )}
          <button
            onClick={() => keycloak.logout({ redirectUri: window.location.origin })}
            className="mt-2 w-full text-left text-xs text-gray-400 hover:text-gray-200 transition-colors"
          >
            Sign out →
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
}
