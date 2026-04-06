CREATE TABLE source_code_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    artifact_id UUID NOT NULL REFERENCES artifacts(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    license_version VARCHAR(20) NOT NULL,
    license_accepted_at TIMESTAMP WITH TIME ZONE,
    license_accepted_ip VARCHAR(45),
    watermark_id UUID NOT NULL,
    download_path VARCHAR(500),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fulfilled_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_source_code_requests_user ON source_code_requests(user_id);
CREATE INDEX idx_source_code_requests_artifact ON source_code_requests(artifact_id);
