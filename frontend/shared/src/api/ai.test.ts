import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { parseSseBuffer, aiApi } from './ai';

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null), setItem: vi.fn(), removeItem: vi.fn() });
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('parseSseBuffer', () => {
  it('should extract token from a single complete SSE event', () => {
    const { tokens, remaining } = parseSseBuffer('data:Hello\n\n');
    expect(tokens).toEqual(['Hello']);
    expect(remaining).toBe('');
  });

  it('should keep incomplete event in remaining', () => {
    const { tokens, remaining } = parseSseBuffer('data:Hello\n\ndata:World');
    expect(tokens).toEqual(['Hello']);
    expect(remaining).toBe('data:World');
  });

  it('should parse multiple complete events', () => {
    const { tokens } = parseSseBuffer('data:foo\n\ndata:bar\n\ndata:baz\n\n');
    expect(tokens).toEqual(['foo', 'bar', 'baz']);
  });

  it('should join multi-line data fields with newline', () => {
    const { tokens } = parseSseBuffer('data:line1\ndata:line2\n\n');
    expect(tokens).toEqual(['line1\nline2']);
  });

  it('should convert empty data line to newline character', () => {
    const { tokens } = parseSseBuffer('data:\n\n');
    expect(tokens).toEqual(['\n']);
  });

  it('should skip events with no data: lines', () => {
    const { tokens } = parseSseBuffer('comment: ignored\n\ndata:real\n\n');
    expect(tokens).toEqual(['real']);
  });

  it('should return empty tokens and empty remaining for empty buffer', () => {
    const { tokens, remaining } = parseSseBuffer('');
    expect(tokens).toEqual([]);
    expect(remaining).toBe('');
  });
});

describe('aiApi.postMessage', () => {
  function makeStreamResponse(chunks: Uint8Array[]) {
    let index = 0;
    const reader = {
      read: vi.fn().mockImplementation(() => {
        if (index < chunks.length) return Promise.resolve({ done: false, value: chunks[index++] });
        return Promise.resolve({ done: true, value: undefined });
      }),
    };
    return {
      ok: true,
      status: 200,
      body: { getReader: () => reader },
    };
  }

  it('should call onToken for each SSE token and onDone when stream ends', async () => {
    const enc = new TextEncoder();
    const mockFetch = vi.fn().mockResolvedValue(
      makeStreamResponse([enc.encode('data:Hello\n\ndata: World\n\n')])
    );
    vi.stubGlobal('fetch', mockFetch);

    const onToken = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    await aiApi.postMessage('conv-1', 'test message', onToken, onDone, onError);

    expect(onToken).toHaveBeenCalledTimes(2);
    expect(onToken).toHaveBeenNthCalledWith(1, 'Hello');
    expect(onToken).toHaveBeenNthCalledWith(2, ' World');
    expect(onDone).toHaveBeenCalledOnce();
    expect(onError).not.toHaveBeenCalled();
  });

  it('should call onError when response is not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401, statusText: 'Unauthorized', body: null }));

    const onError = vi.fn();
    await aiApi.postMessage('conv-1', 'msg', vi.fn(), vi.fn(), onError);

    expect(onError).toHaveBeenCalledWith(expect.any(Error));
  });

  it('should include Bearer token in Authorization header when token present', async () => {
    (localStorage.getItem as ReturnType<typeof vi.fn>).mockReturnValue('test-token');
    const enc = new TextEncoder();
    const mockFetch = vi.fn().mockResolvedValue(makeStreamResponse([enc.encode('')]));
    vi.stubGlobal('fetch', mockFetch);

    await aiApi.postMessage('conv-1', 'msg', vi.fn(), vi.fn(), vi.fn());

    const headers = mockFetch.mock.calls[0][1].headers;
    expect(headers.Authorization).toBe('Bearer test-token');
  });

  it('should include focusDatasetId in request body when provided', async () => {
    const enc = new TextEncoder();
    const mockFetch = vi.fn().mockResolvedValue(makeStreamResponse([enc.encode('')]));
    vi.stubGlobal('fetch', mockFetch);

    await aiApi.postMessage('conv-1', 'msg', vi.fn(), vi.fn(), vi.fn(), 'dataset-42');

    const body = JSON.parse(mockFetch.mock.calls[0][1].body);
    expect(body.focusDatasetId).toBe('dataset-42');
  });
});

describe('aiApi.listConversations', () => {
  it('should GET /api/v1/conversations', async () => {
    const mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve([]) });
    vi.stubGlobal('fetch', mockFetch);

    await aiApi.listConversations();

    expect(mockFetch.mock.calls[0][0]).toBe('/api/v1/conversations');
  });
});
