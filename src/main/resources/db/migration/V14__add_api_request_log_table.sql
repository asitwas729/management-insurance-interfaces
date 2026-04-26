CREATE TABLE api_request_log (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(100),
    source VARCHAR(30) NOT NULL DEFAULT 'UI',
    method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    request_body TEXT,
    response_status INTEGER,
    response_body TEXT,
    error_message TEXT,
    duration_ms BIGINT,
    occurred_at TIMESTAMP NOT NULL DEFAULT now(),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_request_log_created ON api_request_log(created_at DESC);
CREATE INDEX idx_api_request_log_path ON api_request_log(path);
