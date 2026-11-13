CREATE TABLE creator_dictionary_guard (
    id INT PRIMARY KEY
);
INSERT INTO creator_dictionary_guard(id) VALUES (1);

CREATE TABLE creator_dictionary (
    id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_creator_dictionary_kind_name UNIQUE(kind,name),
    CONSTRAINT ck_creator_dictionary_kind CHECK (kind IN ('PROFESSION','TAG','SOURCE'))
);
CREATE INDEX ix_creator_dictionary_kind_enabled ON creator_dictionary(kind,enabled);

CREATE TABLE creator_dictionary_audit (
    id VARCHAR(36) PRIMARY KEY,
    dictionary_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY(dictionary_id) REFERENCES creator_dictionary(id)
);
CREATE INDEX ix_creator_dictionary_audit ON creator_dictionary_audit(dictionary_id,created_at,id);
