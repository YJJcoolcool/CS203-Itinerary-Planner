ALTER TABLE activities
    ADD COLUMN departure_time_zone varchar(64);

ALTER TABLE activities
    ADD COLUMN arrival_time_zone varchar(64);