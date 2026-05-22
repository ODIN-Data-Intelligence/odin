import { create } from 'zustand';

interface AuthState {
  token: string | null;
  tenantId: string | null;
  userId: string | null;
  roles: string[];
  hydrate: () => void;
  setToken: (token: string, claims: { tenantId: string; userId: string; roles: string[] }) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  tenantId: null,
  userId: null,
  roles: [],
  // Call once at app startup (main.tsx) to restore persisted session
  hydrate: () => set({
    token: localStorage.getItem('access_token'),
    tenantId: localStorage.getItem('tenant_id'),
  }),
  setToken: (token, { tenantId, userId, roles }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('tenant_id', tenantId);
    set({ token, tenantId, userId, roles });
  },
  logout: () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('tenant_id');
    set({ token: null, tenantId: null, userId: null, roles: [] });
  },
}));
