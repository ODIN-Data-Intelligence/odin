import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import * as catalogApi from '@datacatalog/shared';
import type { LogicalDataElement } from '@datacatalog/shared';
import ClassificationRecommendationRow from './ClassificationRecommendationRow';

vi.mock('@datacatalog/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@datacatalog/shared')>();
  return {
    ...actual,
    logicalElementApi: {
      ...actual.logicalElementApi,
      acceptClassification: vi.fn(),
      rejectClassification: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(catalogApi.logicalElementApi);

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const ELEMENT: LogicalDataElement = {
  id: 'el-1',
  logicalModelId: 'model-1',
  name: 'Trade Amount',
  isRequired: false,
  isIdentifier: false,
  isNullable: true,
  ordinal: 1,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  recommendedClassification: 'CONFIDENTIAL',
  classificationReasoning: 'Contains financial transaction data.',
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ClassificationRecommendationRow', () => {
  it('should show the recommendation section header', () => {
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    expect(screen.getByText('AI Recommendation')).toBeInTheDocument();
  });

  it('should show the recommended classification badge', () => {
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    expect(screen.getByText('Confidential')).toBeInTheDocument();
  });

  it('should show the reasoning text', () => {
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    expect(screen.getByText('Contains financial transaction data.')).toBeInTheDocument();
  });

  it('should show Accept and Reject buttons', () => {
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    expect(screen.getByRole('button', { name: 'Accept' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
  });

  it('should call acceptClassification when Accept is clicked', async () => {
    mockedApi.acceptClassification.mockResolvedValue({ ...ELEMENT, classification: 'CONFIDENTIAL' });
    const user = userEvent.setup();
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    await user.click(screen.getByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(mockedApi.acceptClassification).toHaveBeenCalledWith('el-1'));
  });

  it('should call rejectClassification when Reject is clicked', async () => {
    mockedApi.rejectClassification.mockResolvedValue({ ...ELEMENT, recommendedClassification: undefined });
    const user = userEvent.setup();
    render(
      <table><tbody><ClassificationRecommendationRow element={ELEMENT} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    await user.click(screen.getByRole('button', { name: 'Reject' }));
    await waitFor(() => expect(mockedApi.rejectClassification).toHaveBeenCalledWith('el-1'));
  });

  it('should not show reasoning when classificationReasoning is absent', () => {
    const elementWithoutReasoning = { ...ELEMENT, classificationReasoning: undefined };
    render(
      <table><tbody><ClassificationRecommendationRow element={elementWithoutReasoning} modelId="model-1" /></tbody></table>,
      { wrapper },
    );
    expect(screen.queryByText('Contains financial transaction data.')).not.toBeInTheDocument();
  });
});
