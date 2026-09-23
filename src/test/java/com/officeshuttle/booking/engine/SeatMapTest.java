package com.officeshuttle.booking.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class SeatMapTest {

    private SeatMap seatMap;

    @BeforeEach
    void setUp() {
        // 4 stops: A=0, B=1, C=2, D=3 -> 3 legs: 0, 1, 2. 3 seats.
        seatMap = new SeatMap(4, 3);
    }

    @Test
    @DisplayName("SeatMap allows booking contiguous non-overlapping segments on the same physical seat (seat weaving)")
    void testSeatWeavingOnSameSeat() {
        Segment ab = Segment.of(0, 1);
        Segment bd = Segment.of(1, 3);

        // Book A->B on Seat 1
        assertTrue(seatMap.isSeatFree(1, ab));
        seatMap.reserve(1, ab);

        // Seat 1 is now occupied for A->B, but MUST be free for B->D
        assertFalse(seatMap.isSeatFree(1, ab));
        assertTrue(seatMap.isSeatFree(1, bd), "Seat 1 must remain free for B->D after booking A->B");

        // Book B->D on Seat 1
        seatMap.reserve(1, bd);
        assertFalse(seatMap.isSeatFree(1, bd));

        // Seat 1 is now fully booked (legs 0, 1, 2)
        assertEquals(0, seatMap.getRemainingFreeLegs(1));
    }

    @Test
    @DisplayName("SeatMap getQualifyingSeats returns column-wise intersection")
    void testGetQualifyingSeats() {
        Segment ab = Segment.of(0, 1);
        Segment bc = Segment.of(1, 2);
        Segment ac = Segment.of(0, 2);

        // Initially all 3 seats qualify for A->C
        BitSet candidates = seatMap.getQualifyingSeats(ac);
        assertEquals(3, candidates.cardinality());
        assertTrue(candidates.get(0) && candidates.get(1) && candidates.get(2));

        // Book seat 1 on A->B
        seatMap.reserve(1, ab);

        // Now seat 1 does NOT qualify for A->C, but seats 2 and 3 do
        candidates = seatMap.getQualifyingSeats(ac);
        assertEquals(2, candidates.cardinality());
        assertFalse(candidates.get(0));
        assertTrue(candidates.get(1) && candidates.get(2));

        // But seat 1 DOES qualify for B->C
        BitSet bcCandidates = seatMap.getQualifyingSeats(bc);
        assertTrue(bcCandidates.get(0));
    }

    @Test
    @DisplayName("SeatMap release frees legs and updates column-wise bitsets accurately")
    void testReleaseAndRebook() {
        Segment ad = Segment.of(0, 3);
        seatMap.reserve(1, ad);

        assertFalse(seatMap.isSeatFree(1, Segment.of(1, 2)));

        seatMap.release(1, ad);

        assertTrue(seatMap.isSeatFree(1, ad));
        BitSet candidates = seatMap.getQualifyingSeats(ad);
        assertTrue(candidates.get(0));
    }

    @Test
    @DisplayName("SeatMap correctly calculates utilization ratio")
    void testUtilizationRatio() {
        assertEquals(0.0, seatMap.getUtilizationRatio(), 0.001);

        // Total capacity = 3 seats * 3 legs = 9 leg-slots
        // Book A->D (3 legs) on seat 1 -> 3/9 = 0.333
        seatMap.reserve(1, Segment.of(0, 3));
        assertEquals(3.0 / 9.0, seatMap.getUtilizationRatio(), 0.001);

        // Book B->D (2 legs) on seat 2 -> 5/9 = 0.555
        seatMap.reserve(2, Segment.of(1, 3));
        assertEquals(5.0 / 9.0, seatMap.getUtilizationRatio(), 0.001);
    }
}
