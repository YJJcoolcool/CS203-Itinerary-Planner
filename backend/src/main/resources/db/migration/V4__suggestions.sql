-- Weather-driven alternative suggestions. V4 is reserved for suggestions; the weather
-- teammate's weather_events migration should take the next free version (V5).
-- Types mirror the @Entity mapping so ddl-auto=validate passes on Postgres; tests build
-- the schema from the entities on H2 (see planning/ai-integration-design.md §9).

CREATE TABLE suggestions (
    id                    varchar(8)                  NOT NULL,
    trip_id               varchar(8)                  NOT NULL,
    activity_id           varchar(8)                  NOT NULL,
    current_location_id   varchar(8),
    proposed_location_id  varchar(8)                  NOT NULL,
    status                varchar(16)                 NOT NULL,
    reason                varchar(255),
    explanation           varchar(1024),
    candidates            varchar(4096),
    ai_choice_id          varchar(8),
    top_score_choice_id   varchar(8),
    created_at            timestamp(6) with time zone NOT NULL DEFAULT now(),
    responded_at          timestamp(6) with time zone,
    CONSTRAINT pk_suggestions PRIMARY KEY (id),
    CONSTRAINT fk_suggestions_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_suggestions_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
    CONSTRAINT fk_suggestions_current_location FOREIGN KEY (current_location_id) REFERENCES location (id) ON DELETE SET NULL,
    CONSTRAINT fk_suggestions_proposed_location FOREIGN KEY (proposed_location_id) REFERENCES location (id),
    CONSTRAINT ck_suggestions_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX idx_suggestions_trip_created ON suggestions (trip_id, created_at);
CREATE INDEX idx_suggestions_activity_status ON suggestions (activity_id, status);
