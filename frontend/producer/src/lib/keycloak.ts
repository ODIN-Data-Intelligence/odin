/// <reference types="vite/client" />
import Keycloak from 'keycloak-js';

// Priority:
//   1. window.__ODIN_CONFIG__.keycloakUrl  — runtime injection via nginx envsubst
//   2. import.meta.env.VITE_KEYCLOAK_URL   — Vite dev server build-time variable
//   3. 'http://localhost:8180'             — local fallback
function resolveKeycloakUrl(): string {
  const runtimeUrl = ((window as unknown) as Record<string, unknown>).__ODIN_CONFIG__ as
    | { keycloakUrl?: string }
    | undefined;
  if (runtimeUrl?.keycloakUrl && !runtimeUrl.keycloakUrl.includes('${')) {
    return runtimeUrl.keycloakUrl;
  }
  return (import.meta.env.VITE_KEYCLOAK_URL as string | undefined) ?? 'http://localhost:8180';
}

export const keycloak = new Keycloak({
  url:      resolveKeycloakUrl(),
  realm:    'datacatalog',
  clientId: 'catalog-frontend',
});
