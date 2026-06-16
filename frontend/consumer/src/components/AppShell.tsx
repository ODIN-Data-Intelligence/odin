import { useRef, useState } from 'react';
import { Outlet, useNavigate, NavLink } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import MuiButton from '@mui/material/Button';
import Fab from '@mui/material/Fab';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import IconButton from '@mui/material/IconButton';
import ChatIcon from '@mui/icons-material/Chat';
import MenuIcon from '@mui/icons-material/Menu';
import { useIsMobile } from '@datacatalog/shared';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';
import AIChatFloatingPanel from './AIChatFloatingPanel';
const NAV_LINKS = [
  { label: 'Search', to: '/search' },
  { label: 'Bookmarks', to: '/bookmarks' },
  { label: 'Lineage', to: '/lineage' },
];

export default function AppShell() {
  const searchRef = useRef<HTMLInputElement | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const [navOpen, setNavOpen] = useState(false);
  const isMobile = useIsMobile();
  const navigate = useNavigate();

  useKeyboardShortcuts({
    onFocusSearch: () => {
      navigate('/search');
      setTimeout(() => searchRef.current?.focus(), 100);
    },
    onOpenChat: () => setChatOpen(true),
  });

  const navButtonSx = {
    opacity: 0.85,
    '&.active': { opacity: 1, fontWeight: 700, bgcolor: 'rgba(255,255,255,0.15)', borderRadius: 1.5 },
  };

  return (
    <Box sx={{ height: '100dvh', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <AppBar position="static" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar variant="dense">
          {isMobile && (
            <IconButton edge="start" color="inherit" onClick={() => setNavOpen(true)} aria-label="Open navigation" sx={{ mr: 1 }}>
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" fontWeight={700} sx={{ mr: 4, letterSpacing: '-0.5px' }}>
            Data Catalog
          </Typography>
          {!isMobile && (
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              {NAV_LINKS.map(link => (
                <MuiButton
                  key={link.to}
                  component={NavLink}
                  to={link.to}
                  color="inherit"
                  size="small"
                  sx={navButtonSx}
                >
                  {link.label}
                </MuiButton>
              ))}
            </Box>
          )}
        </Toolbar>
      </AppBar>

      {/* Mobile navigation drawer */}
      <Drawer anchor="left" open={navOpen} onClose={() => setNavOpen(false)} ModalProps={{ keepMounted: true }}>
        <Box sx={{ width: 240, pt: 1 }} role="navigation">
          <List>
            {NAV_LINKS.map(link => (
              <NavLink key={link.to} to={link.to} onClick={() => setNavOpen(false)} style={{ textDecoration: 'none', color: 'inherit' }}>
                {({ isActive }) => (
                  <ListItemButton selected={isActive}>
                    <ListItemText primary={link.label} primaryTypographyProps={{ fontWeight: isActive ? 700 : 400 }} />
                  </ListItemButton>
                )}
              </NavLink>
            ))}
          </List>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        <Outlet context={{ searchRef }} />
      </Box>

      <Fab
        color="primary"
        variant="extended"
        onClick={() => setChatOpen(true)}
        sx={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1250, gap: 1 }}
      >
        <ChatIcon />
        Ask AI
        {!isMobile && (
          <Box component="kbd" sx={{ ml: 0.5, fontSize: 11, bgcolor: 'rgba(255,255,255,0.2)', px: 0.75, py: 0.25, borderRadius: 0.75 }}>
            ⌘K
          </Box>
        )}
      </Fab>

      <AIChatFloatingPanel open={chatOpen} onClose={() => setChatOpen(false)} />
    </Box>
  );
}
