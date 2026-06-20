-- Optimistic locking (@Version) for check-then-act flows that race under concurrent writes /
-- multiple replicas: dataset ownership transfer (resources) and ownership proposals.
ALTER TABLE resources           ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ownership_proposals ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
