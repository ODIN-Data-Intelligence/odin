import { get, post, put, patch, del } from './client';
import type {
  Catalog, Dataset, DataProduct, Distribution, CsvwColumn, PageResponse,
  LogicalModel, LogicalDataElement, LogicalElementVocabMapping,
  Vocabulary, DatasetVocabularyProfile, VocabularyConcept,
  ColumnElementSuggestion,
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
};

// Vocabulary Mappings
export const vocabMappingApi = {
  list: (elementId: string) =>
    get<LogicalElementVocabMapping[]>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`),
  create: (elementId: string, body: Partial<LogicalElementVocabMapping>) =>
    post<LogicalElementVocabMapping>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`, body),
  delete: (id: string) => del<void>(`${BASE}/vocab-mappings/${id}`),
};
