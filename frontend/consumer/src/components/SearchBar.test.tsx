import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import SearchBar from './SearchBar';
import { useSearchStore } from '../store/searchStore';

vi.mock('../store/searchStore', () => ({
  useSearchStore: vi.fn(),
}));

vi.mock('@datacatalog/shared', () => ({
  searchApi: {
    suggest: vi.fn().mockResolvedValue([]),
  },
}));

const mockSetQuery = vi.fn();
const mockUseSearchStore = useSearchStore as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSearchStore.mockReturnValue({ query: '', setQuery: mockSetQuery });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function renderSearchBar(large?: boolean) {
  return render(
    <MemoryRouter>
      <SearchBar large={large} />
    </MemoryRouter>
  );
}

describe('SearchBar', () => {
  it('should render the search input', () => {
    renderSearchBar();
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('should have placeholder text', () => {
    renderSearchBar();
    expect(screen.getByPlaceholderText(/Search datasets/i)).toBeInTheDocument();
  });

  it('should render the Search button', () => {
    renderSearchBar();
    expect(screen.getByRole('button', { name: 'Search' })).toBeInTheDocument();
  });

  it('should update input value as user types', async () => {
    const user = userEvent.setup();
    renderSearchBar();
    const input = screen.getByRole('textbox');
    await user.type(input, 'trades');
    expect(input).toHaveValue('trades');
  });

  it('should show clear button when input has value', async () => {
    const user = userEvent.setup();
    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'x');
    expect(screen.getByText('×')).toBeInTheDocument();
  });

  it('should clear input when × button is clicked', async () => {
    const user = userEvent.setup();
    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'something');
    await user.click(screen.getByText('×'));
    expect(screen.getByRole('textbox')).toHaveValue('');
    expect(mockSetQuery).toHaveBeenCalledWith('');
  });

  it('should call setQuery on Enter key', async () => {
    const user = userEvent.setup();
    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'trades');
    await user.keyboard('{Enter}');
    expect(mockSetQuery).toHaveBeenCalledWith('trades');
  });

  it('should call setQuery on Search button click', async () => {
    const user = userEvent.setup();
    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'risk');
    await user.click(screen.getByRole('button', { name: 'Search' }));
    expect(mockSetQuery).toHaveBeenCalledWith('risk');
  });

  it('should fetch suggestions after 250ms debounce when input has ≥2 chars', async () => {
    const user = userEvent.setup();
    const { searchApi } = await import('@datacatalog/shared');
    (searchApi.suggest as ReturnType<typeof vi.fn>).mockResolvedValue(['trades', 'risk']);

    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'tr');

    await waitFor(() => expect(searchApi.suggest).toHaveBeenCalledWith('tr'), { timeout: 2000 });
  }, 6000);

  it('should not fetch suggestions for input shorter than 2 chars', async () => {
    const user = userEvent.setup();
    const { searchApi } = await import('@datacatalog/shared');

    renderSearchBar();
    await user.type(screen.getByRole('textbox'), 'a');

    // Wait long enough for the debounce to fire if it were going to
    await new Promise(r => setTimeout(r, 400));
    expect(searchApi.suggest).not.toHaveBeenCalled();
  }, 6000);

  it('should apply large styles when large prop is true', () => {
    renderSearchBar(true);
    expect(screen.getByRole('textbox').className).toContain('py-4');
  });

  it('should apply small styles when large prop is false', () => {
    renderSearchBar(false);
    expect(screen.getByRole('textbox').className).toContain('py-2.5');
  });
});
