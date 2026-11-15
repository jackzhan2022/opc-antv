CREATE TABLE platform_application (
 id VARCHAR(36) PRIMARY KEY,
 code VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL,
 description VARCHAR(500) NOT NULL,
 entry_url VARCHAR(2048) NOT NULL,
 integration_type VARCHAR(12) NOT NULL,
 icon VARCHAR(20) NOT NULL,
 sort_order INTEGER NOT NULL DEFAULT 0,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 1,
 access_version BIGINT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE member_application_access (
 application_id VARCHAR(36) NOT NULL,
 member_id VARCHAR(36) NOT NULL,
 granted BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(application_id,member_id),
 FOREIGN KEY(application_id) REFERENCES platform_application(id) ON DELETE CASCADE,
 FOREIGN KEY(member_id) REFERENCES platform_member(id) ON DELETE CASCADE
);
CREATE INDEX idx_member_application_access ON member_application_access(member_id,granted);
CREATE TABLE application_audit (
 id VARCHAR(36) PRIMARY KEY,
 actor_id VARCHAR(36),
 actor_name VARCHAR(200) NOT NULL,
 action VARCHAR(40) NOT NULL,
 application_id VARCHAR(36) NOT NULL,
 application_name VARCHAR(100) NOT NULL,
 member_name VARCHAR(200),
 detail VARCHAR(500) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_application_audit_created ON application_audit(created_at,id);
INSERT INTO platform_application(id,code,name,description,entry_url,integration_type,icon,sort_order)
 VALUES ('creator-platform','creator-platform','创作者管理','创作者档案与作品资料','','SSO','users',0);
-- Preserve existing enabled members' access once. Future members require an explicit grant.
INSERT INTO member_application_access(application_id,member_id,granted)
 SELECT 'creator-platform',id,TRUE FROM platform_member WHERE enabled=TRUE;
INSERT INTO application_audit(id,actor_name,action,application_id,application_name,detail)
 VALUES ('application-access-migration-v4','系统迁移','ACCESS_MIGRATED','creator-platform','创作者管理','升级时保留既有启用成员的创作者访问权限，新成员需显式授权');
