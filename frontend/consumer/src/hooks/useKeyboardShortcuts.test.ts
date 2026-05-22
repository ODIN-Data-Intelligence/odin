import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useKeyboardShortcuts } from './useKeyboardShortcuts';
import { useDrawerStore } from '../store/drawerStore';

// Mock the drawer store so closeDrawer is observable
vi.mock('../store/drawerStore', () => ({
  useDrawerStore: vi.fn(),
}));

const mockCloseDrawer = vi.fn();
const mockUseDrawerStore = useDrawerStore as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  mockUseDrawerStore.mockReturnValue({ closeDrawer: mockCloseDrawer, openDatasetId: null });
});

function fireKey(key: string, options: Partial<KeyboardEventInit> = {}, target: EventTarget = window) {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, ...options });
  Object.defineProperty(event, 'target', { value: target });
  window.dispatchEvent(event);
  return event;
}

describe('useKeyboardShortcuts', () => {
  it('should call closeDrawer on Escape', () => {
    const onFocusSearch = vi.fn();
    const onOpenChat = vi.fn();

    renderHook(() => useKeyboardShortcuts({ onFocusSearch, onOpenChat }));

    fireKey('Escape');

    expect(mockCloseDrawer).toHaveBeenCalledOnce();
    expect(onFocusSearch).not.toHaveBeenCalled();
  });

  it('should call onFocusSearch on "/" key when not in input', () => {
    const onFocusSearch = vi.fn();
    const onOpenChat = vi.fn();

    renderHook(() => useKeyboardShortcuts({ onFocusSearch, onOpenChat }));

    const event = new KeyboardEvent('keydown', { key: '/', bubbles: true });
    // Default target is body, not input
    window.dispatchEvent(event);

    expect(onFocusSearch).toHaveBeenCalledOnce();
    expect(onOpenChat).not.toHaveBeenCalled();
  });

  it('should call onOpenChat on Ctrl+K', () => {
    const onFocusSearch = vi.fn();
    const onOpenChat = vi.fn();

    renderHook(() => useKeyboardShortcuts({ onFocusSearch, onOpenChat }));

    const event = new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true });
    window.dispatchEvent(event);

    expect(onOpenChat).toHaveBeenCalledOnce();
    expect(onFocusSearch).not.toHaveBeenCalled();
  });

  it('should call onOpenChat on Meta+K (Mac)', () => {
    const onFocusSearch = vi.fn();
    const onOpenChat = vi.fn();

    renderHook(() => useKeyboardShortcuts({ onFocusSearch, onOpenChat }));

    const event = new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true });
    window.dispatchEvent(event);

    expect(onOpenChat).toHaveBeenCalledOnce();
  });

  it('should remove the event listener on unmount', () => {
    const removeSpy = vi.spyOn(window, 'removeEventListener');
    const { unmount } = renderHook(() =>
      useKeyboardShortcuts({ onFocusSearch: vi.fn(), onOpenChat: vi.fn() })
    );

    unmount();

    expect(removeSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
    removeSpy.mockRestore();
  });
});
