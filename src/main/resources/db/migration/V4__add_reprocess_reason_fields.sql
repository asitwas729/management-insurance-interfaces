ALTER TABLE retry_task
    ADD COLUMN request_reason_code VARCHAR(100);

ALTER TABLE retry_task
    ADD COLUMN request_reason_detail VARCHAR(1000);

UPDATE retry_task
SET request_reason_code = 'UNSPECIFIED'
WHERE request_reason_code IS NULL;

ALTER TABLE retry_task
    ALTER COLUMN request_reason_code SET NOT NULL;

ALTER TABLE dlq_replay_request
    ADD COLUMN request_reason_code VARCHAR(100);

ALTER TABLE dlq_replay_request
    ADD COLUMN request_reason_detail VARCHAR(1000);

UPDATE dlq_replay_request
SET request_reason_code = 'UNSPECIFIED'
WHERE request_reason_code IS NULL;

ALTER TABLE dlq_replay_request
    ALTER COLUMN request_reason_code SET NOT NULL;
