CREATE TABLE incident_summary (
    id BIGSERIAL PRIMARY KEY,
    summary_text TEXT NOT NULL,
    analyzed_count INT NOT NULL,
    hours_back INT NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_summary_generated_at ON incident_summary(generated_at);
