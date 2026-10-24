-- Cleanup after proactive polling (M4b). Common to every database: no vendor
-- syntax is used, and every statement is safe to re-run on a fresh database.

-- 1. The old (activity_id, status) index is a prefix of the new, wider
--    (activity_id, status, responded_at) index, so keeping both only costs writes.
DROP INDEX IF EXISTS idx_suggestions_activity_status;

-- 2. notified_at was never populated or consumed; the entity dropped the field.
ALTER TABLE suggestions DROP COLUMN IF EXISTS notified_at;

-- 3. Trigger values were renamed to describe the creation horizon.
UPDATE suggestions SET trigger_source = 'POLL_IMMINENT'
    WHERE trigger_source = 'POLL_2H';

UPDATE suggestions SET trigger_source = 'POLL_TOMORROW'
    WHERE trigger_source = 'POLL_24H';
