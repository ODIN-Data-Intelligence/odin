import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { producerTheme } from '@datacatalog/shared';
import { router } from './router';
import { useAuthStore } from './store/authStore';
import { keycloak } from './lib/keycloak';
import { setTokenProvider } from '@datacatalog/shared';

setTokenProvider(() => useAuthStore.getState().token);

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
});

keycloak
  .init({ onLoad: 'login-required', checkLoginIframe: false, pkceMethod: 'S256' })
  .then(authenticated => {
    if (!authenticated) return;

    useAuthStore.getState().setFromKeycloak(keycloak);

    setInterval(() => {
      keycloak.updateToken(60)
        .then(refreshed => {
          if (refreshed) useAuthStore.getState().setFromKeycloak(keycloak);
        })
        .catch(() => keycloak.logout());
    }, 30_000);

    ReactDOM.createRoot(document.getElementById('root')!).render(
      <React.StrictMode>
        <ThemeProvider theme={producerTheme}>
          <CssBaseline />
          <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
          </QueryClientProvider>
        </ThemeProvider>
      </React.StrictMode>
    );
  })
  .catch(err => {
    console.error('Keycloak init failed', err);
  });
