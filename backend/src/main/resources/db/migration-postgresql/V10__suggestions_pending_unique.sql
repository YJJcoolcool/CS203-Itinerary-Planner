-- Postgres-only: partial indexes are not supported on H2, hence the vendor
-- folder. This is the database-level guarantee behind the check-then-insert in
-- SuggestionService; a violation is caught by the poller's per-trip try/catch
-- and rolls back that trip, so no duplicate is written.

-- Keep only the newest PENDING row per activity. created_at decides; id breaks
-- ties so the statement is deterministic even for rows created in one batch.
DELETE FROM suggestions target
USING suggestions duplicate
WHERE target.status = 'PENDING'
  AND duplicate.status = 'PENDING'
  AND target.activity_id = duplicate.activity_id
  AND (target.created_at < duplicate.created_at
       OR (target.created_at = duplicate.created_at AND target.id < duplicate.id));

CREATE UNIQUE INDEX uq_suggestions_pending_activity
    ON suggestions (activity_id)
    WHERE status = 'PENDING';
