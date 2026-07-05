import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as catalogApi from '@datacatalog/shared';
import type { Dataset, OwnershipProposal } from '@datacatalog/shared';
import OwnershipPanel from './OwnershipPanel';
import { useAuthStore } from '../../store/authStore';

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
    mockedUserApi.list.mockResolvedValue([{
      id: 'user-owner-1',
      email: 'owner@example.com',
      firstName: 'Alice',
      lastName: 'Smith',
      roles: ['DATA_OWNER'],
      active: true,
    }]);
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

  it('should show Pending Transfer Proposal section when dataset is owned', async () => {
    const ownedDataset: Dataset = { ...BASE_DATASET, ownerId: 'user-owner-1' };
    render(<OwnershipPanel dataset={ownedDataset} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Pending Transfer Proposal')).toBeInTheDocument();
    });
  });

  it('should show Pending Ownership Nomination section when dataset is unowned', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('Pending Ownership Nomination')).toBeInTheDocument();
    });
  });

  it('should show PENDING badge', async () => {
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => {
      expect(screen.getByText('PENDING')).toBeInTheDocument();
    });
  });
});

describe('OwnershipPanel — transfer proposal action gating', () => {
  const PRIOR_OWNER_ID = 'kc-owner-1';
  const PROPOSED_OWNER_ID = 'kc-proposed-1';

  const ownedDataset: Dataset = { ...BASE_DATASET, ownerId: PRIOR_OWNER_ID };

  const transferProposal: OwnershipProposal = {
    id: 'proposal-transfer',
    datasetId: 'ds-1',
    proposedOwnerId: PROPOSED_OWNER_ID,
    proposedById: PRIOR_OWNER_ID,
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  };

  beforeEach(() => {
    mockedDatasetApi.getPendingProposal.mockResolvedValue(transferProposal);
    useAuthStore.setState({ userId: null, roles: [], token: null, tenantId: null, email: null, displayName: null });
  });

  it('proposed owner cannot approve or decline a transfer proposal', async () => {
    useAuthStore.setState({ userId: PROPOSED_OWNER_ID, roles: ['data-owner'] });
    render(<OwnershipPanel dataset={ownedDataset} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => screen.getByText('PENDING'));
    expect(screen.queryByRole('button', { name: /approve|decline/i })).not.toBeInTheDocument();
    expect(screen.getByText(/awaiting approval from the current owner/i)).toBeInTheDocument();
  });

  it('prior owner can approve or decline a transfer proposal', async () => {
    useAuthStore.setState({ userId: PRIOR_OWNER_ID, roles: ['data-owner'] });
    render(<OwnershipPanel dataset={ownedDataset} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => screen.getByText('PENDING'));
    expect(screen.getByRole('button', { name: /^approve$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^decline$/i })).toBeInTheDocument();
  });

  it('proposed owner can accept an ownership nomination on an unowned dataset', async () => {
    useAuthStore.setState({ userId: PROPOSED_OWNER_ID, roles: ['data-owner'] });
    render(<OwnershipPanel dataset={BASE_DATASET} onUpdated={vi.fn()} />, { wrapper });
    await waitFor(() => screen.getByText('PENDING'));
    expect(screen.getByRole('button', { name: /^accept$/i })).toBeInTheDocument();
  });
});
