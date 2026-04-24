ALTER TABLE interface_definition
    ADD COLUMN business_category VARCHAR(100);
ALTER TABLE interface_definition
    ADD COLUMN call_direction VARCHAR(30);

UPDATE interface_definition
SET business_category = 'GENERAL'
WHERE business_category IS NULL;

UPDATE interface_definition
SET call_direction = 'OUTBOUND'
WHERE call_direction IS NULL;

ALTER TABLE interface_definition
    ALTER COLUMN business_category SET NOT NULL;
ALTER TABLE interface_definition
    ALTER COLUMN call_direction SET NOT NULL;

ALTER TABLE interface_config_version
    ADD COLUMN environment VARCHAR(20);
ALTER TABLE interface_config_version
    ADD COLUMN protocol_config_json TEXT;
ALTER TABLE interface_config_version
    ADD COLUMN request_sample TEXT;
ALTER TABLE interface_config_version
    ADD COLUMN response_sample TEXT;
ALTER TABLE interface_config_version
    ADD COLUMN mapping_rule_text TEXT;
ALTER TABLE interface_config_version
    ADD COLUMN field_description_text TEXT;
ALTER TABLE interface_config_version
    ADD COLUMN error_code_guide_text TEXT;

UPDATE interface_config_version
SET environment = 'PROD'
WHERE environment IS NULL;

ALTER TABLE interface_config_version
    ALTER COLUMN environment SET NOT NULL;
