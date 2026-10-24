-- Precomputed place embeddings for lightweight semantic ranking.
--
-- Deliberately not pgvector: at Singapore scale the bbox filter leaves a few
-- hundred rows, so ranking is a cosine scan in Java. The vector is stored as
-- little-endian float32 so the encoder/decoder is shared by the index job and
-- the query path.

CREATE TABLE place_embedding (
    location_id varchar(8)  NOT NULL,
    model       varchar(64) NOT NULL,
    dims        integer     NOT NULL,
    vector      bytea       NOT NULL,
    created_at  timestamp(6) with time zone NOT NULL DEFAULT now(),
    CONSTRAINT pk_place_embedding PRIMARY KEY (location_id),
    CONSTRAINT fk_place_embedding_location FOREIGN KEY (location_id)
        REFERENCES location (id) ON DELETE CASCADE,
    CONSTRAINT ck_place_embedding_dims CHECK (dims > 0)
);
