CREATE TABLE logical_element_audit_log (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    logical_element_id   UUID NOT NULL REFERENCES logical_data_elements(id) ON DELETE CASCADE,
    logical_model_id     UUID NOT NULL,
    dataset_id           UUID NOT NULL,
    event_type           VARCHAR(40) NOT NULL,
    changed_by_id        TEXT,
    changed_by_email     TEXT,
    payload_before       JSONB,
    payload_after        JSONB,
    tenant_id            UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lea_log_dataset_created
    ON logical_element_audit_log (dataset_id, created_at DESC);

CREATE INDEX idx_lea_log_element_created
    ON logical_element_audit_log (logical_element_id, created_at DESC);
