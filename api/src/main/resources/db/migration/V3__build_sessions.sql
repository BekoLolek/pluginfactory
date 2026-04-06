CREATE TABLE build_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    workspace_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'CHATTING',
    current_phase VARCHAR(30) NOT NULL DEFAULT 'IDLE',
    complexity_score INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_build_sessions_user ON build_sessions(user_id);
CREATE INDEX idx_build_sessions_status ON build_sessions(status);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES build_sessions(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model_used VARCHAR(50),
    tokens_consumed INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_chat_messages_session ON chat_messages(session_id);

CREATE TABLE token_budgets (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES build_sessions(id),
    allocated_tokens INT NOT NULL,
    consumed_tokens INT NOT NULL DEFAULT 0,
    planning_tokens INT NOT NULL DEFAULT 0,
    implementation_tokens INT NOT NULL DEFAULT 0,
    testing_tokens INT NOT NULL DEFAULT 0,
    threshold_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
