package com.officeshuttle.booking.engine;

import org.springframework.stereotype.Component;
import java.util.BitSet;

/**
 * First-Fit Strategy: Picks the lowest-numbered qualifying seat.
 * Deterministic and simple, but may fragment the bus.
 */
@Component("firstFitStrategy")
public class FirstFitStrategy implements SeatAllocationStrategy {

    @Override
    public int chooseSeat(BitSet candidates, SeatMap seatMap, Segment segment) {
        int firstBit = candidates.nextSetBit(0);
        if (firstBit < 0) {
            return -1; // No seat available -> caller waitlists
        }
        return firstBit + 1; // Convert to 1-based seat number
    }
}
