import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { useAuthStore } from './authStore';

beforeEach(() => {
  useAuthStore.setState({ token: null, tenantId: null, userId: null, email: null, displayName: null, roles: [] });
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
});
