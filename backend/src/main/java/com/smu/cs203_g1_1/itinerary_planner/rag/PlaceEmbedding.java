package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One embedding per indexed place.
 *
 * <p>{@code model} and {@code dims} are stored with the vector so a model change
 * can be detected and re-embedded rather than silently comparing vectors from
 * two different spaces.</p>
 */
@Entity
@Table(name = "place_embedding")
public class PlaceEmbedding {

    @Id
    @Column(name = "location_id", length = 8, nullable = false)
    private String locationId;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false)
    private int dims;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 8192)
    private byte[] vector;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PlaceEmbedding() {
        // JPA
    }

    public PlaceEmbedding(String locationId, String model, float[] values) {
        this.locationId = locationId;
        this.model = model;
        this.dims = values.length;
        this.vector = VectorCodec.encode(values);
        this.createdAt = Instant.now();
    }

    public String getLocationId() {
        return locationId;
    }

    public String getModel() {
        return model;
    }

    public int getDims() {
        return dims;
    }

    public byte[] getVector() {
        return vector;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public float[] asFloats() {
        return VectorCodec.decode(vector);
    }
}
