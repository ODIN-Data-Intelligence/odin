export type SourceType = 'dcat_http' | 'aws_glue' | 'snowflake' | 'teradata';
export type RunStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';

export interface HarvestSource {
  id: string;
  tenantId: string;
  name: string;
  sourceType: SourceType;
  baseUrl?: string;
  region?: string;
  databaseName?: string;
  schemaFilter?: string[];
  extraConfig?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface HarvestJob {
  id: string;
  sourceId: string;
  source?: HarvestSource;
  name: string;
  scheduleCron?: string;
  fullRefresh: boolean;
  enabled: boolean;
  createdAt: string;
}

export interface HarvestRun {
  id: string;
  jobId: string;
  sourceId: string;
  source?: HarvestSource;
  status: RunStatus;
  triggeredBy?: string;
  startedAt?: string;
  completedAt?: string;
  entitiesDiscovered?: number;
  entitiesCreated?: number;
  entitiesUpdated?: number;
  entitiesFailed?: number;
  snapshotPath?: string;
  errorMessage?: string;
}

export interface HarvestRunItem {
  id: string;
  runId: string;
  entityType: string;
  sourceKey: string;
  canonicalId?: string;
  action?: string;
  errorDetail?: string;
}
