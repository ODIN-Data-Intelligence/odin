-- ── Extensions ────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- trigram index for LIKE searches

-- ── Base resource table (polymorphic) ─────────────────────────────────────────
CREATE TABLE resources (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type       VARCHAR(50)  NOT NULL,   -- CATALOG, DATASET, DISTRIBUTION, DATA_SERVICE, DATA_PRODUCT, CATALOG_RECORD
    iri                 TEXT         UNIQUE,
    tenant_id           UUID         NOT NULL,
    domain_id           UUID,
    title               TEXT         NOT NULL,
    description         TEXT,
    issued              TIMESTAMPTZ,
    modified            TIMESTAMPTZ,
    language            TEXT[]       DEFAULT '{}',
    keywords            TEXT[]       DEFAULT '{}',
    themes              TEXT[]       DEFAULT '{}',
    license             TEXT,
    rights_statement    TEXT,
    access_rights       TEXT,
    conforms_to         TEXT[]       DEFAULT '{}',
    creator_id          UUID,
    publisher_id        UUID,
    contact_points      JSONB,       -- List<ContactPoint>
    source_uri          TEXT,
    extra               JSONB,
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_resources_tenant       ON resources (tenant_id);
CREATE INDEX idx_resources_type         ON resources (resource_type);
CREATE INDEX idx_resources_domain       ON resources (domain_id);
CREATE INDEX idx_resources_source_uri   ON resources (source_uri);
CREATE INDEX idx_resources_title_trgm   ON resources USING gin (title gin_trgm_ops);
CREATE INDEX idx_resources_keywords     ON resources USING gin (keywords);

-- ── Catalogs ──────────────────────────────────────────────────────────────────
CREATE TABLE catalogs (
    resource_id     UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    homepage        TEXT,
    has_part        UUID[]   DEFAULT '{}'  -- child catalog IDs
);

-- ── Datasets ──────────────────────────────────────────────────────────────────
CREATE TABLE datasets (
    resource_id              UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    catalog_id               UUID,
    accrual_periodicity      TEXT,
    temporal_start           TIMESTAMPTZ,
    temporal_end             TIMESTAMPTZ,
    spatial_resolution_m     DOUBLE PRECISION,
    temporal_resolution      TEXT,
    version                  TEXT,
    version_notes            TEXT,
    is_version_of            UUID REFERENCES datasets(resource_id)
);

CREATE INDEX idx_datasets_catalog ON datasets (catalog_id);

-- ── Distributions ─────────────────────────────────────────────────────────────
CREATE TABLE distributions (
    resource_id          UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    dataset_id           UUID NOT NULL REFERENCES datasets(resource_id) ON DELETE CASCADE,
    access_url           TEXT,
    download_url         TEXT,
    media_type           TEXT,
    format               TEXT,
    byte_size            BIGINT,
    checksum_algorithm   VARCHAR(20),
    checksum_value       TEXT,
    compress_format      TEXT,
    package_format       TEXT,
    availability         TEXT,
    csvw_table_id        UUID         -- FK added after csvw_tables created below
);

CREATE INDEX idx_distributions_dataset ON distributions (dataset_id);

-- ── Data Services ─────────────────────────────────────────────────────────────
CREATE TABLE data_services (
    resource_id             UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    endpoint_url            TEXT,
    endpoint_description    TEXT,
    serves_dataset          UUID[]  DEFAULT '{}',
    protocol                TEXT,
    security_schema_type    TEXT
);

-- ── Data Products ─────────────────────────────────────────────────────────────
CREATE TABLE data_products (
    resource_id              UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    lifecycle_status         VARCHAR(20) NOT NULL DEFAULT 'Ideation'
                             CHECK (lifecycle_status IN ('Ideation','Design','Build','Deploy','Consume')),
    owner_id                 UUID,
    purpose                  TEXT,
    information_sensitivity  VARCHAR(30),
    has_policy               JSONB
);

CREATE TABLE data_product_ports (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    data_product_id  UUID NOT NULL REFERENCES data_products(resource_id) ON DELETE CASCADE,
    port_type        VARCHAR(10) NOT NULL CHECK (port_type IN ('input','output')),
    data_service_id  UUID REFERENCES data_services(resource_id),
    dataset_id       UUID REFERENCES datasets(resource_id),
    distribution_id  UUID REFERENCES distributions(resource_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ports_product ON data_product_ports (data_product_id);

-- ── Catalog Records ───────────────────────────────────────────────────────────
CREATE TABLE catalog_records (
    resource_id          UUID PRIMARY KEY REFERENCES resources(id) ON DELETE CASCADE,
    catalog_id           UUID NOT NULL,
    primary_topic_id     UUID NOT NULL,    -- the described resource
    listing_date         TIMESTAMPTZ,
    modification_date    TIMESTAMPTZ,
    harvest_source       TEXT
);

CREATE INDEX idx_records_catalog ON catalog_records (catalog_id);

-- ── CSV-W Tables ──────────────────────────────────────────────────────────────
CREATE TABLE csvw_tables (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distribution_id  UUID REFERENCES distributions(resource_id) ON DELETE CASCADE,
    url              TEXT,
    title            TEXT,
    description      TEXT,
    dialect          JSONB,
    suppress_output  BOOLEAN DEFAULT FALSE,
    table_direction  TEXT,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE csvw_table_schemas (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_id     UUID NOT NULL REFERENCES csvw_tables(id) ON DELETE CASCADE,
    primary_key  TEXT[]  DEFAULT '{}',
    about_url    TEXT,
    property_url TEXT,
    value_url    TEXT
);

CREATE TABLE csvw_columns (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    schema_id       UUID NOT NULL REFERENCES csvw_table_schemas(id) ON DELETE CASCADE,
    ordinal         INT  NOT NULL,
    name            TEXT NOT NULL,
    titles          TEXT[]  DEFAULT '{}',
    datatype        TEXT,
    required        BOOLEAN DEFAULT FALSE,
    virtual         BOOLEAN DEFAULT FALSE,
    suppress_output BOOLEAN DEFAULT FALSE,
    lang            TEXT,
    default_value   TEXT,
    property_url    TEXT,
    value_url       TEXT,
    about_url       TEXT,
    description              TEXT,
    logical_data_element_id  UUID,    -- FK added after logical_data_elements created below
    UNIQUE (schema_id, ordinal)
);

CREATE INDEX idx_csvw_columns_schema ON csvw_columns (schema_id);

-- Add FK from distributions to csvw_tables (circular, so deferred)
ALTER TABLE distributions ADD CONSTRAINT fk_distributions_csvw
    FOREIGN KEY (csvw_table_id) REFERENCES csvw_tables(id) DEFERRABLE INITIALLY DEFERRED;

-- ── Vocabulary Registry ────────────────────────────────────────────────────────
CREATE TABLE vocabularies (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name             TEXT NOT NULL,
    prefix           TEXT NOT NULL,
    base_iri         TEXT NOT NULL,
    vocabulary_type  VARCHAR(20) CHECK (vocabulary_type IN ('general','financial','healthcare','geospatial','custom')),
    description      TEXT,
    version          TEXT,
    homepage         TEXT,
    is_system        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (prefix),
    UNIQUE (base_iri)
);

CREATE TABLE dataset_vocabulary_profiles (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id     UUID NOT NULL REFERENCES datasets(resource_id) ON DELETE CASCADE,
    vocabulary_id  UUID NOT NULL REFERENCES vocabularies(id),
    is_primary     BOOLEAN NOT NULL DEFAULT FALSE,
    domain_tags    TEXT[]  DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dataset_id, vocabulary_id)
);

CREATE INDEX idx_vocab_profiles_dataset ON dataset_vocabulary_profiles (dataset_id);

-- ── Logical Models ────────────────────────────────────────────────────────────
CREATE TABLE logical_models (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id  UUID NOT NULL REFERENCES datasets(resource_id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    description TEXT,
    version     TEXT NOT NULL DEFAULT '1.0',
    status      VARCHAR(20) NOT NULL DEFAULT 'draft'
                CHECK (status IN ('draft','published','deprecated')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_logical_models_dataset ON logical_models (dataset_id);

CREATE TABLE logical_data_elements (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logical_model_id     UUID NOT NULL REFERENCES logical_models(id) ON DELETE CASCADE,
    name                 TEXT NOT NULL,
    label                TEXT,
    description          TEXT,
    logical_type         TEXT,      -- MonetaryAmount, Identifier, Date, Party, ...
    is_required          BOOLEAN NOT NULL DEFAULT FALSE,
    is_identifier        BOOLEAN NOT NULL DEFAULT FALSE,
    is_nullable          BOOLEAN NOT NULL DEFAULT TRUE,
    ordinal              INT  NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lde_model ON logical_data_elements (logical_model_id);

-- Add FK from csvw_columns to logical_data_elements (forward ref, so deferred until here)
ALTER TABLE csvw_columns ADD CONSTRAINT fk_csvw_columns_lde
    FOREIGN KEY (logical_data_element_id) REFERENCES logical_data_elements(id) ON DELETE SET NULL;

CREATE INDEX idx_csvw_columns_lde ON csvw_columns (logical_data_element_id);

CREATE TABLE logical_element_vocab_mappings (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logical_element_id   UUID NOT NULL REFERENCES logical_data_elements(id) ON DELETE CASCADE,
    vocabulary_id        UUID NOT NULL REFERENCES vocabularies(id),
    concept_iri          TEXT NOT NULL,
    concept_label        TEXT,
    concept_definition   TEXT,
    match_type           VARCHAR(20) NOT NULL DEFAULT 'exactMatch'
                         CHECK (match_type IN ('exactMatch','closeMatch','relatedMatch','broadMatch','narrowMatch')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (logical_element_id, concept_iri)
);

CREATE INDEX idx_vocab_mappings_element ON logical_element_vocab_mappings (logical_element_id);
CREATE INDEX idx_vocab_mappings_iri     ON logical_element_vocab_mappings (concept_iri);

-- ── Cross-model Mappings ──────────────────────────────────────────────────────
CREATE TABLE cross_model_mappings (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type   VARCHAR(50) NOT NULL,
    source_id     UUID        NOT NULL,
    target_type   VARCHAR(50) NOT NULL,
    target_id     UUID        NOT NULL,
    mapping_type  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id, target_type, target_id)
);

-- ── Triggers: auto-update updated_at ─────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resources_updated_at
    BEFORE UPDATE ON resources
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_logical_models_updated_at
    BEFORE UPDATE ON logical_models
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_lde_updated_at
    BEFORE UPDATE ON logical_data_elements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
