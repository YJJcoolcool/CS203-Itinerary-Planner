ALTER TABLE activities
    ADD COLUMN activity_type varchar(16)
        NOT NULL DEFAULT 'ACTIVITY',

    ADD COLUMN flight_number varchar(20),
    ADD COLUMN flight_date date,
    ADD COLUMN airline varchar(120),

    ADD COLUMN departure_airport_code varchar(8),
    ADD COLUMN departure_airport_name varchar(160),
    ADD COLUMN departure_terminal varchar(32),

    ADD COLUMN arrival_airport_code varchar(8),
    ADD COLUMN arrival_airport_name varchar(160),
    ADD COLUMN arrival_terminal varchar(32);

ALTER TABLE activities
    ADD CONSTRAINT ck_activities_type
    CHECK (activity_type IN ('ACTIVITY', 'FLIGHT'));