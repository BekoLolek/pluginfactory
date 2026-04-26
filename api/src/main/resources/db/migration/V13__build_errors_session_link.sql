-- Allow BuildError rows that aren't tied to a build_iteration (e.g. plan-generation
-- failures happen before any iteration exists) and add a direct session_id link so
-- error queries don't require a join through iterations.

ALTER TABLE build_errors ADD COLUMN session_id UUID REFERENCES build_sessions(id);

UPDATE build_errors
SET session_id = (
    SELECT session_id FROM build_iterations WHERE id = build_errors.iteration_id
)
WHERE iteration_id IS NOT NULL;

ALTER TABLE build_errors ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE build_errors ALTER COLUMN iteration_id DROP NOT NULL;

CREATE INDEX idx_build_errors_session ON build_errors(session_id);
CREATE INDEX idx_build_errors_created ON build_errors(created_at DESC);
