import { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Divider from '@mui/material/Divider';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import { aiApi } from '@datacatalog/shared';
import { useDrawerStore } from '../store/drawerStore';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  streaming?: boolean;
}

const mdComponents: React.ComponentProps<typeof ReactMarkdown>['components'] = {
  p:          ({ children }) => <Typography variant="body2" component="p" sx={{ mb: 0.5, lineHeight: 1.5 }}>{children}</Typography>,
  strong:     ({ children }) => <Box component="strong" sx={{ fontWeight: 600 }}>{children}</Box>,
  ul:         ({ children }) => <Box component="ul" sx={{ pl: 2, mb: 0.5 }}>{children}</Box>,
  ol:         ({ children }) => <Box component="ol" sx={{ pl: 2, mb: 0.5 }}>{children}</Box>,
  li:         ({ children }) => <Typography component="li" variant="body2" sx={{ lineHeight: 1.5 }}>{children}</Typography>,
  h1:         ({ children }) => <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 1, mb: 0.5 }}>{children}</Typography>,
  h2:         ({ children }) => <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 1, mb: 0.5 }}>{children}</Typography>,
  h3:         ({ children }) => <Typography variant="body2" fontWeight={600} sx={{ mt: 0.5, mb: 0.25 }}>{children}</Typography>,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  code:       ({ className, children, ...props }: any) =>
    className?.startsWith('language-')
      ? <Box component="code" className={className} sx={{ fontFamily: 'monospace', fontSize: 12 }} {...props}>{children}</Box>
      : <Box component="code" sx={{ bgcolor: 'rgba(0,0,0,0.08)', borderRadius: 0.5, px: 0.5, fontFamily: 'monospace', fontSize: 12 }}>{children}</Box>,
  pre:        ({ children }) => (
    <Box component="pre" sx={{ bgcolor: 'rgba(0,0,0,0.06)', borderRadius: 1, p: 1.5, fontSize: 12, fontFamily: 'monospace', overflowX: 'auto', whiteSpace: 'pre-wrap', my: 0.75 }}>
      {children}
    </Box>
  ),
};

function ThinkingDots() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, py: 0.5 }}>
      {[0, 150, 300].map(delay => (
        <Box key={delay} sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.disabled', animation: 'bounce 1s infinite', animationDelay: `${delay}ms`,
          '@keyframes bounce': { '0%,100%': { transform: 'translateY(0)' }, '50%': { transform: 'translateY(-4px)' } }
        }} />
      ))}
    </Box>
  );
}

interface AIChatFloatingPanelProps {
  open: boolean;
  onClose: () => void;
}

export default function AIChatFloatingPanel({ open, onClose }: AIChatFloatingPanelProps) {
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

  const suggestions = [
    'What datasets contain customer data?',
    'Show me financial datasets with FIBO concepts',
    openDatasetId ? 'Tell me about this dataset' : 'Which datasets have lineage tracked?',
  ];

  return (
    <Drawer anchor="right" open={open} onClose={onClose} sx={{ zIndex: (theme) => theme.zIndex.tooltip + 1 }} PaperProps={{ sx: { width: 480 } }}>
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Header */}
        <Box sx={{ px: 2, py: 1.5, bgcolor: 'primary.main', color: 'primary.contrastText', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="subtitle2" fontWeight={600}>AI Assistant</Typography>
            <Typography variant="caption" sx={{ opacity: 0.8 }}>Ask anything about your data catalog</Typography>
          </Box>
          <IconButton onClick={onClose} sx={{ color: 'primary.contrastText', opacity: 0.8, '&:hover': { opacity: 1 } }} size="small">
            <CloseIcon />
          </IconButton>
        </Box>

        {/* Messages */}
        <Box sx={{ flex: 1, overflowY: 'auto', px: 2, py: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {messages.length === 0 && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                How can I help you discover and understand your data?
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                {suggestions.map(s => (
                  <Button
                    key={s}
                    variant="outlined"
                    size="small"
                    onClick={() => setInput(s)}
                    sx={{ textAlign: 'left', justifyContent: 'flex-start', textTransform: 'none' }}
                  >
                    {s}
                  </Button>
                ))}
              </Box>
            </Box>
          )}

          {messages.map((msg, i) => (
            <Box key={i} sx={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
              <Paper
                elevation={0}
                sx={{
                  maxWidth: '85%',
                  px: 1.5,
                  py: 1,
                  borderRadius: msg.role === 'user' ? '12px 12px 2px 12px' : '12px 12px 12px 2px',
                  bgcolor: msg.role === 'user' ? 'primary.main' : 'grey.100',
                  color: msg.role === 'user' ? 'primary.contrastText' : 'text.primary',
                }}
              >
                {msg.role === 'assistant' ? (
                  msg.streaming && msg.content === '' ? (
                    <ThinkingDots />
                  ) : (
                    <Box>
                      <ReactMarkdown remarkPlugins={[remarkBreaks]} components={mdComponents}>
                        {msg.content}
                      </ReactMarkdown>
                      {msg.streaming && (
                        <Box component="span" sx={{ display: 'inline-block', width: 6, height: 16, bgcolor: 'text.disabled', ml: 0.5, animation: 'pulse 1s infinite', '@keyframes pulse': { '0%,100%': { opacity: 1 }, '50%': { opacity: 0.3 } }, verticalAlign: 'middle' }} />
                      )}
                    </Box>
                  )
                ) : (
                  <Typography variant="body2">{msg.content}</Typography>
                )}
              </Paper>
            </Box>
          ))}
          <div ref={bottomRef} />
        </Box>

        {/* Input */}
        <Divider />
        <Box sx={{ px: 2, py: 1.5 }}>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <TextField
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !e.shiftKey && sendMessage()}
              placeholder="Ask about datasets..."
              disabled={isStreaming}
              size="small"
              fullWidth
              variant="outlined"
            />
            <IconButton
              onClick={sendMessage}
              disabled={isStreaming || !input.trim()}
              color="primary"
              sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', borderRadius: 2, '&:hover': { bgcolor: 'primary.dark' }, '&:disabled': { bgcolor: 'grey.200' } }}
            >
              <SendIcon fontSize="small" />
            </IconButton>
          </Box>
          <Typography variant="caption" color="text.disabled" sx={{ mt: 0.75, display: 'block' }}>
            Enter to send · Powered by Ollama / OpenAI
          </Typography>
        </Box>
      </Box>
    </Drawer>
  );
}
