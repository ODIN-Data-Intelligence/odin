import { get, post, put, patch, del, getAuthToken } from './client';
import { parseSseBuffer } from './ai';
import type {
  Catalog, Dataset, DataProduct, Distribution, CsvwColumn, PageResponse,
  AgenticEvent,
  LogicalModel, LogicalDataElement, LogicalElementVocabMapping,
  Vocabulary, DatasetVocabularyProfile, VocabularyConcept,
  ColumnElementSuggestion, DatasetAuditEntry, LogicalElementAuditEntry,
  LogicalModelAuditEntry, DatasetActivityEntry, OwnershipProposal,
  BulkRecommendationJob, DatasetSemanticContext, SemanticContextRecommendation,
  AcceptedSemanticTag, DashboardSummary, UserActivity, TermsOfUse,
  TermsPolicySet, TermsPolicyDetail, TermsClassificationRule,
  TermsRegulationRule, TermsRegulationObligation,
  PolicyRecord, PolicyComponentsResponse, EvaluationResponse, EvaluationLogEntry,
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
  approveTransfer: (id: string, proposalId: string, note?: string) =>
    post<Dataset>(`${BASE}/datasets/${id}/ownership-proposals/${proposalId}/approve`, { note }),
  rejectTransfer: (id: string, proposalId: string, note?: string) =>
    post<OwnershipProposal>(`${BASE}/datasets/${id}/ownership-proposals/${proposalId}/reject`, { note }),
  // Audit history
  getHistory: (id: string, page = 0, size = 20) =>
    get<PageResponse<DatasetAuditEntry>>(`${BASE}/datasets/${id}/history?page=${page}&size=${size}`),
  getElementHistory: (id: string, page = 0, size = 20) =>
    get<PageResponse<LogicalElementAuditEntry>>(`${BASE}/datasets/${id}/element-history?page=${page}&size=${size}`),
  getModelHistory: (id: string, page = 0, size = 20) =>
    get<PageResponse<LogicalModelAuditEntry>>(`${BASE}/datasets/${id}/model-history?page=${page}&size=${size}`),
  getActivity: (id: string, page = 0, size = 20) =>
    get<PageResponse<DatasetActivityEntry>>(`${BASE}/datasets/${id}/activity?page=${page}&size=${size}`),
  // Semantic context
  getSemanticContext: (id: string) =>
    get<DatasetSemanticContext>(`${BASE}/datasets/${id}/semantic-context`),
  recommendSemanticContext: (id: string) =>
    post<SemanticContextRecommendation>(`${BASE}/datasets/${id}/recommend-semantic-context`, {}),
  acceptSemanticTag: (id: string, body: { type: string; vocabularyIri?: string }) =>
    post<AcceptedSemanticTag>(`${BASE}/datasets/${id}/semantic-tags`, body),
  deleteSemanticTag: (id: string, tagId: string) =>
    del<void>(`${BASE}/datasets/${id}/semantic-tags/${tagId}`),
  // Terms of use
  getTermsOfUse: (id: string) =>
    get<TermsOfUse>(`${BASE}/datasets/${id}/terms-of-use`),
  acceptTermsOfUse: (id: string) =>
    post<TermsOfUse>(`${BASE}/datasets/${id}/terms-of-use/accept`, {}),
  resetTermsOfUse: (id: string) =>
    del<TermsOfUse>(`${BASE}/datasets/${id}/terms-of-use/policy`),
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
    post<{ translations: Record<string, string> }>(`${BASE}/vocabularies/translate`, iris),
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

  /**
   * Runs the agentic proposer/reviewer loop on ai-service and streams progress events.
   * Each SSE `data:` line is a JSON {@link AgenticEvent}. Pass an AbortSignal to cancel the
   * stream (e.g. when the dialog closes). Resolves once the stream ends.
   */
  agenticReview: async (
    datasetId: string,
    modelId: string,
    handlers: {
      onEvent: (event: AgenticEvent) => void;
      onDone: () => void;
      onError: (err: unknown) => void;
    },
    signal?: AbortSignal,
  ): Promise<void> => {
    const token = getAuthToken();
    let res: Response;
    try {
      res = await fetch(`${BASE}/agentic-review`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ datasetId, modelId }),
        signal,
      });
    } catch (e) {
      handlers.onError(e);
      return;
    }

    if (!res.ok || !res.body) {
      handlers.onError(new Error(`${res.status}: ${res.statusText}`));
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const { tokens, remaining } = parseSseBuffer(buffer);
        buffer = remaining;
        for (const t of tokens) {
          try {
            handlers.onEvent(JSON.parse(t) as AgenticEvent);
          } catch {
            // ignore a malformed/partial line — the next chunk will complete it
          }
        }
      }
      handlers.onDone();
    } catch (e) {
      handlers.onError(e);
    }
  },
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
  recommendDescription: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/recommend-description`, {}),
  acceptDescription: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/accept-description`, {}),
  rejectDescription: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/reject-description`, {}),
  recommendModelDescriptions: (modelId: string) =>
    post<BulkRecommendationJob>(`${BASE}/logical-models/${modelId}/recommend-descriptions`, {}),
  getDescriptionRecommendationJob: (jobId: string) =>
    get<BulkRecommendationJob>(`${BASE}/logical-models/recommend-descriptions/jobs/${jobId}`),
  recommendVocabConcepts: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/recommend-vocab-concepts`, {}),
  acceptVocabConcepts: (id: string, iris: string[]) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/accept-vocab-concepts`, { iris }),
  rejectVocabConcepts: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/reject-vocab-concepts`, {}),
  recommendModelVocabConcepts: (modelId: string) =>
    post<BulkRecommendationJob>(`${BASE}/logical-models/${modelId}/recommend-vocab-concepts`, {}),
  getVocabRecommendationJob: (jobId: string) =>
    get<BulkRecommendationJob>(`${BASE}/logical-models/recommend-vocab-concepts/jobs/${jobId}`),
  recommendPii: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/recommend-pii`, {}),
  acceptPii: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/accept-pii`, {}),
  rejectPii: (id: string) =>
    post<LogicalDataElement>(`${BASE}/logical-data-elements/${id}/reject-pii`, {}),
  recommendModelPii: (modelId: string) =>
    post<BulkRecommendationJob>(`${BASE}/logical-models/${modelId}/recommend-pii`, {}),
  getPiiRecommendationJob: (jobId: string) =>
    get<BulkRecommendationJob>(`${BASE}/logical-models/recommend-pii/jobs/${jobId}`),
};

