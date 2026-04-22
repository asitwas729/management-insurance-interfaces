ALTER TABLE interface_definition
    ADD COLUMN external_org VARCHAR(100);

UPDATE interface_definition
SET external_org = 'UNKNOWN'
WHERE external_org IS NULL;

ALTER TABLE interface_definition
    ALTER COLUMN external_org SET NOT NULL;
