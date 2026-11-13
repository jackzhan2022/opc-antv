ALTER TABLE platform_member ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_member MODIFY COLUMN last_login_at TIMESTAMP NULL DEFAULT NULL;
ALTER TABLE external_identity ADD COLUMN id VARCHAR(36);
ALTER TABLE external_identity ADD COLUMN display_name VARCHAR(200);
ALTER TABLE external_identity ADD COLUMN linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE TABLE pending_identity (
 id VARCHAR(36) PRIMARY KEY,
 provider VARCHAR(20) NOT NULL,
 tenant_id VARCHAR(128) NOT NULL,
 subject_id VARCHAR(128) NOT NULL,
 display_name VARCHAR(200) NOT NULL,
 first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_pending_identity UNIQUE(provider, tenant_id, subject_id)
);
-- V1 registered users automatically. Keep member records and require explicit identity approval.
INSERT INTO pending_identity(id,provider,tenant_id,subject_id,display_name)
 SELECT UUID(), e.provider, e.tenant_id, e.subject_id, m.display_name
 FROM external_identity e JOIN platform_member m ON m.id=e.member_id;
DELETE FROM external_identity;
ALTER TABLE external_identity MODIFY COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE external_identity ADD CONSTRAINT uq_identity_id UNIQUE(id);
ALTER TABLE external_identity ADD CONSTRAINT uq_member_provider UNIQUE(member_id,provider,tenant_id);
CREATE TABLE member_admin_guard (
 id INTEGER PRIMARY KEY,
 bootstrap_completed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO member_admin_guard(id,bootstrap_completed) VALUES (1,FALSE);
CREATE TABLE member_audit (
 id VARCHAR(36) PRIMARY KEY,
 actor_id VARCHAR(36),
 actor_name VARCHAR(200) NOT NULL,
 action VARCHAR(40) NOT NULL,
 target_id VARCHAR(36) NOT NULL,
 target_name VARCHAR(200) NOT NULL,
 detail VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_created ON member_audit(created_at,id);
CREATE INDEX idx_pending_seen ON pending_identity(last_seen_at,id);
