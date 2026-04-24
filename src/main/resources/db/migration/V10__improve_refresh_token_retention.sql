ALTER TABLE refresh_token
    ADD COLUMN revoked_at TIMESTAMP;

UPDATE refresh_token
SET revoked_at = created_at
WHERE revoked = TRUE
  AND revoked_at IS NULL;

CREATE INDEX idx_refresh_token_expires_at_revoked
    ON refresh_token (expires_at, revoked);

CREATE INDEX idx_refresh_token_revoked_at
    ON refresh_token (revoked_at);

CREATE TABLE archive_refresh_token (
    id           BIGINT          PRIMARY KEY,
    username     VARCHAR(100)    NOT NULL,
    token        VARCHAR(512)    NOT NULL UNIQUE,
    expires_at   TIMESTAMP       NOT NULL,
    revoked      BOOLEAN         NOT NULL,
    revoked_at   TIMESTAMP,
    created_at   TIMESTAMP       NOT NULL,
    archived_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_archive_refresh_token_archived_at
    ON archive_refresh_token (archived_at DESC);
