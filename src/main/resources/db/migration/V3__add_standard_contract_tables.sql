CREATE TABLE error_catalog (
    code VARCHAR(50) PRIMARY KEY,
    domain VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    http_status INTEGER NOT NULL,
    retriable BOOLEAN NOT NULL,
    next_action VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE reprocess_policy (
    id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(50) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    auto_max_attempts INTEGER NOT NULL DEFAULT 0,
    backoff_seconds INTEGER NOT NULL DEFAULT 0,
    approval_level VARCHAR(30) NOT NULL DEFAULT 'NONE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (error_code)
);

CREATE TABLE maintenance_window (
    id BIGSERIAL PRIMARY KEY,
    external_org VARCHAR(100) NOT NULL,
    day_of_week VARCHAR(12) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    suppress_level VARCHAR(20) NOT NULL DEFAULT 'WARN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reason VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_maintenance_window_org ON maintenance_window (external_org);

INSERT INTO error_catalog (code, domain, severity, http_status, retriable, next_action, description) VALUES
('IF-NET-001', 'NETWORK', 'ERROR', 504, TRUE, 'AUTO_RETRY', 'External call timeout'),
('IF-NET-002', 'NETWORK', 'ERROR', 502, TRUE, 'AUTO_RETRY', 'Network connection failure'),
('IF-EXT-001', 'EXTERNAL', 'ERROR', 502, TRUE, 'AUTO_RETRY_THEN_MANUAL', 'External server 5xx'),
('IF-EXT-002', 'EXTERNAL', 'WARN', 503, TRUE, 'DEFER_UNTIL_WINDOW_END', 'External maintenance window'),
('IF-VAL-001', 'VALIDATION', 'ERROR', 400, FALSE, 'FIX_DATA', 'Mandatory field is missing'),
('IF-VAL-002', 'VALIDATION', 'ERROR', 400, FALSE, 'FIX_DATA', 'Payload format is invalid'),
('IF-DAT-001', 'DATA', 'CRITICAL', 500, FALSE, 'COMPENSATE_WITH_APPROVAL', 'Data consistency check failed');

INSERT INTO reprocess_policy (error_code, mode, auto_max_attempts, backoff_seconds, approval_level, enabled) VALUES
('IF-NET-001', 'AUTO_RETRY', 3, 60, 'NONE', TRUE),
('IF-NET-002', 'AUTO_RETRY', 3, 60, 'NONE', TRUE),
('IF-EXT-001', 'MANUAL_REPROCESS', 2, 120, 'S2', TRUE),
('IF-VAL-001', 'NONE', 0, 0, 'NONE', TRUE),
('IF-VAL-002', 'NONE', 0, 0, 'NONE', TRUE),
('IF-DAT-001', 'COMPENSATE', 0, 0, 'S1_2MAN', TRUE);
