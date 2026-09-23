package com.officeshuttle.booking.engine;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.BitSet;

/**
 * Best-Fit Strategy (Default): Among qualifying seats, picks the one whose remaining
 * free capacity after this booking is smallest (i.e. the seat already most "used up" in a compatible way).
 * Ties are broken by lowest seat number for explainability.
 * 
 * Provides significant utilization gain and less fragmentation than First-Fit, while
 * remaining O(1)-ish as it only evaluates the candidate set.
 */
@Component("bestFitStrategy")
@Primary
public class BestFitStrategy implements SeatAllocationStrategy {

    @Override
    public int chooseSeat(BitSet candidates, SeatMap seatMap, Segment segment) {
        int bestSeat = -1;
        int bestRemainingFreeLegs = Integer.MAX_VALUE;

        for (int s = candidates.nextSetBit(0); s >= 0; s = candidates.nextSetBit(s + 1)) {
            int seatNo = s + 1; // 1-based seat number
            // Calculate remaining free legs on this seat after booking this segment
            int currentFreeLegs = seatMap.getRemainingFreeLegs(seatNo);
            int remainingAfterBooking = currentFreeLegs - segment.getLegCount();

            if (remainingAfterBooking < bestRemainingFreeLegs ||
                (remainingAfterBooking == bestRemainingFreeLegs && (bestSeat == -1 || seatNo < bestSeat))) {
                bestSeat = seatNo;
                bestRemainingFreeLegs = remainingAfterBooking;
            }
        }

        return bestSeat;
    }
}
