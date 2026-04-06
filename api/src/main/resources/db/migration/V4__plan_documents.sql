CREATE TABLE plan_documents (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES build_sessions(id),
    plugin_name VARCHAR(255),
    description TEXT,
    minecraft_version VARCHAR(20),
    server_type VARCHAR(20),
    commands TEXT NOT NULL DEFAULT '[]',
    event_listeners TEXT NOT NULL DEFAULT '[]',
    config_schema TEXT NOT NULL DEFAULT '[]',
    dependencies TEXT NOT NULL DEFAULT '[]',
    test_scenarios TEXT NOT NULL DEFAULT '[]',
    estimated_loc INT,
    complexity_score INT,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
