-- Source-only schema: contains no private values. Flyway runs only in the fixed sai_personal schema.
-- Aggregate payloads retain typed application validation. Origin distinguishes public defaults from user content.
CREATE TABLE sai_agents (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_models (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_model_settings (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_provider_configurations (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_user_rules (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_knowledge_sources (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_writing_drafts (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_writing_presets (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_usage (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_conversations (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sai_provider_permissions (
    id VARCHAR(160) PRIMARY KEY,
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    origin VARCHAR(16) NOT NULL DEFAULT 'user' CHECK (origin IN ('user', 'synthetic')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

