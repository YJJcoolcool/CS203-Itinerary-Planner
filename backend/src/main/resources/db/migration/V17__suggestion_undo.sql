-- CGT-169: an accepted suggestion can be undone.
--
-- accepted_location_id records the option the user actually applied (since V15
-- any ranked option can be chosen, not just proposed_location_id), so undo
-- knows which place to swap back out. UNDONE marks a reversed suggestion.
-- One statement per ALTER: H2 (used by FlywayMigrationTest) rejects the
-- comma-separated Postgres form.

ALTER TABLE suggestions ADD COLUMN accepted_location_id varchar(8);

ALTER TABLE suggestions
    ADD CONSTRAINT fk_suggestions_accepted_location FOREIGN KEY (accepted_location_id)
        REFERENCES location (id) ON DELETE SET NULL;

-- Rows accepted before this column existed most likely applied the proposed
-- place. If they applied a different option, undo safely refuses (409) because
-- the activity won't contain the proposed place.
UPDATE suggestions
SET accepted_location_id = proposed_location_id
WHERE status = 'ACCEPTED' AND accepted_location_id IS NULL;

ALTER TABLE suggestions DROP CONSTRAINT ck_suggestions_status;

ALTER TABLE suggestions
    ADD CONSTRAINT ck_suggestions_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'UNDONE'));
