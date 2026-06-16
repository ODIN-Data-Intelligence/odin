import { useState } from 'react';
import { Outlet, NavLink, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import { useIsMobile } from '@datacatalog/shared';
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
  const isMobile = useIsMobile();
  const [mobileOpen, setMobileOpen] = useState(false);

  const displayName = useAuthStore(s => s.displayName);
  const email       = useAuthStore(s => s.email);
  const hasAnyRole  = useAuthStore(s => s.hasAnyRole);

  const visibleAdminItems = adminItems.filter(item =>
    !item.roles || hasAnyRole(item.roles)
  );

  const closeMobile = () => setMobileOpen(false);

  const navLink = (item: { label: string; to: string }, end = false) => (
    <NavLink
      key={item.to}
      to={`${base}/${item.to}`}
      end={end}
      onClick={closeMobile}
      style={{ textDecoration: 'none' }}
    >
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
  );

  const drawerContent = (
    <>
      {/* Brand */}
      <Box sx={{ px: 2, py: 2.5, borderBottom: 1, borderColor: 'grey.700' }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ letterSpacing: '-0.01em', color: 'grey.100' }}>
          Data Catalog
        </Typography>
        <Typography variant="caption" sx={{ color: 'grey.500' }}>{tenant}</Typography>
      </Box>

      {/* Nav */}
      <List dense sx={{ flex: 1, px: 1, py: 1.5, overflowY: 'auto' }}>
        {navItems.map(item => navLink(item, item.to === ''))}

        {visibleAdminItems.length > 0 && (
          <>
            <Divider sx={{ my: 1, borderColor: 'grey.700' }} />
            <Typography variant="caption" sx={{ px: 1.5, py: 0.5, display: 'block', color: 'grey.500', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
              Admin
            </Typography>
            {visibleAdminItems.map(item => navLink(item))}
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
    </>
  );

  const drawerPaperSx = {
    width: DRAWER_WIDTH,
    boxSizing: 'border-box' as const,
    bgcolor: 'grey.900',
    color: 'grey.100',
    display: 'flex',
    flexDirection: 'column' as const,
  };

  return (
    <Box sx={{ display: 'flex', height: '100dvh', overflow: 'hidden' }}>
      {/* Mobile top bar with hamburger — hidden on desktop */}
      {isMobile && (
        <AppBar
          position="fixed"
          elevation={0}
          sx={{ bgcolor: 'grey.900', borderBottom: 1, borderColor: 'grey.700' }}
        >
          <Toolbar variant="dense">
            <IconButton edge="start" color="inherit" onClick={() => setMobileOpen(true)} aria-label="Open navigation" sx={{ mr: 1.5 }}>
              <MenuIcon />
            </IconButton>
            <Typography variant="subtitle1" fontWeight={600} sx={{ color: 'grey.100' }}>
              Data Catalog
            </Typography>
          </Toolbar>
        </AppBar>
      )}

      {isMobile ? (
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={closeMobile}
          ModalProps={{ keepMounted: true }}
          sx={{ '& .MuiDrawer-paper': drawerPaperSx }}
        >
          {drawerContent}
        </Drawer>
      ) : (
        <Drawer
          variant="permanent"
          sx={{ width: DRAWER_WIDTH, flexShrink: 0, '& .MuiDrawer-paper': drawerPaperSx }}
        >
          {drawerContent}
        </Drawer>
      )}

      <Box
        component="main"
        sx={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}
      >
        {/* Spacer to clear the fixed mobile AppBar */}
        {isMobile && <Toolbar variant="dense" />}
        <Outlet />
      </Box>
    </Box>
  );
}
