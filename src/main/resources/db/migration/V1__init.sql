-- ============================================================
-- V1__init.sql
-- 보험사 금융 IT 인터페이스 통합관리시스템 초기 스키마
-- PostgreSQL 기준
-- ============================================================

-- 1. 인터페이스 정의
CREATE TABLE interface_definition (
    id              BIGSERIAL       PRIMARY KEY,
    interface_code  VARCHAR(100)    NOT NULL UNIQUE,
    name            VARCHAR(200)    NOT NULL,
    protocol_type   VARCHAR(30)     NOT NULL,
    owner_team      VARCHAR(100)    NOT NULL,
    sla_millis      BIGINT,
    status          VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

-- 2. 인터페이스 설정 버전
CREATE TABLE interface_config_version (
    id                      BIGSERIAL       PRIMARY KEY,
    interface_definition_id BIGINT          NOT NULL REFERENCES interface_definition(id),
    version                 INTEGER         NOT NULL,
    endpoint                VARCHAR(1000)   NOT NULL,
    auth_type               VARCHAR(50),
    headers_json            TEXT,
    timeout_millis          BIGINT          NOT NULL,
    published               BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       NOT NULL DEFAULT now(),
    UNIQUE (interface_definition_id, version)
);

-- 3. 실행 이력
CREATE TABLE execution_history (
    id               BIGSERIAL       PRIMARY KEY,
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
    error_message    VARCHAR(1000)
);

CREATE INDEX idx_exec_hist_interface_code ON execution_history (interface_code);
CREATE INDEX idx_exec_hist_started_at     ON execution_history (started_at DESC);
CREATE INDEX idx_exec_hist_status         ON execution_history (status);

-- 4. 멱등성 레코드
CREATE TABLE idempotency_record (
    id               BIGSERIAL       PRIMARY KEY,
    idempotency_key  VARCHAR(200)    NOT NULL UNIQUE,
    interface_code   VARCHAR(100)    NOT NULL,
    execution_id     VARCHAR(100)    NOT NULL,
    status           VARCHAR(30)     NOT NULL DEFAULT 'RESERVED',
    created_at       TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT now()
);

-- 5. 재처리 태스크
CREATE TABLE retry_task (
    id                    BIGSERIAL       PRIMARY KEY,
    interface_definition_id BIGINT        NOT NULL REFERENCES interface_definition(id),
    original_execution_id VARCHAR(100)    NOT NULL,
    status                VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    requester             VARCHAR(100)    NOT NULL,
    approver              VARCHAR(100),
    reject_reason         VARCHAR(1000),
    created_at            TIMESTAMP       NOT NULL DEFAULT now(),
    approved_at           TIMESTAMP,
    executed_at           TIMESTAMP
);

CREATE INDEX idx_retry_task_status ON retry_task (status);

-- 6. 감사 로그
CREATE TABLE audit_log (
    id           BIGSERIAL   PRIMARY KEY,
    actor        VARCHAR(100) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    target_type  VARCHAR(100) NOT NULL,
    target_id    VARCHAR(100) NOT NULL,
    before_value TEXT,
    after_value  TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_action      ON audit_log (action);
CREATE INDEX idx_audit_log_actor       ON audit_log (actor);
CREATE INDEX idx_audit_log_created_at  ON audit_log (created_at DESC);

-- 7. DLQ 메시지
CREATE TABLE dlq_message (
    id               BIGSERIAL       PRIMARY KEY,
    interface_code   VARCHAR(100)    NOT NULL,
    topic            VARCHAR(500)    NOT NULL,
    payload          TEXT            NOT NULL,
    reason           VARCHAR(1000)   NOT NULL,
    replay_count     INTEGER         NOT NULL DEFAULT 0,
    last_replayed_at TIMESTAMP,
    created_at       TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_dlq_message_interface_code ON dlq_message (interface_code);

-- 8. DLQ 재처리 요청
CREATE TABLE dlq_replay_request (
    id                   BIGSERIAL   PRIMARY KEY,
    dlq_message_id       BIGINT      NOT NULL REFERENCES dlq_message(id),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requester            VARCHAR(100) NOT NULL,
    approver             VARCHAR(100),
    reject_reason        VARCHAR(1000),
    payload_override_json TEXT,
    approved_at          TIMESTAMP,
    executed_at          TIMESTAMP,
    created_at           TIMESTAMP   NOT NULL DEFAULT now()
);

-- 9. 앱 사용자
CREATE TABLE app_user (
    id            BIGSERIAL       PRIMARY KEY,
    username      VARCHAR(100)    NOT NULL UNIQUE,
    password_hash VARCHAR(200)    NOT NULL,
    roles         VARCHAR(200)    NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT now()
);
