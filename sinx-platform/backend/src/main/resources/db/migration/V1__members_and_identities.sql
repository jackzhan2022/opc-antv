CREATE TABLE platform_member (
 id VARCHAR(36) PRIMARY KEY,
 display_name VARCHAR(200) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 platform_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE external_identity (
 provider VARCHAR(20) NOT NULL,
 tenant_id VARCHAR(128) NOT NULL,
 subject_id VARCHAR(128) NOT NULL,
 member_id VARCHAR(36) NOT NULL,
 PRIMARY KEY (provider, tenant_id, subject_id),
 CONSTRAINT fk_identity_member FOREIGN KEY (member_id) REFERENCES platform_member(id)
);
