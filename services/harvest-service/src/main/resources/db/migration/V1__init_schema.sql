CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE harvest_sources (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        UUID NOT NULL,
    name             TEXT NOT NULL,
    source_type      VARCHAR(30) NOT NULL CHECK (source_type IN ('dcat_http','aws_glue','snowflake','teradata')),
    base_url         TEXT,
    region           TEXT,
    database_name    TEXT,
    schema_filter    TEXT[]  DEFAULT '{}',
    credential_ref   TEXT,
    extra_config     JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE harvest_credentials (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id        UUID NOT NULL REFERENCES harvest_sources(id) ON DELETE CASCADE,
    credential_type  VARCHAR(30) NOT NULL,
    encrypted_payload TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE harvest_jobs (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id        UUID NOT NULL REFERENCES harvest_sources(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    schedule_cron    TEXT,
    full_refresh     BOOLEAN NOT NULL DEFAULT FALSE,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE harvest_runs (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id                UUID NOT NULL REFERENCES harvest_jobs(id),
    source_id             UUID NOT NULL REFERENCES harvest_sources(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'pending'
                          CHECK (status IN ('pending','running','completed','failed','cancelled')),
    triggered_by          VARCHAR(50),
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    entities_discovered   INT,
    entities_created      INT,
    entities_updated      INT,
    entities_failed       INT,
    snapshot_path         TEXT,
    error_message         TEXT,
    full_refresh          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runs_job    ON harvest_runs (job_id);
CREATE INDEX idx_runs_status ON harvest_runs (status);

CREATE TABLE harvest_run_items (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id               UUID NOT NULL REFERENCES harvest_runs(id) ON DELETE CASCADE,
    entity_type          VARCHAR(30),
    source_key           TEXT,
    canonical_id         UUID,
    action               VARCHAR(20),
    raw_payload          JSONB,
    normalized_payload   JSONB,
    error_detail         TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_run ON harvest_run_items (run_id);
