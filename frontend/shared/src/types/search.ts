export interface SearchResult {
  id: string;
  entityType: 'DATASET' | 'DATA_PRODUCT' | 'DISTRIBUTION';
  tenantId: string;
  title: string;
  description?: string;
  domainId?: string;
  keywords?: string[];
  themes?: string[];
  lifecycleStatus?: string;
  format?: string;
  mediaType?: string;
  distributionFormats?: string[];
  sourceUri?: string;
  hasLineage?: boolean;
  hasLogicalModel?: boolean;
  logicalElementNames?: string[];
  vocabConceptLabels?: string[];
  fiboConcepts?: string[];
  semanticTypes?: string[];
  score?: number;
  updatedAt?: string;
}

export interface SearchFacet {
  key: string;
  count: number;
}

export interface SearchFacets {
  entityTypes?: SearchFacet[];
  domains?: SearchFacet[];
  formats?: SearchFacet[];
  lifecycleStatuses?: SearchFacet[];
  vocabularyTypes?: SearchFacet[];
  fiboConcepts?: SearchFacet[];
  keywords?: SearchFacet[];
  themes?: SearchFacet[];
  vocabConcepts?: SearchFacet[];
  semanticTypes?: SearchFacet[];
}

export interface SearchResponse {
  results: SearchResult[];
  total: number;
  page: number;
  size: number;
  facets: SearchFacets;
}

export interface SearchRequest {
  q?: string;
  type?: string;
  types?: string;
  domain?: string;
  lifecycleStatus?: string;
  format?: string;
  hasLineage?: boolean;
  fiboConcept?: string;
  vocab?: string;
  keyword?: string;
  theme?: string;
  vocabConcept?: string;
  semanticType?: string;
  page?: number;
  size?: number;
}

export interface SavedSearch {
  id: string;
  name: string;
  query: SearchRequest;
  createdAt: string;
}
