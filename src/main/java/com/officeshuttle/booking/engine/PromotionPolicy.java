package com.officeshuttle.booking.engine;

import com.officeshuttle.booking.domain.WaitlistEntry;

import java.util.List;
import java.util.Map;

/**
 * Strategy pattern interface for waitlist promotion policies.
 */
public interface PromotionPolicy {
    /**
     * Identifies eligible waitlist entries that can be promoted given the current seat map state.
     * @param waitingEntries List of entries with status WAITING, ordered by sequenceNo ascending
     * @param seatMap current physical occupancy of the bus
     * @param allocationStrategy strategy to pick physical seat
     * @return Map of WaitlistEntry to the assigned seat number (1-based)
     */
    Map<WaitlistEntry, Integer> findPromotableEntries(
            List<WaitlistEntry> waitingEntries,
            SeatMap seatMap,
            SeatAllocationStrategy allocationStrategy
    );
}
