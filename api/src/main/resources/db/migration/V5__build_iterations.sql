CREATE TABLE build_iterations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    iteration_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    "trigger" VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_build_iterations_session ON build_iterations(session_id);

CREATE TABLE container_sessions (
    id UUID PRIMARY KEY,
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    container_id VARCHAR(100) NOT NULL,
    container_type VARCHAR(20) NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    memory_mb INT NOT NULL,
    cpu_millicores INT NOT NULL
);

CREATE TABLE build_errors (
    id UUID PRIMARY KEY,
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE artifacts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    iteration_id UUID NOT NULL REFERENCES build_iterations(id),
    jar_file_path VARCHAR(500),
    file_hash VARCHAR(64),
    file_size_bytes BIGINT,
    plugin_version VARCHAR(50),
    plugin_yml TEXT,
    security_passed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retention_expires_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE source_bundles (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL UNIQUE REFERENCES artifacts(id),
    source_zip_path VARCHAR(500),
    source_hash VARCHAR(64),
    source_size_bytes BIGINT,
    template_version VARCHAR(20),
    build_tool VARCHAR(20) NOT NULL DEFAULT 'MAVEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retention_expires_at TIMESTAMP WITH TIME ZONE
);
