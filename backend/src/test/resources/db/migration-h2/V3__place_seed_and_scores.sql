-- Offline place seed fields and precomputed place scores. Types mirror the @Entity mappings
-- so ddl-auto=validate passes on Postgres. Tests generate their schema from the entities on H2,
-- so open_intervals stays a varchar (not jsonb); see planning/ai-integration-design.md §4.

ALTER TABLE location ADD COLUMN category_group varchar(32);
ALTER TABLE location ADD COLUMN indoor_class varchar(16);
ALTER TABLE location ADD COLUMN indoor_source varchar(16);
ALTER TABLE location ADD COLUMN indoor_confidence numeric(3,2);
ALTER TABLE location ADD COLUMN est_cost numeric(10,2);
ALTER TABLE location ADD COLUMN open_intervals varchar(2048);
ALTER TABLE location ADD COLUMN seeded boolean NOT NULL DEFAULT false;

ALTER TABLE location
    ADD CONSTRAINT ck_location_indoor_class CHECK (indoor_class IN ('INDOOR', 'OUTDOOR', 'UNKNOWN'));
ALTER TABLE location
    ADD CONSTRAINT ck_location_indoor_source CHECK (indoor_source IN ('OSM_TAG', 'MANUAL', 'AI'));
ALTER TABLE location
    ADD CONSTRAINT ck_location_indoor_confidence CHECK (indoor_confidence >= 0 AND indoor_confidence <= 1);
ALTER TABLE location
    ADD CONSTRAINT ck_location_est_cost CHECK (est_cost >= 0);

-- Covers the candidate lookup: seeded rows in a category, filtered by indoor class.
CREATE INDEX idx_location_seeded_lookup ON location (seeded, category_group, indoor_class);

-- Intrinsic facts stay on location; our score lives here so its source and age stay visible
-- and it can be recomputed without touching the place record.
CREATE TABLE place_scores (
    location_id      varchar(8)                  NOT NULL,
    source           varchar(16)                 NOT NULL,
    quality_score    numeric(4,3)                NOT NULL,
    popularity_score numeric(4,3)                NOT NULL,
    scored_at        timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT pk_place_scores PRIMARY KEY (location_id, source),
    CONSTRAINT fk_place_scores_location FOREIGN KEY (location_id) REFERENCES location (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_scores_source CHECK (source IN ('MANUAL', 'RULE')),
    CONSTRAINT ck_place_scores_quality CHECK (quality_score >= 0 AND quality_score <= 1),
    CONSTRAINT ck_place_scores_popularity CHECK (popularity_score >= 0 AND popularity_score <= 1)
);
