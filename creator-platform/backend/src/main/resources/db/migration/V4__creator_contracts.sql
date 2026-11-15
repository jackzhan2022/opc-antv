CREATE TABLE contract_template (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    filename VARCHAR(200) NOT NULL,
    file_type VARCHAR(8) NOT NULL,
    source_body LONGBLOB NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    remote_id VARCHAR(100),
    remote_environment VARCHAR(10),
    remote_json MEDIUMTEXT,
    conversion_task VARCHAR(100),
    resource_id VARCHAR(100),
    resource_deadline BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE creator_contract (
    id VARCHAR(36) PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    template_remote_id VARCHAR(100) NOT NULL,
    template_snapshot MEDIUMTEXT NOT NULL,
    ess_environment VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    legal_name VARCHAR(100) NOT NULL,
    mobile VARCHAR(20) NOT NULL,
    creator_snapshot MEDIUMTEXT NOT NULL,
    field_values MEDIUMTEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    flow_id VARCHAR(100),
    launch_token VARCHAR(36) NOT NULL,
    deadline BIGINT NOT NULL,
    company_snapshot MEDIUMTEXT,
    launch_stage VARCHAR(30),
    error_code VARCHAR(50),
    preview_flow_id VARCHAR(100),
    preview_url TEXT,
    preview_deadline BIGINT,
    archived_pdf LONGBLOB,
    version BIGINT NOT NULL DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE(flow_id)
);
CREATE INDEX ix_contract_creator ON creator_contract(creator_id,created_at,id);
CREATE INDEX ix_contract_status ON creator_contract(status,updated_at);
CREATE TABLE contract_audit (
    id VARCHAR(36) PRIMARY KEY,
    contract_id VARCHAR(36),
    template_id VARCHAR(36),
    actor_id VARCHAR(100) NOT NULL,
    actor_name VARCHAR(200) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE contract_callback (
    msg_id VARCHAR(100) PRIMARY KEY,
    msg_type VARCHAR(100) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL
);
