-- Invitations now carry the role the invitee will receive on acceptance.
-- DEFAULT keeps existing rows valid; the CHECK forbids OWNER invites
-- (ownership stays on trips.owner_id). Mirrors ck_trip_member_roles_role.
ALTER TABLE trip_invitations
    ADD COLUMN role varchar(16) NOT NULL DEFAULT 'VIEWER';

ALTER TABLE trip_invitations
    ADD CONSTRAINT ck_trip_invitations_role CHECK (role IN ('VIEWER', 'EDITOR'));
