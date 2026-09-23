package com.officeshuttle.booking.engine;

import java.util.BitSet;

/**
 * Strategy pattern interface for seat selection among qualifying candidate seats.
 */
public interface SeatAllocationStrategy {
    /**
     * Chooses the optimal seat number (1-based) from the qualifying candidate set.
     * @param candidates BitSet of candidate seat indices (0-based)
     * @param seatMap current SeatMap state
     * @param segment requested journey segment
     * @return 1-based seat number, or -1 if candidate set is empty
     */
    int chooseSeat(BitSet candidates, SeatMap seatMap, Segment segment);
}
