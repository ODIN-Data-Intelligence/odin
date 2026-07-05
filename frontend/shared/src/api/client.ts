const BASE_HEADERS = { 'Content-Type': 'application/json' };

// Token provider — must be replaced at app startup via setTokenProvider().
// Defaults to null so uninitialised callers receive no auth header rather than
// reading a potentially stale token from localStorage.
let _getToken: () => string | null = () => null;

export function setTokenProvider(fn: () => string | null): void {
  _getToken = fn;
}

/**
 * Returns the current auth token from the registered provider. Use this for hand-rolled
 * fetch calls (e.g. SSE streams) that can't go through get/post but still need the Bearer token.
 */
export function getAuthToken(): string | null {
  return _getToken();
}

function getAuthHeader(): Record<string, string> {
  const token = _getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`${res.status}: ${text}`);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json();
}

export async function get<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { ...BASE_HEADERS, ...getAuthHeader() } });
  return handleResponse<T>(res);
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { ...BASE_HEADERS, ...getAuthHeader() },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return handleResponse<T>(res);
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { ...BASE_HEADERS, ...getAuthHeader() },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return handleResponse<T>(res);
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PATCH',
    headers: { ...BASE_HEADERS, ...getAuthHeader() },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return handleResponse<T>(res);
}

export async function del<T>(url: string): Promise<T> {
  const res = await fetch(url, {
    method: 'DELETE',
    headers: { ...BASE_HEADERS, ...getAuthHeader() },
  });
  return handleResponse<T>(res);
}
