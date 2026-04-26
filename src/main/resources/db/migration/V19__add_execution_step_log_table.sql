-- V19__add_execution_step_log_table.sql
CREATE TABLE execution_step_log (
    id              BIGSERIAL       PRIMARY KEY,
    execution_id    VARCHAR(100)    NOT NULL,
    step_order      INTEGER         NOT NULL,
    step_name       VARCHAR(100)    NOT NULL,
    step_type       VARCHAR(50)     NOT NULL,
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP,
    elapsed_ms      BIGINT,
    step_status     VARCHAR(30)     NOT NULL,
    error_message   TEXT,
    CONSTRAINT fk_step_log_execution FOREIGN KEY (execution_id) REFERENCES execution_history(execution_id)
);

CREATE INDEX idx_step_log_execution_id ON execution_step_log (execution_id);
CREATE INDEX idx_step_log_step_order   ON execution_step_log (execution_id, step_order);
