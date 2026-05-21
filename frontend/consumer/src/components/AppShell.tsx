import { useRef, useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts';
import AIChatFloatingPanel from './AIChatFloatingPanel';

export default function AppShell() {
  const searchRef = useRef<HTMLInputElement | null>(null);
  const [chatOpen, setChatOpen] = useState(false);
  const navigate = useNavigate();

  useKeyboardShortcuts({
    onFocusSearch: () => {
      navigate('/search');
      setTimeout(() => searchRef.current?.focus(), 100);
    },
    onOpenChat: () => setChatOpen(true),
  });

  return (
    <div className="min-h-screen flex flex-col">
      <Outlet context={{ searchRef }} />
      <button
        onClick={() => setChatOpen(true)}
        className="fixed bottom-6 right-6 bg-blue-600 text-white rounded-full px-5 py-3 shadow-lg hover:bg-blue-700 transition-colors text-sm font-medium flex items-center gap-2 z-40"
      >
        <span className="text-base leading-none">💬</span>
        Ask AI
        <kbd className="ml-1 text-xs bg-blue-500 px-1.5 py-0.5 rounded opacity-80">⌘K</kbd>
      </button>
      {chatOpen && <AIChatFloatingPanel onClose={() => setChatOpen(false)} />}
    </div>
  );
}
