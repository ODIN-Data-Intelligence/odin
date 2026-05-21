import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import { aiApi } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  streaming?: boolean;
}

const mdComponents: React.ComponentProps<typeof ReactMarkdown>['components'] = {
  p:          ({ children }) => <p className="mb-1 last:mb-0 leading-snug">{children}</p>,
  strong:     ({ children }) => <strong className="font-semibold">{children}</strong>,
  em:         ({ children }) => <em className="italic">{children}</em>,
  ul:         ({ children }) => <ul className="list-disc list-inside mb-1 space-y-0.5">{children}</ul>,
  ol:         ({ children }) => <ol className="list-decimal list-inside mb-1 space-y-0.5">{children}</ol>,
  li:         ({ children }) => <li className="leading-snug">{children}</li>,
  h1:         ({ children }) => <h1 className="text-sm font-bold mt-2 mb-1">{children}</h1>,
  h2:         ({ children }) => <h2 className="text-sm font-bold mt-2 mb-1">{children}</h2>,
  h3:         ({ children }) => <h3 className="text-sm font-semibold mt-1 mb-0.5">{children}</h3>,
  blockquote: ({ children }) => <blockquote className="border-l-2 border-gray-300 pl-2 italic text-gray-500 my-1">{children}</blockquote>,
  pre:        ({ children }) => <pre className="bg-gray-200/70 rounded p-2 text-xs font-mono overflow-x-auto my-1 whitespace-pre-wrap">{children}</pre>,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  code:       ({ className, children, ...props }: any) =>
    className?.startsWith('language-')
      ? <code className={className} {...props}>{children}</code>
      : <code className="bg-gray-200/70 rounded px-1 py-0.5 text-xs font-mono">{children}</code>,
};

function ThinkingDots() {
  return (
    <span className="flex items-center gap-1 py-0.5">
      <span className="block w-2 h-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '0ms' }} />
      <span className="block w-2 h-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '150ms' }} />
      <span className="block w-2 h-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '300ms' }} />
    </span>
  );
}

interface AIChatFloatingPanelProps {
  onClose: () => void;
}

export default function AIChatFloatingPanel({ onClose }: AIChatFloatingPanelProps) {
  const { openDatasetId } = useDrawerStore();
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function sendMessage() {
    if (!input.trim() || isStreaming) return;

    const userMsg = input.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', content: userMsg }]);
    setIsStreaming(true);

    let convId = conversationId;
    if (!convId) {
      try {
        const conv = await aiApi.createConversation();
        convId = conv.id;
        setConversationId(convId);
      } catch {
        setMessages(prev => [...prev, { role: 'assistant', content: 'Error: Could not connect to the AI service.' }]);
        setIsStreaming(false);
        return;
      }
    }

    let assistantContent = '';
    setMessages(prev => [...prev, { role: 'assistant', content: '', streaming: true }]);

    await aiApi.postMessage(
      convId,
      userMsg,
      (token) => {
        assistantContent += token;
        setMessages(prev => {
          const next = [...prev];
          next[next.length - 1] = { role: 'assistant', content: assistantContent, streaming: true };
          return next;
        });
      },
      () => {
        setMessages(prev => {
          const next = [...prev];
          next[next.length - 1] = { role: 'assistant', content: assistantContent };
          return next;
        });
        setIsStreaming(false);
      },
      () => {
        setMessages(prev => {
          const next = [...prev];
          next[next.length - 1] = { role: 'assistant', content: 'Error: Failed to get response.' };
          return next;
        });
        setIsStreaming(false);
      },
      openDatasetId
    );
  }

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-2xl border-l border-gray-200 flex flex-col z-50">
      <div className="px-4 py-3 border-b bg-blue-600 text-white flex items-center justify-between">
        <div>
          <h3 className="font-semibold text-sm">AI Assistant</h3>
          <p className="text-xs text-blue-200">Ask anything about your data catalog</p>
        </div>
        <button onClick={onClose} className="text-blue-200 hover:text-white text-xl leading-none">&times;</button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="text-center py-8">
            <p className="text-sm text-gray-500 mb-4">How can I help you discover and understand your data?</p>
            <div className="space-y-2">
              {[
                'What datasets contain customer data?',
                'Show me financial datasets with FIBO concepts',
                openDatasetId ? `Tell me about this dataset` : 'Which datasets have lineage tracked?',
              ].map(suggestion => (
                <button
                  key={suggestion}
                  onClick={() => setInput(suggestion)}
                  className="block w-full text-left text-xs px-3 py-2 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, i) => (
          <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[85%] rounded-xl px-3 py-2 text-sm ${
                msg.role === 'user'
                  ? 'bg-blue-600 text-white rounded-br-sm'
                  : 'bg-gray-100 text-gray-800 rounded-bl-sm'
              }`}
            >
              {msg.role === 'assistant' ? (
                msg.streaming && msg.content === '' ? (
                  <ThinkingDots />
                ) : (
                  <div className="space-y-1">
                    <ReactMarkdown remarkPlugins={[remarkBreaks]} components={mdComponents}>
                      {msg.content}
                    </ReactMarkdown>
                    {msg.streaming && (
                      <span className="inline-block w-1.5 h-4 bg-gray-400 ml-0.5 animate-pulse align-middle" />
                    )}
                  </div>
                )
              ) : (
                msg.content
              )}
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="px-4 py-3 border-t">
        <div className="flex gap-2">
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && !e.shiftKey && sendMessage()}
            placeholder="Ask about datasets..."
            disabled={isStreaming}
            className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          />
          <button
            onClick={sendMessage}
            disabled={isStreaming || !input.trim()}
            className="px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm"
          >
            ↑
          </button>
        </div>
        <p className="text-xs text-gray-400 mt-1.5">Enter to send · Powered by Ollama / OpenAI</p>
      </div>
    </div>
  );
}
