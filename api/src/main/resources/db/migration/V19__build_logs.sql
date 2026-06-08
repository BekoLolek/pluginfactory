-- Raw per-step output of a build's containers (Maven compile, Paper runtime
-- smoke test, functional/bot test), captured as it runs so the admin
-- dashboard can show the actual terminal output for any build — pass or fail.
-- One row per step per iteration (so each auto-fix attempt is visible).
CREATE TABLE build_logs (
    id           UUID PRIMARY KEY,
    session_id   UUID NOT NULL,
    iteration_id UUID,
    phase        VARCHAR(32) NOT NULL,
    exit_code    INTEGER,
    content      TEXT NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_build_logs_session ON build_logs (session_id, created_at);
