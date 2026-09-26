CREATE TABLE domain_records (
    kind VARCHAR(32) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    record_id VARCHAR(128) NOT NULL,
    payload_json CLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (kind, owner_id, record_id)
);

CREATE INDEX idx_domain_records_owner ON domain_records (owner_id, kind, updated_at);
