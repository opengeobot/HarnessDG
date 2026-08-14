-- V11: Workflow tables (Wave 5)

-- Extend jobs table with workflow fields
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS fencing_token VARCHAR(64);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(128);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS timeout_at TIMESTAMPTZ;

-- Job events for SSE streaming
CREATE TABLE IF NOT EXISTS job_events (
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT NOT NULL REFERENCES jobs(id),
    sequence   BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    data       JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_job_event_seq UNIQUE (job_id, sequence)
);
CREATE INDEX IF NOT EXISTS ix_job_events_job ON job_events(job_id, sequence);

-- Inbox dedup
CREATE TABLE IF NOT EXISTS inbox_dedup (
    message_id VARCHAR(256) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Preview artifacts
CREATE TABLE IF NOT EXISTS preview_artifacts (
    id            BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repositories(id),
    job_id        BIGINT REFERENCES jobs(id),
    artifact_key  VARCHAR(256) NOT NULL,
    version       INT NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_preview_artifacts_repo ON preview_artifacts(repository_id);
