import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { SearchResult } from '@datacatalog/shared';
import SearchResultCard from './SearchResultCard';

function renderCard(result: SearchResult) {
  return render(
    <MemoryRouter initialEntries={['/tenant-1']}>
      <Routes>
        <Route path="/:tenant" element={<SearchResultCard result={result} />} />
      </Routes>
    </MemoryRouter>,
  );
}

const BASE: SearchResult = {
  id: 'id-1',
  entityType: 'DATASET',
  tenantId: 'tenant-1',
  title: 'Trade Data',
  description: 'Daily trade positions',
  keywords: ['risk', 'trading'],
  updatedAt: '2024-01-15T10:00:00Z',
};

describe('SearchResultCard — DATASET', () => {
  it('should render title as a link to datasets/:id', () => {
    renderCard(BASE);
    const link = screen.getByRole('link', { name: 'Trade Data' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/tenant-1/datasets/id-1');
  });

  it('should show the blue Dataset badge', () => {
    renderCard(BASE);
    expect(screen.getByText('Dataset')).toBeInTheDocument();
  });

  it('should render description', () => {
    renderCard(BASE);
    expect(screen.getByText('Daily trade positions')).toBeInTheDocument();
  });

  it('should render keyword chips', () => {
    renderCard(BASE);
    expect(screen.getByText('risk')).toBeInTheDocument();
    expect(screen.getByText('trading')).toBeInTheDocument();
  });

  it('should show at most 4 keyword chips', () => {
    renderCard({ ...BASE, keywords: ['a', 'b', 'c', 'd', 'e'] });
    expect(screen.queryByText('e')).not.toBeInTheDocument();
  });

  it('should show lifecycle status pill when present', () => {
    renderCard({ ...BASE, lifecycleStatus: 'Consume' });
    expect(screen.getByText('Consume')).toBeInTheDocument();
  });

  it('should not show lifecycle pill when absent', () => {
    renderCard(BASE);
    expect(screen.queryByText('Consume')).not.toBeInTheDocument();
  });

  it('should show format badge when format is set', () => {
    renderCard({ ...BASE, format: 'Parquet' });
    expect(screen.getByText('Parquet')).toBeInTheDocument();
  });

  it('should not show format badge when format is absent', () => {
    renderCard(BASE);
    expect(screen.queryByText('Parquet')).not.toBeInTheDocument();
  });
});

describe('SearchResultCard — DATA_PRODUCT', () => {
  const dp: SearchResult = { ...BASE, id: 'dp-1', entityType: 'DATA_PRODUCT', title: 'Risk Product' };

  it('should render link to data-products/:id', () => {
    renderCard(dp);
    const link = screen.getByRole('link', { name: 'Risk Product' });
    expect(link).toHaveAttribute('href', '/tenant-1/data-products/dp-1');
  });

  it('should show the purple Data Product badge', () => {
    renderCard(dp);
    expect(screen.getByText('Data Product')).toBeInTheDocument();
  });
});

describe('SearchResultCard — DISTRIBUTION', () => {
  const dist: SearchResult = { ...BASE, id: 'dist-1', entityType: 'DISTRIBUTION', title: 'Trade CSV', format: 'CSV' };

  it('should show the gray Distribution badge', () => {
    renderCard(dist);
    expect(screen.getByText('Distribution')).toBeInTheDocument();
  });

  it('should render link to datasets list (no datasetId available)', () => {
    renderCard(dist);
    const link = screen.getByRole('link', { name: 'Trade CSV' });
    expect(link).toHaveAttribute('href', '/tenant-1/datasets');
  });

  it('should show the format badge', () => {
    renderCard(dist);
    expect(screen.getByText('CSV')).toBeInTheDocument();
  });
});
