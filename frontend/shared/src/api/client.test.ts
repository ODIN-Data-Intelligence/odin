import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { get, post, put, patch, del } from './client';

function makeFetchMock(status: number, body: unknown, ok = true) {
  return vi.fn().mockResolvedValue({
    ok,
    status,
    statusText: status === 200 ? 'OK' : 'Error',
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(String(body)),
  });
}

beforeEach(() => {
  vi.stubGlobal('localStorage', {
    getItem: vi.fn().mockReturnValue(null),
    setItem: vi.fn(),
    removeItem: vi.fn(),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('get', () => {
  it('should call fetch with GET method and correct headers', async () => {
    const mockFetch = makeFetchMock(200, { id: '1' });
    vi.stubGlobal('fetch', mockFetch);

    await get('/api/v1/test');

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/test', expect.objectContaining({
      headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
    }));
  });

  it('should include Bearer token when access_token is in localStorage', async () => {
    (localStorage.getItem as ReturnType<typeof vi.fn>).mockReturnValue('my-token');
    const mockFetch = makeFetchMock(200, {});
    vi.stubGlobal('fetch', mockFetch);

    await get('/api/v1/test');

    const headers = mockFetch.mock.calls[0][1].headers;
    expect(headers.Authorization).toBe('Bearer my-token');
  });

  it('should omit Authorization header when no token', async () => {
    const mockFetch = makeFetchMock(200, {});
    vi.stubGlobal('fetch', mockFetch);

    await get('/api/v1/test');

    const headers = mockFetch.mock.calls[0][1].headers;
    expect(headers.Authorization).toBeUndefined();
  });

  it('should return parsed JSON on 200', async () => {
    vi.stubGlobal('fetch', makeFetchMock(200, { foo: 'bar' }));
    const result = await get<{ foo: string }>('/api/v1/test');
    expect(result).toEqual({ foo: 'bar' });
  });

  it('should return undefined on 204', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: () => Promise.resolve(null),
      text: () => Promise.resolve(''),
    }));
    const result = await get('/api/v1/test');
    expect(result).toBeUndefined();
  });

  it('should throw on non-ok response', async () => {
    vi.stubGlobal('fetch', makeFetchMock(404, 'Not Found', false));
    await expect(get('/api/v1/missing')).rejects.toThrow('404');
  });

  it('should throw with status text when body read fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      text: () => Promise.reject(new Error('read error')),
    }));
    await expect(get('/api/v1/fail')).rejects.toThrow('500: Internal Server Error');
  });
});

describe('post', () => {
  it('should call fetch with POST method and serialised body', async () => {
    const mockFetch = makeFetchMock(201, { id: '1' });
    vi.stubGlobal('fetch', mockFetch);

    await post('/api/v1/items', { name: 'test' });

    expect(mockFetch).toHaveBeenCalledWith('/api/v1/items', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'test' }),
    }));
  });

  it('should post without body when body is undefined', async () => {
    const mockFetch = makeFetchMock(201, {});
    vi.stubGlobal('fetch', mockFetch);

    await post('/api/v1/trigger');

    expect(mockFetch.mock.calls[0][1].body).toBeUndefined();
  });
});

describe('put', () => {
  it('should call fetch with PUT method', async () => {
    const mockFetch = makeFetchMock(200, { id: '1' });
    vi.stubGlobal('fetch', mockFetch);

    await put('/api/v1/items/1', { name: 'updated' });

    expect(mockFetch.mock.calls[0][1].method).toBe('PUT');
    expect(mockFetch.mock.calls[0][1].body).toBe(JSON.stringify({ name: 'updated' }));
  });
});

describe('patch', () => {
  it('should call fetch with PATCH method', async () => {
    const mockFetch = makeFetchMock(200, {});
    vi.stubGlobal('fetch', mockFetch);

    await patch('/api/v1/items/1/status', { status: 'active' });

    expect(mockFetch.mock.calls[0][1].method).toBe('PATCH');
  });
});

describe('del', () => {
  it('should call fetch with DELETE method', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 204, text: () => Promise.resolve('') });
    vi.stubGlobal('fetch', mockFetch);

    await del('/api/v1/items/1');

    expect(mockFetch.mock.calls[0][1].method).toBe('DELETE');
  });
});
