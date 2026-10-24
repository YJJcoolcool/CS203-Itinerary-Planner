package com.smu.cs203_g1_1.itinerary_planner.rag;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class VectorCodecTest {

    @Test
    void roundTripsFloatValues() {
        float[] original = {1.5f, -2.25f, 0f, 1e-6f};

        float[] decoded = VectorCodec.decode(VectorCodec.encode(original));

        assertArrayEquals(original, decoded);
    }

    @Test
    void encodedLengthIsFourBytesPerDimension() {
        assertArrayEquals(new byte[12], VectorCodec.encode(new float[3]));
    }

    @Test
    void rejectsBlobsThatAreNotWholeFloats() {
        assertThrows(IllegalArgumentException.class,
            () -> VectorCodec.decode(new byte[5]));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(null));
    }
}
