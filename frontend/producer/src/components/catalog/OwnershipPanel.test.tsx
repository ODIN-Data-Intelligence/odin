import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as catalogApi from '@datacatalog/shared';
import type { Dataset, OwnershipProposal } from '@datacatalog/shared';
import OwnershipPanel from './OwnershipPanel';

vi.mock('@datacatalog/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@datacatalog/shared')>();
  return {
    ...actual,
    datasetApi: {
      assignOwner: vi.fn(),
      proposeTransfer: vi.fn(),
      getPendingProposal: vi.fn(),
      approveTransfer: vi.fn(),
      rejectTransfer: vi.fn(),
    },
    userApi: {
      get: vi.fn(),
      list: vi.fn(),
    },
  };
});

const mockedDatasetApi = vi.mocked(catalogApi.datasetApi);
const mockedUserApi = vi.mocked(catalogApi.userApi);

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const BASE_DATASET: Dataset = {
  id: 'ds-1',
  resourceType: 'DATASET',
  tenantId: 'tenant-1',
  title: 'Trade Data',
  ownerId: undefined,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedDatasetApi.getPendingProposal.mockResolvedValue(null);
});

describe('OwnershipPanel — unowned dataset', () => {
  it('should show "Unowned" badge when dataset has no owner', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Unowned')).toBeInTheDocument();
    });
  });

  it('should show the data owner section title', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Data Owner')).toBeInTheDocument();
    });
  });

  it('should not show Pending Transfer Proposal section when no proposal', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.queryByText('Pending Transfer Proposal')).not.toBeInTheDocument();
    });
  });
});

describe('OwnershipPanel — owned dataset', () => {
  const ownedDataset: Dataset = { ...BASE_DATASET, ownerId: 'user-owner-1' };

  beforeEach(() => {
    mockedUserApi.get.mockResolvedValue({
      id: 'user-owner-1',
      email: 'owner@example.com',
      firstName: 'Alice',
      lastName: 'Smith',
      roles: ['DATA_OWNER'],
      active: true,
    });
  });

  it('should show owner email when dataset has an owner', async () => {
    render(<OwnershipPanel dataset={ownedDataset} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('owner@example.com')).toBeInTheDocument();
    });
  });

  it('should show Transfer or Propose button when dataset is owned', async () => {
    render(<OwnershipPanel dataset={ownedDataset} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /transfer ownership|propose transfer/i }),
      ).toBeInTheDocument();
    });
  });
});

describe('OwnershipPanel — pending proposal', () => {
  const pendingProposal: OwnershipProposal = {
    id: 'proposal-1',
    datasetId: 'ds-1',
    proposedOwnerId: 'user-2',
    proposedById: 'user-3',
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  };

  beforeEach(() => {
    mockedDatasetApi.getPendingProposal.mockResolvedValue(pendingProposal);
  });

  it('should show Pending Transfer Proposal section when proposal exists', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Pending Transfer Proposal')).toBeInTheDocument();
    });
  });

  it('should show PENDING badge', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('PENDING')).toBeInTheDocument();
    });
  });
});
