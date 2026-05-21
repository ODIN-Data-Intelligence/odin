CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS age;

-- AGE graph is created by the lineage-init.sql docker init script.
-- If this migration runs before the init script, we create it idempotently here.
DO $$
BEGIN
    PERFORM ag_catalog.create_graph('lineage_graph');
EXCEPTION WHEN OTHERS THEN
    NULL; -- graph may already exist
END $$;

CREATE TABLE lineage_jobs (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    namespace    TEXT NOT NULL,
    name         TEXT NOT NULL,
    facets       JSONB,
    age_vertex_id BIGINT,
    UNIQUE (namespace, name)
);

CREATE TABLE lineage_datasets (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    namespace           TEXT NOT NULL,
    name                TEXT NOT NULL,
    facets              JSONB,
    schema_facet        JSONB,
    catalog_resource_id UUID,
    age_vertex_id       BIGINT,
    UNIQUE (namespace, name)
);

CREATE TABLE lineage_runs (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id              TEXT NOT NULL UNIQUE,
    job_id              UUID NOT NULL REFERENCES lineage_jobs(id),
    facets              JSONB,
    nominal_start_time  TIMESTAMPTZ,
    nominal_end_time    TIMESTAMPTZ
);

CREATE TABLE lineage_run_events (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id      UUID NOT NULL REFERENCES lineage_runs(id),
    event_type  VARCHAR(20),
    event_time  TIMESTAMPTZ,
    producer    TEXT,
    schema_url  TEXT,
    inputs      JSONB,
    outputs     JSONB,
    raw_event   JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_run_events_run ON lineage_run_events (run_id);

CREATE TABLE column_lineage (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_event_id        UUID REFERENCES lineage_run_events(id),
    output_dataset_id   UUID REFERENCES lineage_datasets(id),
    output_column       TEXT,
    input_dataset_id    UUID REFERENCES lineage_datasets(id),
    input_column        TEXT,
    transformation_type TEXT
);
