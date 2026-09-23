package com.officeshuttle.booking.edgecases;

import com.officeshuttle.booking.engine.SeatMap;
import com.officeshuttle.booking.engine.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoShowTest {

    @Test
    @DisplayName("No-Show: Only downstream remaining legs are freed; elapsed legs are never resold")
    void testNoShowDownstreamReleaseOnly() {
        SeatMap seatMap = new SeatMap(4, 1); // 4 stops: 0, 1, 2, 3

        // Passenger had booked [0, 3)
        seatMap.reserve(1, Segment.of(0, 3));

        // Shuttle departs Stop 0 (A) and Stop 1 (B) without passenger boarding.
        // At departedStopIdx = 1 (B), booking is marked NO_SHOW.
        // Only downstream legs [1, 3) are released back!
        seatMap.release(1, Segment.of(1, 3));

        // Downstream legs (B->D) are now available for waitlist promotion / booking
        assertTrue(seatMap.isSeatFree(1, Segment.of(1, 3)));

        // Elapsed leg 0 (A->B) was NOT released and cannot be booked
        assertFalse(seatMap.isSeatFree(1, Segment.of(0, 1)));
    }
}
