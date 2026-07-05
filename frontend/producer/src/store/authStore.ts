import { create } from 'zustand';
import type Keycloak from 'keycloak-js';

interface AuthState {
  token: string | null;
  tenantId: string | null;
  userId: string | null;
  email: string | null;
  displayName: string | null;
  roles: string[];
  setFromKeycloak: (kc: Keycloak) => void;
  setToken: (token: string, claims: { tenantId: string; userId: string; roles: string[] }) => void;
  logout: (kc?: Keycloak) => void;
  hasRole: (role: string) => boolean;
  hasAnyRole: (roles: string[]) => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: null,
  tenantId: null,
  userId: null,
  email: null,
  displayName: null,
  roles: [],

  setFromKeycloak: (kc) => {
    const parsed   = kc.tokenParsed   as Record<string, unknown> | undefined;
    const idParsed = kc.idTokenParsed as Record<string, unknown> | undefined;
    const realmAccess = parsed?.realm_access as { roles?: string[] } | undefined;

    const firstName = (idParsed?.given_name  as string) ?? (parsed?.given_name  as string) ?? '';
    const lastName  = (idParsed?.family_name as string) ?? (parsed?.family_name as string) ?? '';
    const fullName  = (idParsed?.name as string) ?? (parsed?.name as string)
      ?? (firstName || lastName ? `${firstName} ${lastName}`.trim() : null);

    set({
      token:       kc.token ?? null,
      tenantId:    (parsed?.tenant_id as string) ?? null,
      userId:      (parsed?.sub as string) ?? null,
      email:       (idParsed?.email as string) ?? (parsed?.email as string) ?? null,
      displayName: fullName ?? (idParsed?.preferred_username as string) ?? (parsed?.preferred_username as string) ?? null,
      roles:       realmAccess?.roles ?? [],
    });
  },

  setToken: (token, { tenantId, userId, roles }) => {
    set({ token, tenantId, userId, roles });
  },

  logout: (kc) => {
    set({ token: null, tenantId: null, userId: null, email: null, displayName: null, roles: [] });
    if (kc) {
      kc.logout({ redirectUri: window.location.origin });
    }
  },

  hasRole:    (role)  => get().roles.includes(role),
  hasAnyRole: (roles) => roles.some(r => get().roles.includes(r)),
}));
