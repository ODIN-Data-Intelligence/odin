-- Dataset ownership
ALTER TABLE datasets ADD COLUMN owner_id UUID;

-- Ownership transfer proposals
CREATE TABLE ownership_proposals (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id        UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    proposed_owner_id UUID NOT NULL,
    proposed_by_id    UUID NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    tenant_id         UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ
);
CREATE INDEX idx_ownership_proposals_dataset_status ON ownership_proposals (dataset_id, status);

-- Audit log
CREATE TABLE dataset_audit_log (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id       UUID NOT NULL REFERENCES resources(id) ON DELETE CASCADE,
    event_type       VARCHAR(40) NOT NULL,
    changed_by_id    TEXT,
    changed_by_email TEXT,
    payload_before   JSONB,
    payload_after    JSONB,
    tenant_id        UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dataset_audit_log_dataset_created ON dataset_audit_log (dataset_id, created_at DESC);
