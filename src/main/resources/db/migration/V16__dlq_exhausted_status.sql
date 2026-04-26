-- Add status column for DLQ message lifecycle (PENDING/REPLAYED/EXHAUSTED)
-- NOTE: V9 is already used in this repo, so this change is introduced as V16.

ALTER TABLE dlq_message
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE dlq_message
   SET status = 'REPLAYED'
 WHERE replay_count > 0
   AND status = 'PENDING';

