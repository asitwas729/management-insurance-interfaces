ALTER TABLE interface_config_version
    ADD COLUMN sandbox_mode BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE interface_config_version
    ADD COLUMN mock_http_status INTEGER;

ALTER TABLE interface_config_version
    ADD COLUMN mock_response_body TEXT;
