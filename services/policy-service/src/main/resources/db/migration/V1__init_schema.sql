CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE policy_records (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id    UUID        NOT NULL,
    tenant_id     UUID        NOT NULL,
    policy_level  VARCHAR(4)  NOT NULL DEFAULT 'A',
    policy_json   JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dataset_id, tenant_id)
);

CREATE INDEX idx_policy_records_tenant ON policy_records (tenant_id);
CREATE INDEX idx_policy_records_dataset ON policy_records (dataset_id);

CREATE TABLE evaluation_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id      UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    action          VARCHAR(64) NOT NULL,
    granted         BOOLEAN     NOT NULL,
    request_context JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_eval_log_dataset_tenant ON evaluation_log (dataset_id, tenant_id, created_at DESC);
