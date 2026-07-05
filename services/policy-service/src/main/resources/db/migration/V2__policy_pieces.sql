CREATE TABLE policy_pieces (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    piece_type    VARCHAR(32)  NOT NULL,
    dimension_key VARCHAR(128) NOT NULL,
    label         TEXT,
    policy_json   JSONB        NOT NULL,
    policy_level  VARCHAR(4)   NOT NULL DEFAULT 'A',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, piece_type, dimension_key)
);

CREATE TABLE dataset_policy_links (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_id  UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    piece_id    UUID        NOT NULL REFERENCES policy_pieces(id) ON DELETE CASCADE,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (dataset_id, tenant_id, piece_id)
);

CREATE INDEX idx_dpl_dataset_tenant ON dataset_policy_links (dataset_id, tenant_id);
