package com.officeshuttle.booking.edgecases;

import com.officeshuttle.booking.engine.SeatMap;
import com.officeshuttle.booking.engine.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

class IntervalTrimmingTest {

    @Test
    @DisplayName("Interval Trimming: Trimming [0,3) to [1,3) releases [0,1) for immediate reuse on the same seat")
    void testIntervalTrimmingReleasesLeg() {
        SeatMap seatMap = new SeatMap(4, 1); // 1 seat, stops A=0, B=1, C=2, D=3

        // Passenger 1 books full journey A->D [0,3)
        seatMap.reserve(1, Segment.of(0, 3));
        assertFalse(seatMap.isSeatFree(1, Segment.of(0, 1)));

        // Passenger 1 changes boarding stop to B -> trims [0, 3) to [1, 3)
        // This releases sub-range [0, 1)
        seatMap.release(1, Segment.of(0, 1));

        // Passenger 2 can now book A->B [0,1) on the same seat!
        assertTrue(seatMap.isSeatFree(1, Segment.of(0, 1)));
        BitSet candidates = seatMap.getQualifyingSeats(Segment.of(0, 1));
        assertTrue(candidates.get(0));

        seatMap.reserve(1, Segment.of(0, 1));
        // Both bookings now co-exist on Seat 1: [0,1) and [1,3)
        assertEquals(0, seatMap.getRemainingFreeLegs(1));
    }
}
