CREATE TABLE logical_model_audit_log (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logical_model_id  UUID NOT NULL,
    dataset_id        UUID NOT NULL,
    event_type        VARCHAR(40) NOT NULL,
    changed_by_id     TEXT,
    changed_by_email  TEXT,
    payload_before    JSONB,
    payload_after     JSONB,
    tenant_id         UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lma_log_dataset_created
    ON logical_model_audit_log (dataset_id, created_at DESC);

CREATE INDEX idx_lma_log_model_created
    ON logical_model_audit_log (logical_model_id, created_at DESC);

-- Audit history must survive the element's own deletion. Drop the cascading FK
-- (matches the no-FK pattern already used for logical_model_id/dataset_id on this table).
ALTER TABLE logical_element_audit_log
    DROP CONSTRAINT logical_element_audit_log_logical_element_id_fkey;
