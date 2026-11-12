ALTER TABLE activities
    ADD COLUMN activity_type varchar(16) NOT NULL DEFAULT 'ACTIVITY';

ALTER TABLE activities
    ADD COLUMN flight_number varchar(20);

ALTER TABLE activities
    ADD COLUMN flight_date date;

ALTER TABLE activities
    ADD COLUMN airline varchar(120);

ALTER TABLE activities
    ADD COLUMN departure_airport_code varchar(8);

ALTER TABLE activities
    ADD COLUMN departure_airport_name varchar(160);

ALTER TABLE activities
    ADD COLUMN departure_terminal varchar(32);

ALTER TABLE activities
    ADD COLUMN arrival_airport_code varchar(8);

ALTER TABLE activities
    ADD COLUMN arrival_airport_name varchar(160);

ALTER TABLE activities
    ADD COLUMN arrival_terminal varchar(32);

ALTER TABLE activities
    ADD CONSTRAINT ck_activities_type
    CHECK (activity_type IN ('ACTIVITY', 'FLIGHT'));
