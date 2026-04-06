-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    auth_provider VARCHAR(50) NOT NULL,
    discord_id VARCHAR(100) UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_active_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_discord_id ON users(discord_id);

-- Tiers reference table
CREATE TABLE tiers (
    name VARCHAR(20) PRIMARY KEY,
    max_builds INT NOT NULL,
    token_budget INT NOT NULL,
    max_parallel INT NOT NULL,
    max_iterations INT NOT NULL,
    max_commands INT NOT NULL,
    max_event_listeners INT NOT NULL,
    jar_retention_days INT NOT NULL,
    marketplace_slots INT NOT NULL,
    source_code_access BOOLEAN NOT NULL DEFAULT FALSE,
    price_monthly_cents INT NOT NULL DEFAULT 0
);

INSERT INTO tiers (name, max_builds, token_budget, max_parallel, max_iterations, max_commands, max_event_listeners, jar_retention_days, marketplace_slots, source_code_access, price_monthly_cents) VALUES
    ('FREE', 1, 50000, 0, 0, 5, 3, 7, 0, FALSE, 0),
    ('BASIC', 5, 200000, 0, 2, 15, 10, 30, 1, FALSE, 999),
    ('PRO', 20, 500000, 5, 5, -1, -1, 90, 5, TRUE, 2999),
    ('TEAM', -1, 1000000, 20, -1, -1, -1, -1, -1, TRUE, 7999);

-- Subscriptions table
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    builds_used_this_period INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_subscriptions_user_id UNIQUE (user_id)
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);

-- API keys table
CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    key_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    last_four VARCHAR(4) NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX idx_api_keys_key_hash ON api_keys(key_hash);
