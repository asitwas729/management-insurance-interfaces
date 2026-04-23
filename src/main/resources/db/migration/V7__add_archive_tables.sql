CREATE TABLE archive_execution_history (
    id               BIGINT          PRIMARY KEY,
    execution_id     VARCHAR(100)    NOT NULL UNIQUE,
    interface_code   VARCHAR(100)    NOT NULL,
    protocol_type    VARCHAR(30)     NOT NULL,
    trigger_type     VARCHAR(30)     NOT NULL,
    status           VARCHAR(30)     NOT NULL,
    started_at       TIMESTAMP       NOT NULL,
    ended_at         TIMESTAMP,
    latency_millis   BIGINT,
    request_payload  TEXT,
    response_payload TEXT,
    error_code       VARCHAR(100),
    error_message    VARCHAR(1000),
    archived_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_archive_exec_interface_code ON archive_execution_history (interface_code);
CREATE INDEX idx_archive_exec_started_at     ON archive_execution_history (started_at DESC);
CREATE INDEX idx_archive_exec_archived_at    ON archive_execution_history (archived_at DESC);

CREATE TABLE archive_audit_log (
    id            BIGINT          PRIMARY KEY,
    actor         VARCHAR(100)    NOT NULL,
    action        VARCHAR(100)    NOT NULL,
    target_type   VARCHAR(100)    NOT NULL,
    target_id     VARCHAR(200)    NOT NULL,
    before_state  TEXT,
    after_state   TEXT,
    created_at    TIMESTAMP       NOT NULL,
    archived_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_archive_audit_created_at ON archive_audit_log (created_at DESC);
