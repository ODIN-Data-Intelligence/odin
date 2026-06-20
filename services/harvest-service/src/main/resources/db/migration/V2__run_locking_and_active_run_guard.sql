-- Optimistic locking for harvest run status read-modify-write.
ALTER TABLE harvest_runs ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

-- At most one active (pending/running) run per job, enforced at the DB level so two concurrent
-- triggers (two clicks / two replicas) cannot both launch the same harvest.
CREATE UNIQUE INDEX uq_harvest_runs_active_per_job
    ON harvest_runs (job_id)
    WHERE status IN ('pending', 'running');
