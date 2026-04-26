CREATE TABLE standard_message_schema (
    schema_code  VARCHAR(100) NOT NULL,
    version      INTEGER      NOT NULL,
    xsd_text     TEXT         NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (schema_code, version)
);

CREATE TABLE standard_message_rule (
    schema_code     VARCHAR(100) NOT NULL,
    version         INTEGER      NOT NULL,
    rule_id         VARCHAR(100) NOT NULL,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'ERROR',
    xpath_expr      VARCHAR(500) NOT NULL,
    operator        VARCHAR(30)  NOT NULL,
    expected_value  VARCHAR(500),
    message         VARCHAR(500),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (schema_code, version, rule_id),
    CONSTRAINT fk_standard_message_rule_schema
        FOREIGN KEY (schema_code, version)
        REFERENCES standard_message_schema(schema_code, version)
);

CREATE INDEX idx_standard_message_rule_schema ON standard_message_rule (schema_code, version);
