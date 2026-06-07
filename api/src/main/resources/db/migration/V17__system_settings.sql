-- Global, persistent key/value settings the admin dashboard can flip at
-- runtime. First use: maintenance_mode, which puts the user-facing web app
-- into a "temporarily down" state. Persisted (not in-memory) so the flag
-- survives API restarts/redeploys.
CREATE TABLE system_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(512) NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_settings (setting_key, setting_value) VALUES ('maintenance_mode', 'false');
