import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import DatasetSummaryCard from './DatasetSummaryCard';
import { useDrawerStore } from '../store/drawerStore';
import type { SearchResult } from '@datacatalog/shared';

vi.mock('../store/drawerStore', () => ({
  useDrawerStore: vi.fn(),
}));

const mockOpenDataset = vi.fn();
const mockUseDrawerStore = useDrawerStore as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  mockUseDrawerStore.mockReturnValue({ openDataset: mockOpenDataset });
});

const baseResult: SearchResult = {
  id: 'ds-1',
  entityType: 'DATASET',
  tenantId: 'tenant-1',
  title: 'Trade Positions',
  description: 'Daily trade position snapshot',
};

function renderCard(props: Partial<{ result: SearchResult; isActive: boolean }> = {}) {
  return render(
    <MemoryRouter>
      <DatasetSummaryCard result={{ ...baseResult, ...props.result }} isActive={props.isActive ?? false} />
    </MemoryRouter>
  );
}

describe('DatasetSummaryCard', () => {
  it('should render the dataset title', () => {
    renderCard();
    expect(screen.getByText('Trade Positions')).toBeInTheDocument();
  });

  it('should render the description', () => {
    renderCard();
    expect(screen.getByText('Daily trade position snapshot')).toBeInTheDocument();
  });

  it('should call openDataset with the result id when clicked', async () => {
    renderCard();
    await userEvent.click(screen.getByRole('button'));
    expect(mockOpenDataset).toHaveBeenCalledWith('ds-1');
  });

  it('should display format badge when format is provided', () => {
    renderCard({ result: { ...baseResult, format: 'application/parquet' } });
    expect(screen.getByText('application/parquet')).toBeInTheDocument();
  });

  it('should display mediaType as format fallback', () => {
    renderCard({ result: { ...baseResult, mediaType: 'text/csv' } });
    expect(screen.getByText('text/csv')).toBeInTheDocument();
  });

  it('should not render format badge when neither format nor mediaType provided', () => {
    renderCard({ result: { ...baseResult } });
    // Only the title and description — no unknown format badge
    expect(screen.queryByText('text/csv')).toBeNull();
  });

  it('should show lineage badge when hasLineage is true', () => {
    renderCard({ result: { ...baseResult, hasLineage: true } });
    expect(screen.getByText('lineage')).toBeInTheDocument();
  });

  it('should not show lineage badge when hasLineage is false', () => {
    renderCard({ result: { ...baseResult, hasLineage: false } });
    expect(screen.queryByText('lineage')).toBeNull();
  });

  it('should show up to 3 vocab concept labels', () => {
    renderCard({
      result: { ...baseResult, vocabConceptLabels: ['Price', 'Currency', 'Amount', 'TradeDate'] },
    });
    expect(screen.getByText('Price')).toBeInTheDocument();
    expect(screen.getByText('Currency')).toBeInTheDocument();
    expect(screen.getByText('Amount')).toBeInTheDocument();
    expect(screen.queryByText('TradeDate')).toBeNull();
  });

  it('should apply active border style when isActive is true', () => {
    renderCard({ isActive: true });
    const button = screen.getByRole('button');
    expect(button.className).toContain('border-blue-400');
  });

  it('should apply inactive border style when isActive is false', () => {
    renderCard({ isActive: false });
    const button = screen.getByRole('button');
    expect(button.className).toContain('border-gray-200');
  });

  it('should render entity type in lowercase', () => {
    renderCard();
    expect(screen.getByText('dataset')).toBeInTheDocument();
  });

  it('should display lifecycleStatus when provided', () => {
    renderCard({ result: { ...baseResult, lifecycleStatus: 'Deploy' } });
    expect(screen.getByText('Deploy')).toBeInTheDocument();
  });
});
