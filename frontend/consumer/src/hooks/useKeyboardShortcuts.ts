import { useEffect } from 'react';
import { useDrawerStore } from '../store/drawerStore';

interface Shortcuts {
  onFocusSearch: () => void;
  onOpenChat: () => void;
}

export function useKeyboardShortcuts({ onFocusSearch, onOpenChat }: Shortcuts) {
  const { closeDrawer, openDatasetId } = useDrawerStore();

  useEffect(() => {
    function handler(e: KeyboardEvent) {
      const tag = (e.target as HTMLElement).tagName;
      const isInput = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement).isContentEditable;

      if (e.key === 'Escape') {
        closeDrawer();
        return;
      }

      if (isInput) return;

      if (e.key === '/' && !e.ctrlKey && !e.metaKey) {
        e.preventDefault();
        onFocusSearch();
        return;
      }

      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        onOpenChat();
        return;
      }
    }

    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [closeDrawer, onFocusSearch, onOpenChat, openDatasetId]);
}
