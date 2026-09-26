CREATE TABLE model_profiles (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    name VARCHAR(80) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model_id VARCHAR(160) NOT NULL,
    secret_cipher CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_model_profiles_owner ON model_profiles (owner_id, created_at);

CREATE TABLE model_routes (
    owner_id VARCHAR(128) NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    profile_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (owner_id, purpose),
    FOREIGN KEY (profile_id) REFERENCES model_profiles(id) ON DELETE CASCADE
);
