import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import RecentlyViewed, { recordRecentlyViewed } from './RecentlyViewed';

const KEY = 'dc_recently_viewed';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { store = {}; },
  };
})();

beforeEach(() => {
  localStorageMock.clear();
  vi.stubGlobal('localStorage', localStorageMock);
  vi.clearAllMocks();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('recordRecentlyViewed', () => {
  it('should save an entry to localStorage', () => {
    recordRecentlyViewed('ds-1', 'Trade Positions');

    const stored: { id: string; title: string }[] = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    expect(stored[0].id).toBe('ds-1');
    expect(stored[0].title).toBe('Trade Positions');
  });

  it('should prepend new entries (most-recent first)', () => {
    recordRecentlyViewed('ds-1', 'First');
    recordRecentlyViewed('ds-2', 'Second');

    const stored: { id: string }[] = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    expect(stored[0].id).toBe('ds-2');
    expect(stored[1].id).toBe('ds-1');
  });

  it('should deduplicate entries by id', () => {
    recordRecentlyViewed('ds-1', 'Old Title');
    recordRecentlyViewed('ds-1', 'New Title');

    const stored: { id: string; title: string }[] = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    expect(stored).toHaveLength(1);
    expect(stored[0].title).toBe('New Title');
  });

  it('should limit to 5 entries', () => {
    for (let i = 1; i <= 7; i++) {
      recordRecentlyViewed(`ds-${i}`, `Dataset ${i}`);
    }

    const stored: unknown[] = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    expect(stored).toHaveLength(5);
  });

  it('should not throw when localStorage throws on getItem', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => { throw new Error('QuotaExceeded'); },
      setItem: vi.fn(),
    });
    expect(() => recordRecentlyViewed('ds-1', 'Title')).not.toThrow();
  });
});

describe('RecentlyViewed component', () => {
  it('should return null when localStorage is empty', () => {
    const { container } = render(
      <MemoryRouter><RecentlyViewed /></MemoryRouter>
    );
    expect(container.firstChild).toBeNull();
  });

  it('should render entries from localStorage', () => {
    localStorageMock.setItem(KEY, JSON.stringify([
      { id: 'ds-1', title: 'Trade Positions', viewedAt: 1000 },
      { id: 'ds-2', title: 'Risk Metrics', viewedAt: 900 },
    ]));

    render(<MemoryRouter><RecentlyViewed /></MemoryRouter>);

    expect(screen.getByText('Trade Positions')).toBeInTheDocument();
    expect(screen.getByText('Risk Metrics')).toBeInTheDocument();
  });

  it('should render the section heading', () => {
    localStorageMock.setItem(KEY, JSON.stringify([
      { id: 'ds-1', title: 'My Dataset', viewedAt: 1000 },
    ]));

    render(<MemoryRouter><RecentlyViewed /></MemoryRouter>);

    expect(screen.getByText('Recently Viewed')).toBeInTheDocument();
  });

  it('should navigate to /search?ds=id when an entry is clicked', async () => {
    const user = userEvent.setup();
    localStorageMock.setItem(KEY, JSON.stringify([
      { id: 'ds-42', title: 'Clicked Dataset', viewedAt: 1000 },
    ]));

    render(<MemoryRouter><RecentlyViewed /></MemoryRouter>);
    await user.click(screen.getByText('Clicked Dataset'));

    expect(mockNavigate).toHaveBeenCalledWith('/search?ds=ds-42');
  });
});
