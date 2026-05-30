import { get, post, put, patch, del } from './client';
import type {
  Catalog, Dataset, DataProduct, Distribution, CsvwColumn, PageResponse,
  LogicalModel, LogicalDataElement, LogicalElementVocabMapping,
  Vocabulary, DatasetVocabularyProfile, VocabularyConcept,
  ColumnElementSuggestion, DatasetAuditEntry, OwnershipProposal,
  BulkRecommendationJob, DatasetSemanticContext, SemanticContextRecommendation,
  AcceptedSemanticTag,
} from '../types/catalog';

const BASE = '/api/v1';

// Catalogs
export const catalogApi = {
  list: () => get<Catalog[]>(`${BASE}/catalogs`),
  get: (id: string) => get<Catalog>(`${BASE}/catalogs/${id}`),
  create: (body: Partial<Catalog>) => post<Catalog>(`${BASE}/catalogs`, body),
  export: (id: string) => get<unknown>(`${BASE}/catalogs/${id}/export`),
};

// Datasets
export const datasetApi = {
  list: (params?: { catalogId?: string; domain?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams(params as Record<string, string>).toString();
    return get<PageResponse<Dataset>>(`${BASE}/datasets${q ? '?' + q : ''}`);
  },
  get: (id: string) => get<Dataset>(`${BASE}/datasets/${id}`),
  create: (body: Partial<Dataset>) => post<Dataset>(`${BASE}/datasets`, body),
  update: (id: string, body: Partial<Dataset>) => put<Dataset>(`${BASE}/datasets/${id}`, body),
  delete: (id: string) => del<void>(`${BASE}/datasets/${id}`),
  listDistributions: (id: string) => get<Distribution[]>(`${BASE}/datasets/${id}/distributions`),
  createDistribution: (id: string, body: Partial<Distribution>) =>
    post<Distribution>(`${BASE}/datasets/${id}/distributions`, body),
  getPhysicalSchema: (id: string) => get<CsvwColumn[]>(`${BASE}/datasets/${id}/physical-schema`),
  getDistributionPhysicalSchema: (id: string) => get<CsvwColumn[]>(`${BASE}/distributions/${id}/physical-schema`),
  suggestElementMappings: (distributionId: string, modelId: string) =>
    get<ColumnElementSuggestion[]>(`${BASE}/distributions/${distributionId}/suggest-element-mappings?modelId=${modelId}`),
  // Ownership
  assignOwner: (id: string, userId: string) =>
    put<Dataset>(`${BASE}/datasets/${id}/owner`, { userId }),
  proposeTransfer: (id: string, proposedOwnerId: string) =>
    post<OwnershipProposal>(`${BASE}/datasets/${id}/ownership-proposals`, { proposedOwnerId }),
  getPendingProposal: (id: string) =>
    get<OwnershipProposal | null>(`${BASE}/datasets/${id}/ownership-proposals/pending`),
  approveTransfer: (id: string, proposalId: string) =>
    post<Dataset>(`${BASE}/datasets/${id}/ownership-proposals/${proposalId}/approve`, {}),
  rejectTransfer: (id: string, proposalId: string) =>
    post<OwnershipProposal>(`${BASE}/datasets/${id}/ownership-proposals/${proposalId}/reject`, {}),
  // Audit history
  getHistory: (id: string, page = 0, size = 20) =>
    get<PageResponse<DatasetAuditEntry>>(`${BASE}/datasets/${id}/history?page=${page}&size=${size}`),
  // Semantic context
  getSemanticContext: (id: string) =>
    get<DatasetSemanticContext>(`${BASE}/datasets/${id}/semantic-context`),
  recommendSemanticContext: (id: string) =>
    post<SemanticContextRecommendation>(`${BASE}/datasets/${id}/recommend-semantic-context`, {}),
  acceptSemanticTag: (id: string, body: { type: string; vocabularyIri?: string }) =>
    post<AcceptedSemanticTag>(`${BASE}/datasets/${id}/semantic-tags`, body),
  deleteSemanticTag: (id: string, tagId: string) =>
    del<void>(`${BASE}/datasets/${id}/semantic-tags/${tagId}`),
};

// Distributions
export const distributionApi = {
  list: (params?: { page?: number; size?: number }) => {
    const q = new URLSearchParams(params as Record<string, string>).toString();
    return get<PageResponse<Distribution>>(`${BASE}/distributions${q ? '?' + q : ''}`);
  },
};

// Data Products
export const dataProductApi = {
  list: (params?: { domain?: string; lifecycleStatus?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams(params as Record<string, string>).toString();
    return get<PageResponse<DataProduct>>(`${BASE}/data-products${q ? '?' + q : ''}`);
  },
  get: (id: string) => get<DataProduct>(`${BASE}/data-products/${id}`),
  create: (body: Partial<DataProduct>) => post<DataProduct>(`${BASE}/data-products`, body),
  update: (id: string, body: Partial<DataProduct>) => put<DataProduct>(`${BASE}/data-products/${id}`, body),
  patchLifecycle: (id: string, status: string) =>
    patch<DataProduct>(`${BASE}/data-products/${id}/lifecycle`, { status }),
  delete: (id: string) => del<void>(`${BASE}/data-products/${id}`),
  listDatasets: (id: string) => get<Dataset[]>(`${BASE}/data-products/${id}/datasets`),
  linkDataset: (id: string, datasetId: string) =>
    post<void>(`${BASE}/data-products/${id}/datasets`, { datasetId }),
  unlinkDataset: (id: string, datasetId: string) =>
    del<void>(`${BASE}/data-products/${id}/datasets/${datasetId}`),
};

// Vocabularies
export const vocabularyApi = {
  list: () => get<Vocabulary[]>(`${BASE}/vocabularies`),
  get: (id: string) => get<Vocabulary>(`${BASE}/vocabularies/${id}`),
  create: (body: Partial<Vocabulary>) => post<Vocabulary>(`${BASE}/vocabularies`, body),
  searchConcepts: (id: string, q: string, limit = 20) =>
    get<VocabularyConcept[]>(`${BASE}/vocabularies/${id}/concepts/search?q=${encodeURIComponent(q)}&limit=${limit}`),
  /** Resolve a single IRI to its preferred label. Falls back to humanized fragment. */
  translate: (iri: string) =>
    get<{ iri: string; label: string }>(`${BASE}/vocabularies/translate?iri=${encodeURIComponent(iri)}`),
  /** Resolve up to 200 IRIs to their preferred labels in one request. Returns iri→label map. */
  translateBatch: (iris: string[]) =>
    post<Record<string, string>>(`${BASE}/vocabularies/translate`, iris),
};

// Dataset vocabulary profiles
export const vocabProfileApi = {
  list: (datasetId: string) =>
    get<DatasetVocabularyProfile[]>(`${BASE}/datasets/${datasetId}/vocabulary-profiles`),
  create: (datasetId: string, body: { vocabularyId: string; isPrimary?: boolean; domainTags?: string[] }) =>
    post<DatasetVocabularyProfile>(`${BASE}/datasets/${datasetId}/vocabulary-profiles`, body),
  delete: (datasetId: string, vocabId: string) =>
    del<void>(`${BASE}/datasets/${datasetId}/vocabulary-profiles/${vocabId}`),
};

// Logical Models
export const logicalModelApi = {
  list: (datasetId: string) => get<LogicalModel[]>(`${BASE}/datasets/${datasetId}/logical-models`),
  get: (id: string) => get<LogicalModel>(`${BASE}/logical-models/${id}`),
  create: (datasetId: string, body: Partial<LogicalModel>) =>
    post<LogicalModel>(`${BASE}/datasets/${datasetId}/logical-models`, body),
  update: (id: string, body: Partial<LogicalModel>) => put<LogicalModel>(`${BASE}/logical-models/${id}`, body),
  patchStatus: (id: string, status: string) =>
    patch<LogicalModel>(`${BASE}/logical-models/${id}/status`, { status }),
  delete: (id: string) => del<void>(`${BASE}/logical-models/${id}`),
  suggestMappings: (id: string) => post<void>(`${BASE}/logical-models/${id}/suggest-mappings`),
};

// Logical Data Elements
export const logicalElementApi = {
  list: (modelId: string) => get<LogicalDataElement[]>(`${BASE}/logical-models/${modelId}/elements`),
  get: (id: string) => get<LogicalDataElement>(`${BASE}/logical-data-elements/${id}`),
  create: (modelId: string, body: Partial<LogicalDataElement>) =>
    post<LogicalDataElement>(`${BASE}/logical-models/${modelId}/elements`, body),
  update: (id: string, body: Partial<LogicalDataElement>) =>
    put<LogicalDataElement>(`${BASE}/logical-data-elements/${id}`, body),
  delete: (id: string) => del<void>(`${BASE}/logical-data-elements/${id}`),
  bind: (id: string, physicalColumnId: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/bind`, { physicalColumnId }),
  unbind: (id: string) =>
    del<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/bind`),
  recommendClassification: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/recommend-classification`, {}),
  acceptClassification: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/accept-classification`, {}),
  rejectClassification: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/reject-classification`, {}),
  recommendModelClassifications: (modelId: string) =>
    post<BulkRecommendationJob>(`${BASE}/logical-models/${modelId}/recommend-classifications`, {}),
  getRecommendationJob: (jobId: string) =>
    get<BulkRecommendationJob>(`${BASE}/logical-models/recommend-classifications/jobs/${jobId}`),
};

// Vocabulary Mappings
export const vocabMappingApi = {
  list: (elementId: string) =>
    get<LogicalElementVocabMapping[]>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`),
  create: (elementId: string, body: Partial<LogicalElementVocabMapping>) =>
    post<LogicalElementVocabMapping>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`, body),
  delete: (id: string) => del<void>(`${BASE}/vocab-mappings/${id}`),
};
