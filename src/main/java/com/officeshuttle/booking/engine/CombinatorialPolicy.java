package com.officeshuttle.booking.engine;

import com.officeshuttle.booking.domain.WaitlistEntry;
import org.springframework.stereotype.Component;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combinatorial Promotion Policy (Optional Enhancement).
 * Extends FCFS promotion: if no single waitlist request can fill a freed segment,
 * searches for compatible pairs of waitlisted requests that together cover it
 * without overlapping each other (e.g. A->B plus B->D for a freed A->D seat).
 */
@Component("combinatorialPolicy")
public class CombinatorialPolicy implements PromotionPolicy {

    @Override
    public Map<WaitlistEntry, Integer> findPromotableEntries(
            List<WaitlistEntry> waitingEntries,
            SeatMap seatMap,
            SeatAllocationStrategy allocationStrategy) {

        Map<WaitlistEntry, Integer> promotions = new LinkedHashMap<>();

        // Phase 1: Try single requests
        for (WaitlistEntry entry : waitingEntries) {
            Segment segment = Segment.of(entry.getFromStopIdx(), entry.getToStopIdx());
            BitSet candidates = seatMap.getQualifyingSeats(segment);

            if (candidates.cardinality() > 0) {
                int chosenSeat = allocationStrategy.chooseSeat(candidates, seatMap, segment);
                if (chosenSeat > 0) {
                    promotions.put(entry, chosenSeat);
                    seatMap.reserve(chosenSeat, segment);
                }
            }
        }

        // Phase 2: Combinatorial pairing for remaining entries
        int size = waitingEntries.size();
        for (int i = 0; i < size; i++) {
            WaitlistEntry e1 = waitingEntries.get(i);
            if (promotions.containsKey(e1)) continue;

            Segment s1 = Segment.of(e1.getFromStopIdx(), e1.getToStopIdx());

            for (int j = i + 1; j < size; j++) {
                WaitlistEntry e2 = waitingEntries.get(j);
                if (promotions.containsKey(e2)) continue;

                Segment s2 = Segment.of(e2.getFromStopIdx(), e2.getToStopIdx());

                // Must be mutually disjoint
                if (!s1.overlaps(s2)) {
                    // Check if both fit on the same physical seat
                    BitSet cand1 = seatMap.getQualifyingSeats(s1);
                    BitSet cand2 = seatMap.getQualifyingSeats(s2);
                    cand1.and(cand2);

                    if (cand1.cardinality() > 0) {
                        int chosenSeat = cand1.nextSetBit(0) + 1;
                        promotions.put(e1, chosenSeat);
                        seatMap.reserve(chosenSeat, s1);

                        promotions.put(e2, chosenSeat);
                        seatMap.reserve(chosenSeat, s2);
                        break;
                    }
                }
            }
        }

        return promotions;
    }
}
