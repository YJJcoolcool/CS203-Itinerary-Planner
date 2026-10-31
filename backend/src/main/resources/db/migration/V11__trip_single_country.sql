ALTER TABLE trips ADD COLUMN country_code varchar(2);

UPDATE trips SET country_code = COALESCE(
    (SELECT MIN(tc.country_code) FROM trip_countries tc WHERE tc.trip_id = trips.id),
    'sg')
WHERE country_code IS NULL;

ALTER TABLE trips ALTER COLUMN country_code SET NOT NULL;
CREATE INDEX idx_trips_country_code ON trips (country_code);
