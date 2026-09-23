package com.officeshuttle.booking.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatAllocationStrategyTest {

    private SeatMap seatMap;
    private BestFitStrategy bestFitStrategy;
    private FirstFitStrategy firstFitStrategy;

    @BeforeEach
    void setUp() {
        // 4 stops: A=0, B=1, C=2, D=3. 3 seats.
        seatMap = new SeatMap(4, 3);
        bestFitStrategy = new BestFitStrategy();
        firstFitStrategy = new FirstFitStrategy();
    }

    @Test
    @DisplayName("Best-Fit chooses the most-committed compatible seat, while First-Fit chooses the lowest index")
    void testBestFitVersusFirstFit() {
        // Seat 1: booked A->B (occupies leg 0; legs 1,2 are free -> 2 free legs)
        seatMap.reserve(1, Segment.of(0, 1));

        // Seat 2: completely empty (3 free legs)
        // Seat 3: completely empty (3 free legs)

        // Incoming request: B->D (occupies legs 1, 2)
        Segment bd = Segment.of(1, 3);
        BitSet candidates = seatMap.getQualifyingSeats(bd);
        // All seats 1, 2, 3 qualify!

        // Best-Fit should pick Seat 1 because remaining free legs after booking will be:
        // Seat 1: 2 - 2 = 0 free legs remaining (tightest fit!)
        // Seat 2: 3 - 2 = 1 free leg remaining
        // Seat 3: 3 - 2 = 1 free leg remaining
        int bestFitChosen = bestFitStrategy.chooseSeat(candidates, seatMap, bd);
        assertEquals(1, bestFitChosen, "Best-fit must pack seat 1 to minimize remaining capacity and bus fragmentation");

        // Now test First-Fit on a situation where seat 1 is unavailable
        seatMap.reserve(1, bd); // seat 1 now full
        BitSet newCandidates = seatMap.getQualifyingSeats(Segment.of(0, 1));
        // seats 2 and 3 qualify
        int firstFitChosen = firstFitStrategy.chooseSeat(newCandidates, seatMap, Segment.of(0, 1));
        assertEquals(2, firstFitChosen, "First-fit must choose the lowest index available (Seat 2)");
    }
}
