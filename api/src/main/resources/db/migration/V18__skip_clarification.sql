-- "Skip questions — just build it": when set, the chatbot does not ask
-- clarifying questions. It briefly summarizes what it will build from the
-- user's initial description and goes straight to plan generation.
ALTER TABLE build_sessions ADD COLUMN skip_clarification BOOLEAN NOT NULL DEFAULT false;
