-- Initial schema, generated from the @Entity classes (PostgreSQLDialect) and tidied by hand.
-- Column types match what Hibernate expects so ddl-auto=validate passes. See ../decisions.md.

CREATE TABLE users (
    id            varchar(8)   NOT NULL,
    clerk_user_id varchar(255) NOT NULL,
    display_name  varchar(255),
    email         varchar(255),
    role          varchar(255) NOT NULL DEFAULT 'USER',
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_clerk_user_id UNIQUE (clerk_user_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE trips (
    id         varchar(8) NOT NULL,
    name       varchar(255),
    start_date date,
    end_date   date,
    owner_id   varchar(8) NOT NULL,
    CONSTRAINT pk_trips PRIMARY KEY (id),
    CONSTRAINT fk_trips_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

-- Hibernate maps this List as a bag with no key; the PK just blocks duplicate memberships.
CREATE TABLE trip_members (
    trip_id varchar(8) NOT NULL,
    user_id varchar(8) NOT NULL,
    CONSTRAINT pk_trip_members PRIMARY KEY (trip_id, user_id),
    CONSTRAINT fk_trip_members_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_trip_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE trip_member_roles (
    trip_id varchar(8)   NOT NULL,
    user_id varchar(8)   NOT NULL,
    role    varchar(255) NOT NULL,
    CONSTRAINT pk_trip_member_roles PRIMARY KEY (trip_id, user_id),
    CONSTRAINT fk_trip_member_roles_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT ck_trip_member_roles_role CHECK (role IN ('VIEWER', 'EDITOR', 'OWNER'))
);

CREATE TABLE trip_invitations (
    id           varchar(8)                  NOT NULL,
    trip_id      varchar(8)                  NOT NULL,
    sender_id    varchar(8)                  NOT NULL,
    receiver_id  varchar(8)                  NOT NULL,
    status       varchar(255)                NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    responded_at timestamp(6) with time zone,
    CONSTRAINT pk_trip_invitations PRIMARY KEY (id),
    CONSTRAINT fk_trip_invitations_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_trip_invitations_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_trip_invitations_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT ck_trip_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

-- Table name comes from the class name (Location has no @Table name).
CREATE TABLE location (
    id                 varchar(8)   NOT NULL,
    name               varchar(255) NOT NULL,
    category           varchar(255),
    latitude           float(53)    NOT NULL,
    longitude          float(53)    NOT NULL,
    is_indoor          boolean,
    osm_type           varchar(255),
    osm_id             bigint,
    address            varchar(255),
    opening_hours      varchar(255),
    parent_location_id varchar(8),
    CONSTRAINT pk_location PRIMARY KEY (id),
    CONSTRAINT uq_location_osm_identity UNIQUE (osm_type, osm_id),
    CONSTRAINT fk_location_parent FOREIGN KEY (parent_location_id) REFERENCES location (id)
);

CREATE TABLE activities (
    id              varchar(8)                  NOT NULL,
    name            varchar(255),
    start_date_time timestamp(6) with time zone,
    end_date_time   timestamp(6) with time zone,
    status          varchar(255),
    trip_id         varchar(8)                  NOT NULL,
    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT fk_activities_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
);

CREATE TABLE activity_locations (
    activity_id    varchar(8) NOT NULL,
    location_order integer    NOT NULL,
    location_id    varchar(8) NOT NULL,
    CONSTRAINT pk_activity_locations PRIMARY KEY (activity_id, location_order),
    CONSTRAINT fk_activity_locations_activity FOREIGN KEY (activity_id) REFERENCES activities (id),
    CONSTRAINT fk_activity_locations_location FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT ck_activity_locations_order CHECK (location_order >= 0)
);

-- Postgres doesn't index foreign keys automatically; these cover the reverse lookups.
CREATE INDEX idx_trips_owner_id ON trips (owner_id);
CREATE INDEX idx_trip_members_user_id ON trip_members (user_id);
CREATE INDEX idx_trip_invitations_trip_id ON trip_invitations (trip_id);
CREATE INDEX idx_trip_invitations_receiver_id ON trip_invitations (receiver_id);
CREATE INDEX idx_activities_trip_id ON activities (trip_id);
CREATE INDEX idx_activity_locations_location_id ON activity_locations (location_id);
