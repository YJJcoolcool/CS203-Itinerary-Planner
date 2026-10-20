package com.smu.cs203_g1_1.itinerary_planner.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts embedding vectors to and from the little-endian float32 blob stored
 * in {@code place_embedding.vector}.
 *
 * <p>One codec for both sides of the pipeline: the index job and the query path
 * must agree byte-for-byte, or cosine scores are silently meaningless.</p>
 */
public final class VectorCodec {

    private static final int FLOAT_BYTES = Float.BYTES;

    private VectorCodec() {
    }

    public static byte[] encode(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("vector must not be null");
        }

        ByteBuffer buffer = ByteBuffer
            .allocate(vector.length * FLOAT_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);

        buffer.asFloatBuffer().put(vector);

        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length % FLOAT_BYTES != 0) {
            throw new IllegalArgumentException("not a float32 blob");
        }

        float[] vector = new float[bytes.length / FLOAT_BYTES];

        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(vector);

        return vector;
    }
}
