-- Persist AI bulk-recommendation job state so any replica can serve poll requests
-- (previously held in an in-JVM ConcurrentHashMap, which broke at replicas > 1).
CREATE TABLE bulk_recommendation_jobs (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    model_id      UUID NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    error         TEXT
);

CREATE INDEX idx_bulk_rec_jobs_created ON bulk_recommendation_jobs (created_at DESC);
