CREATE TABLE policy_template (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(100) NOT NULL UNIQUE,
    auth_type VARCHAR(30) NOT NULL,
    timeout_millis BIGINT NOT NULL,
    retry_max_attempts INTEGER NOT NULL,
    retry_interval_millis BIGINT NOT NULL,
    rate_limit_per_minute INTEGER NOT NULL,
    allowed_partner_ids_json TEXT,
    mask_request_payload BOOLEAN NOT NULL DEFAULT TRUE,
    mask_response_payload BOOLEAN NOT NULL DEFAULT TRUE,
    allowed_roles_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE interface_policy_binding (
    id BIGSERIAL PRIMARY KEY,
    interface_definition_id BIGINT NOT NULL REFERENCES interface_definition(id),
    policy_template_id BIGINT NOT NULL REFERENCES policy_template(id),
    partner_id VARCHAR(100),
    priority INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_binding_if ON interface_policy_binding(interface_definition_id);
CREATE INDEX idx_policy_binding_partner ON interface_policy_binding(partner_id);

CREATE TABLE runtime_policy_snapshot (
    id BIGSERIAL PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL UNIQUE,
    interface_code VARCHAR(100) NOT NULL,
    partner_id VARCHAR(100),
    client_id VARCHAR(100),
    policy_name VARCHAR(100) NOT NULL,
    snapshot_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_runtime_policy_if ON runtime_policy_snapshot(interface_code);
CREATE INDEX idx_runtime_policy_created ON runtime_policy_snapshot(created_at DESC);

INSERT INTO policy_template (
    policy_name,
    auth_type,
    timeout_millis,
    retry_max_attempts,
    retry_interval_millis,
    rate_limit_per_minute,
    allowed_partner_ids_json,
    mask_request_payload,
    mask_response_payload,
    allowed_roles_json,
    enabled
) VALUES (
    'DEFAULT',
    'NONE',
    3000,
    0,
    0,
    1000,
    '[]',
    TRUE,
    TRUE,
    '[]',
    TRUE
);
