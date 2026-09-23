package com.officeshuttle.booking.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {

    @Test
    @DisplayName("Segment.overlaps: touching stops (A->B and B->D) do not overlap")
    void testTouchingStopsDoNotOverlap() {
        Segment ab = Segment.of(0, 1);
        Segment bd = Segment.of(1, 3);

        assertFalse(ab.overlaps(bd), "A->B [0,1) and B->D [1,3) share only boundary point, zero road legs; must not overlap");
        assertFalse(bd.overlaps(ab), "Symmetric overlap check must also be false");
    }

    @Test
    @DisplayName("Segment.overlaps: overlapping intervals conflict")
    void testOverlappingIntervalsConflict() {
        Segment ac = Segment.of(0, 2);
        Segment bd = Segment.of(1, 3);

        assertTrue(ac.overlaps(bd), "A->C [0,2) and B->D [1,3) share leg 1; must overlap");
        assertTrue(bd.overlaps(ac));
    }

    @Test
    @DisplayName("Segment.overlaps: identical intervals conflict")
    void testIdenticalIntervalsConflict() {
        Segment s1 = Segment.of(1, 3);
        Segment s2 = Segment.of(1, 3);

        assertTrue(s1.overlaps(s2));
    }

    @Test
    @DisplayName("Segment construction rejects invalid intervals")
    void testInvalidSegmentConstruction() {
        assertThrows(IllegalArgumentException.class, () -> Segment.of(2, 2), "Zero-length segment must be rejected");
        assertThrows(IllegalArgumentException.class, () -> Segment.of(3, 1), "Reversed segment must be rejected");
        assertThrows(IllegalArgumentException.class, () -> Segment.of(-1, 2), "Negative index must be rejected");
    }

    @Test
    @DisplayName("Segment bitmask calculations are exact")
    void testRangeMaskCalculation() {
        // [0, 1) -> leg 0 -> 1L << 0 = 1
        assertEquals(1L, Segment.of(0, 1).getRangeMask());

        // [1, 3) -> legs 1 and 2 -> bit 1 and bit 2 set = 2 + 4 = 6 (binary 110)
        assertEquals(6L, Segment.of(1, 3).getRangeMask());

        // [0, 3) -> legs 0, 1, 2 -> 1 + 2 + 4 = 7 (binary 111)
        assertEquals(7L, Segment.of(0, 3).getRangeMask());
    }
}
