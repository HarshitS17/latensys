ALTER TABLE api_request_logs ADD COLUMN IF NOT EXISTS decision VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_request_logs_timestamp
    ON api_request_logs (timestamp);
