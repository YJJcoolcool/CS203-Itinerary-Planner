-- Proactive polling (M4b): how a suggestion was created and whether it has been
-- notified. Types mirror the @Entity mapping so ddl-auto=validate passes on
-- Postgres; tests build the schema from the entities on H2 (see V4__suggestions.sql).

ALTER TABLE suggestions
    ADD COLUMN trigger_source varchar(16) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE suggestions
    ADD COLUMN notified_at timestamp(6) with time zone;

-- Serves both the PENDING suppression check and the 6h REJECTED cooldown.
CREATE INDEX idx_suggestions_activity_status_responded
    ON suggestions (activity_id, status, responded_at);
