export interface Resource {
  id: string;
  resourceType: string;
  iri?: string;
  tenantId: string;
  domainId?: string;
  title: string;
  description?: string;
  issued?: string;
  modified?: string;
  language?: string[];
  keywords?: string[];
  themes?: string[];
  license?: string;
  conformsTo?: string[];
  sourceUri?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Dataset extends Resource {
  accrualPeriodicity?: string;
  temporalStart?: string;
  temporalEnd?: string;
  spatial?: Record<string, unknown>;
  version?: string;
  isVersionOf?: string;
  ownerId?: string;
  distributions?: Distribution[];
  logicalModels?: LogicalModel[];
  semanticTypes?: string[];
  hasPolicy?: string;
}

export interface TermsOfUseDerivationDetails {
  totalPublishedElementCount: number;
  classifiedElementCount: number;
  elementsWithVocabCount: number;
  distinctClassifications: string[];
  vocabConceptCount: number;
  matchedSignals: string[];
  readyToAccept: boolean;
}

export interface TermsOfUse {
  effectiveClassification?: string;
  accessLevel?: 'OPEN' | 'INTERNAL_ONLY' | 'RESTRICTED' | 'HIGHLY_RESTRICTED';
  permissions: string[];
  prohibitions: string[];
  obligations: string[];
  applicableRegulations: string[];
  odrlPolicy?: Record<string, unknown>;
  policySource?: 'derived' | 'explicit' | 'fallback';
  derivationDetails?: TermsOfUseDerivationDetails;
}

export interface AcceptedSemanticTag {
  id: string;
  datasetId: string;
  type: string;
  vocabularyIri?: string;
  createdAt: string;
}

export interface DatasetSemanticContext {
  semanticTypes: string[];
  vocabConceptLabels: string[];
  vocabConceptIris: string[];
  fiboConcepts: string[];
  logicalElementNames: string[];
  logicalTypes: string[];
  acceptedTags: AcceptedSemanticTag[];
}

export interface RecommendedSemanticType {
  type: string;
  rationale: string;
  vocabularyHint?: string;
}

export interface SemanticContextRecommendation {
  types: RecommendedSemanticType[];
  rationale: string;
}

export interface DatasetAuditEntry {
  id: string;
  datasetId: string;
  eventType: string;
  changedById?: string;
  changedByEmail?: string;
  payloadBefore?: string;
  payloadAfter?: string;
  createdAt: string;
}

export interface OwnershipProposal {
  id: string;
  datasetId: string;
  proposedOwnerId: string;
  proposedById: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  resolvedAt?: string;
  note?: string;
}

export interface Distribution extends Resource {
  datasetId: string;
  accessUrl?: string;
  downloadUrl?: string;
  mediaType?: string;
  format?: string;
  byteSize?: number;
  checksumAlgorithm?: string;
  checksumValue?: string;
  csvwTableId?: string;
  compressFormat?: string;
  availability?: string;
  databaseName?: string;
  schemaName?: string;
  tableName?: string;
}

export interface DataProduct extends Resource {
  lifecycleStatus: 'Ideation' | 'Design' | 'Build' | 'Deploy' | 'Consume';
  ownerId?: string;
  purpose?: string;
  informationSensitivity?: string;
  hasPolicy?: Record<string, unknown>;
  ports?: DataProductPort[];
}

export interface DataProductPort {
  id: string;
  dataProductId: string;
  portType: 'input' | 'output';
  dataServiceId?: string;
  datasetId?: string;
  distributionId?: string;
}

export interface Catalog extends Resource {
  datasets?: Dataset[];
}

export interface CsvwTable {
  id: string;
  distributionId: string;
  url?: string;
  title?: string;
  dialect?: Record<string, unknown>;
  schema?: CsvwTableSchema;
}

export interface CsvwTableSchema {
  id: string;
  tableId: string;
  primaryKey?: string[];
  aboutUrl?: string;
  columns?: CsvwColumn[];
}

export interface CsvwColumn {
  id: string;
  schemaId: string;
  ordinal: number;
  name: string;
  titles?: string[];
  datatype?: string;
  required?: boolean;
  propertyUrl?: string;
  description?: string;
  logicalDataElementId?: string;
}

export interface Vocabulary {
  id: string;
  name: string;
  prefix: string;
  baseIri: string;
  vocabularyType: 'general' | 'financial' | 'healthcare' | 'geospatial' | 'custom';
  description?: string;
  version?: string;
  homepage?: string;
  isSystem: boolean;
}

export interface VocabularyConcept {
  iri: string;
  label: string;
  definition?: string;
}

export interface DatasetVocabularyProfile {
  id: string;
  datasetId: string;
  vocabularyId: string;
  vocabulary?: Vocabulary;
  isPrimary: boolean;
  domainTags?: string[];
  createdAt: string;
}

export interface LogicalModel {
  id: string;
  datasetId: string;
  name: string;
  description?: string;
  version: string;
  status: 'draft' | 'published' | 'deprecated';
  elements?: LogicalDataElement[];
  createdAt: string;
  updatedAt: string;
}

export interface RecommendedVocabMapping {
  conceptIri: string;
  conceptLabel?: string;
  conceptDefinition?: string;
  matchType: string;
  reasoning?: string;
}

export interface LogicalDataElement {
  id: string;
  logicalModelId: string;
  name: string;
  label?: string;
  description?: string;
  logicalType?: string;
  isRequired: boolean;
  isIdentifier: boolean;
  isNullable: boolean;
  ordinal: number;
  physicalColumnId?: string;
  physicalColumnRef?: { table: string; column: string; type: string };
  physicalColumn?: CsvwColumn;
  vocabMappings?: LogicalElementVocabMapping[];
  createdAt: string;
  updatedAt: string;
  classification?: 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'HIGH_CONFIDENTIAL';
  recommendedClassification?: 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'HIGH_CONFIDENTIAL';
  classificationReasoning?: string;
  classificationRecommendedAt?: string;
  recommendedDescription?: string;
  descriptionReasoning?: string;
  descriptionRecommendedAt?: string;
  recommendedVocabMappings?: RecommendedVocabMapping[];
  vocabMappingReasoning?: string;
  vocabMappingRecommendedAt?: string;
}

export interface LogicalElementVocabMapping {
  id: string;
  logicalElementId: string;
  vocabularyId: string;
  vocabulary?: Vocabulary;
  conceptIri: string;
  conceptLabel?: string;
  conceptDefinition?: string;
  matchType: 'exactMatch' | 'closeMatch' | 'relatedMatch' | 'broadMatch' | 'narrowMatch';
  createdAt: string;
}

export interface BulkRecommendationJob {
  jobId: string;
  modelId: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  createdAt: string;
  completedAt?: string;
  error?: string;
}

export interface ColumnElementSuggestion {
  columnId: string;
  columnName: string;
  suggestedElementId: string;
  suggestedElementName: string;
  confidence: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface DashboardSummary {
  ownedDatasetCount: number;
  ownedDataProductCount: number;
  pendingTransferRequests: OwnershipProposal[];
}

export interface ActivityProposal {
  id: string;
  datasetId: string;
  datasetTitle: string;
  proposedOwnerId: string;
  proposedById: string;
  role: 'PROPOSER' | 'NOMINEE';
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  resolvedAt?: string;
  note?: string;
}

export interface ActivityChange {
  id: string;
  datasetId: string;
  datasetTitle: string;
  eventType: string;
  createdAt: string;
}

export interface UserActivity {
  proposals: ActivityProposal[];
  changes: ActivityChange[];
}
