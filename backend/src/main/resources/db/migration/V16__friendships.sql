-- Friend requests and friendships (CGT-163). One row per pair: PENDING until the
-- addressee accepts; a rejected request is deleted so it can be sent again.
-- Rows go with either user (admin delete), hence ON DELETE CASCADE.
CREATE TABLE friendships (
    id           varchar(8)                  NOT NULL,
    requester_id varchar(8)                  NOT NULL,
    addressee_id varchar(8)                  NOT NULL,
    status       varchar(16)                 NOT NULL,
    created_at   timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_friendships PRIMARY KEY (id),
    CONSTRAINT fk_friendships_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_addressee FOREIGN KEY (addressee_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_friendships_pair UNIQUE (requester_id, addressee_id),
    CONSTRAINT ck_friendships_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_friendships_not_self CHECK (requester_id <> addressee_id)
);

CREATE INDEX idx_friendships_addressee_status ON friendships (addressee_id, status);
