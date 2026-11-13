CREATE TABLE creator_record (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(64) NOT NULL,
    wechat VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    source VARCHAR(200) NOT NULL,
    maintainer_id VARCHAR(100) NOT NULL,
    maintainer_name VARCHAR(200) NOT NULL,
    notes TEXT NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX ix_creator_status_updated ON creator_record(archived, updated_at, id);
CREATE INDEX ix_creator_city ON creator_record(city);
CREATE TABLE creator_profession (
    creator_id VARCHAR(36) NOT NULL,
    position_no INT NOT NULL,
    profession VARCHAR(50) NOT NULL,
    PRIMARY KEY(creator_id, position_no),
    FOREIGN KEY(creator_id) REFERENCES creator_record(id)
);
CREATE INDEX ix_creator_profession ON creator_profession(profession, creator_id);
CREATE TABLE creator_tag (
    creator_id VARCHAR(36) NOT NULL,
    position_no INT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY(creator_id, position_no),
    FOREIGN KEY(creator_id) REFERENCES creator_record(id)
);
CREATE INDEX ix_creator_tag ON creator_tag(tag, creator_id);
CREATE TABLE creator_work (
    creator_id VARCHAR(36) NOT NULL,
    position_no INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    PRIMARY KEY(creator_id, position_no),
    FOREIGN KEY(creator_id) REFERENCES creator_record(id)
);
CREATE TABLE creator_audit (
    id VARCHAR(36) PRIMARY KEY,
    creator_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_name VARCHAR(200) NOT NULL,
    action VARCHAR(20) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(creator_id) REFERENCES creator_record(id)
);
CREATE INDEX ix_creator_audit ON creator_audit(creator_id, created_at, id);
