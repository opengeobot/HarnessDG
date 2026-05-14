ALTER TABLE ont_dimension
    ADD COLUMN IF NOT EXISTS entity_id BIGINT REFERENCES ont_entity(id);

CREATE INDEX IF NOT EXISTS idx_dimension_entity
    ON ont_dimension(entity_id)
    WHERE is_deleted = FALSE;
