CREATE TABLE trip_countries (
    trip_id      varchar(8) NOT NULL,
    country_code varchar(2) NOT NULL,

    CONSTRAINT pk_trip_countries
        PRIMARY KEY (trip_id, country_code),

    CONSTRAINT fk_trip_countries_trip
        FOREIGN KEY (trip_id)
        REFERENCES trips (id)
        ON DELETE CASCADE
);
