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
    const parsed = kc.tokenParsed as Record<string, unknown> | undefined;
    const realmAccess = parsed?.realm_access as { roles?: string[] } | undefined;
    set({
      token:       kc.token ?? null,
      tenantId:    (parsed?.tenant_id as string) ?? null,
      userId:      (parsed?.sub as string) ?? null,
      email:       (parsed?.email as string) ?? null,
      displayName: (parsed?.name as string) ?? (parsed?.preferred_username as string) ?? null,
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
