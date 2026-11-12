ALTER TABLE activities
    ADD COLUMN departure_time_zone varchar(64),
    ADD COLUMN arrival_time_zone varchar(64);