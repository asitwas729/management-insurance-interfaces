-- V18__enhance_execution_history_for_summary_log.sql
ALTER TABLE execution_history ADD COLUMN interface_name  VARCHAR(200);
ALTER TABLE execution_history ADD COLUMN source_system   VARCHAR(100);
ALTER TABLE execution_history ADD COLUMN target_system   VARCHAR(100);
ALTER TABLE execution_history ADD COLUMN partner_name    VARCHAR(100);
ALTER TABLE execution_history ADD COLUMN retry_count     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE execution_history ADD COLUMN error_category  VARCHAR(50);

CREATE INDEX idx_exec_hist_error_category ON execution_history (error_category);
