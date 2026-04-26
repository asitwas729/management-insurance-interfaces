CREATE TABLE interface_resilience_policy (
    interface_code              VARCHAR(100) PRIMARY KEY
                                    REFERENCES interface_definition(interface_code),
    cb_failure_rate_threshold   FLOAT       NOT NULL DEFAULT 50.0,
    cb_sliding_window           INTEGER     NOT NULL DEFAULT 20,
    rate_limit_per_second       INTEGER     NOT NULL DEFAULT 50,
    timeout_millis              BIGINT      NOT NULL DEFAULT 3000,
    retry_max_attempts          INTEGER     NOT NULL DEFAULT 0,
    updated_at                  TIMESTAMP   NOT NULL DEFAULT now()
);
