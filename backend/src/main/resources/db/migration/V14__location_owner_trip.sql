-- CGT-142: manually entered places belong to the trip they were created in.
-- Shared places (seeded or OpenStreetMap) keep trip_id NULL and stay visible to everyone.
-- Deleting a trip deletes its private places too.

ALTER TABLE location ADD COLUMN trip_id varchar(8);

ALTER TABLE location
    ADD CONSTRAINT fk_location_trip FOREIGN KEY (trip_id)
        REFERENCES trips (id) ON DELETE CASCADE;

CREATE INDEX idx_location_trip_id ON location (trip_id);

-- Backfill existing manual places: one used by activities of exactly one trip
-- now belongs to that trip. Manual places used by no trip, or by several,
-- stay unowned: hidden from lists, but still working where they are used.
UPDATE location
SET trip_id = (
    SELECT MIN(a.trip_id)
    FROM activity_locations al
    JOIN activities a ON a.id = al.activity_id
    WHERE al.location_id = location.id
)
WHERE seeded = false
  AND osm_id IS NULL
  AND trip_id IS NULL
  AND (
    SELECT COUNT(DISTINCT a.trip_id)
    FROM activity_locations al
    JOIN activities a ON a.id = al.activity_id
    WHERE al.location_id = location.id
  ) = 1;