// Dashboard
export const dashboardApi = {
  getSummary:  () => get<DashboardSummary>(`${BASE}/dashboard/summary`),
  getActivity: () => get<UserActivity>(`${BASE}/dashboard/activity`),
};

// Vocabulary Mappings
export const vocabMappingApi = {
  list: (elementId: string) =>
    get<LogicalElementVocabMapping[]>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`),
  create: (elementId: string, body: Partial<LogicalElementVocabMapping>) =>
    post<LogicalElementVocabMapping>(`${BASE}/logical-data-elements/${elementId}/vocab-mappings`, body),
  delete: (id: string) => del<void>(`${BASE}/vocab-mappings/${id}`),
};

// Terms Policies
export const termsPolicyApi = {
  list:     () => get<TermsPolicySet[]>(`${BASE}/terms-policies`),
  get:      (id: string) => get<TermsPolicyDetail>(`${BASE}/terms-policies/${id}`),
  create:   (body: { name: string; description?: string }) =>
              post<TermsPolicySet>(`${BASE}/terms-policies`, body),
  update:   (id: string, body: { name: string; description?: string }) =>
              put<TermsPolicySet>(`${BASE}/terms-policies/${id}`, body),
  delete:   (id: string) => del<void>(`${BASE}/terms-policies/${id}`),
  activate: (id: string) => post<TermsPolicySet>(`${BASE}/terms-policies/${id}/activate`, {}),
  clone:    (id: string, name: string) =>
              post<TermsPolicySet>(`${BASE}/terms-policies/${id}/clone`, { name }),

  upsertClassificationRule: (id: string, classification: string, body: Omit<TermsClassificationRule, 'id' | 'classification'>) =>
    put<TermsClassificationRule>(`${BASE}/terms-policies/${id}/classification-rules/${classification}`, body),
  deleteClassificationRule: (id: string, classification: string) =>
    del<void>(`${BASE}/terms-policies/${id}/classification-rules/${classification}`),

  addRegulationRule:    (id: string, body: Omit<TermsRegulationRule, 'id'>) =>
    post<TermsRegulationRule>(`${BASE}/terms-policies/${id}/regulation-rules`, body),
  updateRegulationRule: (id: string, ruleId: string, body: Omit<TermsRegulationRule, 'id'>) =>
    put<TermsRegulationRule>(`${BASE}/terms-policies/${id}/regulation-rules/${ruleId}`, body),
  deleteRegulationRule: (id: string, ruleId: string) =>
    del<void>(`${BASE}/terms-policies/${id}/regulation-rules/${ruleId}`),

  addRegulationObligation:    (id: string, body: Omit<TermsRegulationObligation, 'id'>) =>
    post<TermsRegulationObligation>(`${BASE}/terms-policies/${id}/regulation-obligations`, body),
  deleteRegulationObligation: (id: string, oblId: string) =>
    del<void>(`${BASE}/terms-policies/${id}/regulation-obligations/${oblId}`),
};

// Policy service (ODRE enforcement — port 8007, proxied via /api/v1/policies)
export const policyApi = {
  getPolicy:     (datasetId: string) =>
    get<PolicyRecord>(`${BASE}/policies/${datasetId}`),
  getComponents: (datasetId: string) =>
    get<PolicyComponentsResponse>(`${BASE}/policies/${datasetId}/components`),
  upsertPolicy:  (datasetId: string, body: { policyJson: string; policyLevel?: string }) =>
    put<PolicyRecord>(`${BASE}/policies/${datasetId}`, body),
  deletePolicy:  (datasetId: string) =>
    del<void>(`${BASE}/policies/${datasetId}`),
  evaluate:      (datasetId: string, body: { M: Record<string, unknown>; F?: Record<string, unknown> }) =>
    post<EvaluationResponse>(`${BASE}/policies/${datasetId}/evaluate`, body),
  listEvaluations: (datasetId: string, page = 0, size = 5) =>
    get<{ content: EvaluationLogEntry[]; totalElements: number }>(
      `${BASE}/policies/${datasetId}/evaluations?page=${page}&size=${size}`),
};
