import { Outlet, NavLink, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import { useAuthStore } from '../../store/authStore';
import { keycloak } from '../../lib/keycloak';

const DRAWER_WIDTH = 224;

const navItems = [
  { label: 'Dashboard',     to: '' },
  { label: 'Search',        to: 'search' },
  { label: 'Data Products', to: 'data-products' },
  { label: 'Datasets',      to: 'datasets' },
  { label: 'Distributions', to: 'distributions' },
  { label: 'Lineage',       to: 'lineage' },
];

const adminItems = [
  { label: 'Harvest',        to: 'admin/harvest',                   roles: ['administrator'] },
  { label: 'Domains',        to: 'admin/domains',                   roles: ['administrator', 'data-governance'] },
  { label: 'Terms Policies', to: 'admin/governance/terms-policies', roles: ['administrator', 'data-governance'] },
  { label: 'Users',          to: 'admin/users',                     roles: ['administrator'] },
  { label: 'Settings',       to: 'admin/settings',                  roles: ['administrator'] },
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
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: DRAWER_WIDTH,
            boxSizing: 'border-box',
            bgcolor: 'grey.900',
            color: 'grey.100',
            display: 'flex',
            flexDirection: 'column',
          },
        }}
      >
        {/* Brand */}
        <Box sx={{ px: 2, py: 2.5, borderBottom: 1, borderColor: 'grey.700' }}>
          <Typography variant="subtitle1" fontWeight={600} sx={{ letterSpacing: '-0.01em', color: 'grey.100' }}>
            Data Catalog
          </Typography>
          <Typography variant="caption" sx={{ color: 'grey.500' }}>{tenant}</Typography>
        </Box>

        {/* Nav */}
        <List dense sx={{ flex: 1, px: 1, py: 1.5, overflowY: 'auto' }}>
          {navItems.map(item => (
            <NavLink key={item.to} to={`${base}/${item.to}`} end={item.to === ''} style={{ textDecoration: 'none' }}>
              {({ isActive }) => (
                <ListItemButton
                  selected={isActive}
                  sx={{
                    borderRadius: 1,
                    mb: 0.25,
                    '&.Mui-selected': { bgcolor: 'primary.main', '&:hover': { bgcolor: 'primary.dark' } },
                    '&:hover': { bgcolor: 'grey.800' },
                  }}
                >
                  <ListItemText
                    primary={item.label}
                    primaryTypographyProps={{ variant: 'body2', fontWeight: isActive ? 600 : 400, color: isActive ? 'white' : 'grey.300' }}
                  />
                </ListItemButton>
              )}
            </NavLink>
          ))}

          {visibleAdminItems.length > 0 && (
            <>
              <Divider sx={{ my: 1, borderColor: 'grey.700' }} />
              <Typography variant="caption" sx={{ px: 1.5, py: 0.5, display: 'block', color: 'grey.500', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
                Admin
              </Typography>
              {visibleAdminItems.map(item => (
                <NavLink key={item.to} to={`${base}/${item.to}`} style={{ textDecoration: 'none' }}>
                  {({ isActive }) => (
                    <ListItemButton
                      selected={isActive}
                      sx={{
                        borderRadius: 1,
                        mb: 0.25,
                        '&.Mui-selected': { bgcolor: 'primary.main', '&:hover': { bgcolor: 'primary.dark' } },
                        '&:hover': { bgcolor: 'grey.800' },
                      }}
                    >
                      <ListItemText
                        primary={item.label}
                        primaryTypographyProps={{ variant: 'body2', fontWeight: isActive ? 600 : 400, color: isActive ? 'white' : 'grey.300' }}
                      />
                    </ListItemButton>
                  )}
                </NavLink>
              ))}
            </>
          )}
        </List>

        {/* User footer */}
        <Box sx={{ px: 2, py: 1.5, borderTop: 1, borderColor: 'grey.700' }}>
          <Typography variant="body2" fontWeight={500} noWrap sx={{ color: 'grey.200' }}>
            {displayName ?? email ?? 'User'}
          </Typography>
          {email && displayName && (
            <Typography variant="caption" noWrap sx={{ color: 'grey.500', display: 'block' }}>{email}</Typography>
          )}
          <Button
            size="small"
            onClick={() => keycloak.logout({ redirectUri: window.location.origin })}
            sx={{ mt: 1, color: 'grey.400', textTransform: 'none', fontSize: 12, p: 0, '&:hover': { color: 'grey.200' } }}
          >
            Sign out →
          </Button>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flex: 1, overflowY: 'auto' }}>
        <Outlet />
      </Box>
    </Box>
  );
}
