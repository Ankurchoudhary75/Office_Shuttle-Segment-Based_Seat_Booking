package com.officeshuttle.booking.engine;

import com.officeshuttle.booking.domain.WaitlistEntry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict Global FCFS-Among-Eligible Promotion Policy (Default).
 * Iterates through waiting entries in sequence order (oldest first).
 * The earliest requester who can be seated is promoted next, never skipped in favor
 * of a later request just because it uses capacity more efficiently.
 * Continues scanning because a single cancellation can satisfy multiple disjoint waitlisted requests.
 */
@Component("fcfsEligiblePolicy")
@Primary
public class FcfsEligiblePolicy implements PromotionPolicy {

    @Override
    public Map<WaitlistEntry, Integer> findPromotableEntries(
            List<WaitlistEntry> waitingEntries,
            SeatMap seatMap,
            SeatAllocationStrategy allocationStrategy) {

        Map<WaitlistEntry, Integer> promotions = new LinkedHashMap<>();

        for (WaitlistEntry entry : waitingEntries) {
            Segment segment = Segment.of(entry.getFromStopIdx(), entry.getToStopIdx());
            BitSet candidates = seatMap.getQualifyingSeats(segment);

            if (candidates.cardinality() > 0) {
                int chosenSeat = allocationStrategy.chooseSeat(candidates, seatMap, segment);
                if (chosenSeat > 0) {
                    promotions.put(entry, chosenSeat);
                    // Provisionally apply on the seatMap copy so subsequent entries can also fit if disjoint
                    seatMap.reserve(chosenSeat, segment);
                }
            }
        }

        return promotions;
    }
}
