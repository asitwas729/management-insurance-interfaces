CREATE INDEX IF NOT EXISTS idx_archive_exec_started_at
    ON archive_execution_history (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_archive_audit_created_at
    ON archive_audit_log (created_at DESC);
