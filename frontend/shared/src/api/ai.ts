import { get, post } from './client';

const BASE = '/api/v1';

export interface Conversation {
  id: string;
  tenantId: string;
  userId: string;
  title?: string;
  createdAt: string;
}

export interface ConversationMessage {
  id: string;
  conversationId: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  createdAt: string;
}

export interface SemanticSearchRequest {
  query: string;
  limit?: number;
  entityTypes?: string[];
}

export const aiApi = {
  listConversations: () => get<Conversation[]>(`${BASE}/conversations`),
  getConversation: (id: string) => get<Conversation>(`${BASE}/conversations/${id}`),
  createConversation: (body?: { title?: string }) => post<Conversation>(`${BASE}/conversations`, body),
  semanticSearch: (body: SemanticSearchRequest) =>
    post<Array<{ entityId: string; entityType: string; title: string; score: number }>>(`${BASE}/semantic-search`, body),

  streamMessage: (conversationId: string, content: string): EventSource => {
    const token = localStorage.getItem('access_token');
    const url = `${BASE}/conversations/${conversationId}/messages`;
    // We open an EventSource via a POST-equivalent workaround using fetch + ReadableStream externally.
    // Return a URL-encoded GET EventSource for streaming (the service handles both).
    const src = new EventSource(
      `${url}?content=${encodeURIComponent(content)}${token ? '&token=' + token : ''}`
    );
    return src;
  },

  postMessage: async (
    conversationId: string,
    content: string,
    onToken: (token: string) => void,
    onDone: () => void,
    onError: (err: unknown) => void,
    focusDatasetId?: string | null
  ): Promise<void> => {
    const token = localStorage.getItem('access_token');
    const res = await fetch(`${BASE}/conversations/${conversationId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        Accept: 'text/event-stream',
      },
      body: JSON.stringify({ content, ...(focusDatasetId ? { focusDatasetId } : {}) }),
    });

    if (!res.ok || !res.body) {
      onError(new Error(`${res.status}: ${res.statusText}`));
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        // Split on SSE event boundaries (\n\n); keep the last incomplete chunk in buffer
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';
        for (const part of parts) {
          // An SSE event may have multiple data: lines (for content containing \n)
          const dataLines = part.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5));
          if (dataLines.length === 0) continue;
          const content = dataLines.join('\n');
          // Empty data line = the token was a bare \n character
          onToken(content || '\n');
        }
      }
      onDone();
    } catch (e) {
      onError(e);
    }
  },
};
