import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { useAuthStore } from './authStore';

const LOCAL_STORAGE_MOCK = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { store = {}; },
  };
})();

beforeEach(() => {
  LOCAL_STORAGE_MOCK.clear();
  vi.stubGlobal('localStorage', LOCAL_STORAGE_MOCK);
  useAuthStore.setState({ token: null, tenantId: null, userId: null, roles: [] });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useAuthStore initial state', () => {
  it('should start with null token and tenantId', () => {
    const { token, tenantId, userId, roles } = useAuthStore.getState();
    expect(token).toBeNull();
    expect(tenantId).toBeNull();
    expect(userId).toBeNull();
    expect(roles).toEqual([]);
  });
});

describe('useAuthStore.hydrate', () => {
  it('should restore token and tenantId from localStorage', () => {
    LOCAL_STORAGE_MOCK.setItem('access_token', 'stored-jwt');
    LOCAL_STORAGE_MOCK.setItem('tenant_id', 'stored-tenant');

    useAuthStore.getState().hydrate();

    const state = useAuthStore.getState();
    expect(state.token).toBe('stored-jwt');
    expect(state.tenantId).toBe('stored-tenant');
  });

  it('should leave token as null when localStorage is empty', () => {
    useAuthStore.getState().hydrate();
    expect(useAuthStore.getState().token).toBeNull();
  });
});

describe('useAuthStore.setToken', () => {
  it('should update in-memory state', () => {
    useAuthStore.getState().setToken('my-jwt', {
      tenantId: 'tenant-1',
      userId: 'user-1',
      roles: ['admin', 'reader'],
    });

    const state = useAuthStore.getState();
    expect(state.token).toBe('my-jwt');
    expect(state.tenantId).toBe('tenant-1');
    expect(state.userId).toBe('user-1');
    expect(state.roles).toEqual(['admin', 'reader']);
  });

  it('should persist token and tenantId to localStorage', () => {
    useAuthStore.getState().setToken('jwt-abc', { tenantId: 't-99', userId: 'u-1', roles: [] });

    expect(localStorage.getItem('access_token')).toBe('jwt-abc');
    expect(localStorage.getItem('tenant_id')).toBe('t-99');
  });
});

describe('useAuthStore.logout', () => {
  it('should clear in-memory state', () => {
    useAuthStore.setState({ token: 'tok', tenantId: 'tnt', userId: 'usr', roles: ['admin'] });

    useAuthStore.getState().logout();

    const state = useAuthStore.getState();
    expect(state.token).toBeNull();
    expect(state.tenantId).toBeNull();
    expect(state.userId).toBeNull();
    expect(state.roles).toEqual([]);
  });

  it('should remove token and tenantId from localStorage', () => {
    LOCAL_STORAGE_MOCK.setItem('access_token', 'tok');
    LOCAL_STORAGE_MOCK.setItem('tenant_id', 'tnt');

    useAuthStore.getState().logout();

    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('tenant_id')).toBeNull();
  });
});
