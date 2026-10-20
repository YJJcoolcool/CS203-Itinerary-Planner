-- Per-user travel preferences and per-trip group preferences.
--
-- User preferences are readable by other signed-in users (structured fields
-- only). A trip's group preference is the AI summary of its members plus the
-- trip's goal, and is regenerated lazily when it goes stale.

CREATE TABLE user_preference (
    user_id            varchar(8)   PRIMARY KEY,
    tags               varchar(255),
    max_walk_km        numeric(4,2),
    dietary            varchar(64),
    indoor_preference  varchar(16),
    notes              varchar(1024),
    summary            varchar(1024),
    summary_stale      boolean      NOT NULL DEFAULT true,
    updated_at         timestamp(6) with time zone NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preference_indoor
        CHECK (indoor_preference IN ('INDOOR', 'OUTDOOR', 'ANY')),
    CONSTRAINT ck_user_preference_walk CHECK (max_walk_km >= 0)
);

CREATE TABLE trip_preference (
    trip_id        varchar(8)   PRIMARY KEY,
    goals          varchar(255),
    tags           varchar(255),
    summary        varchar(1024),
    summary_stale  boolean      NOT NULL DEFAULT true,
    updated_by     varchar(8),
    updated_at     timestamp(6) with time zone NOT NULL DEFAULT now(),

    CONSTRAINT fk_trip_preference_trip FOREIGN KEY (trip_id)
        REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_preference_user FOREIGN KEY (updated_by)
        REFERENCES users (id) ON DELETE SET NULL
);
