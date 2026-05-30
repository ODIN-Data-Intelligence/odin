import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { useAuthStore } from './store/authStore';
import { keycloak } from './lib/keycloak';
import { setTokenProvider } from '@datacatalog/shared';
import './index.css';

// Wire the shared API client to always use the current Keycloak token
setTokenProvider(() => useAuthStore.getState().token);

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1 } },
});

keycloak
  .init({ onLoad: 'login-required', checkLoginIframe: false, pkceMethod: 'S256' })
  .then(authenticated => {
    if (!authenticated) return; // keycloak-js redirects to login automatically

    useAuthStore.getState().setFromKeycloak(keycloak);

    // Proactively refresh the token 60 s before it expires
    setInterval(() => {
      keycloak.updateToken(60)
        .then(refreshed => {
          if (refreshed) useAuthStore.getState().setFromKeycloak(keycloak);
        })
        .catch(() => keycloak.logout());
    }, 30_000);

    ReactDOM.createRoot(document.getElementById('root')!).render(
      <React.StrictMode>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </React.StrictMode>
    );
  })
  .catch(err => {
    console.error('Keycloak init failed', err);
  });
