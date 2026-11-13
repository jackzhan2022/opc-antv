ALTER TABLE creator_record ADD COLUMN intro TEXT NULL;
ALTER TABLE creator_record ADD COLUMN quote_amount DECIMAL(12,2) NULL;
ALTER TABLE creator_record ADD COLUMN quote_unit VARCHAR(10) NOT NULL DEFAULT '';
CREATE INDEX ix_creator_maintainer ON creator_record(maintainer_id);
CREATE INDEX ix_creator_source ON creator_record(source);
