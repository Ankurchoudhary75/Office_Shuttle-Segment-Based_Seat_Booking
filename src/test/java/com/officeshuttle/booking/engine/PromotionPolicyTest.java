package com.officeshuttle.booking.engine;

import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.User;
import com.officeshuttle.booking.domain.WaitlistEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromotionPolicyTest {

    private SeatMap seatMap;
    private BestFitStrategy bestFitStrategy;
    private FcfsEligiblePolicy fcfsPolicy;
    private CombinatorialPolicy combinatorialPolicy;

    @BeforeEach
    void setUp() {
        seatMap = new SeatMap(4, 2); // 4 stops (A=0, B=1, C=2, D=3), 2 seats
        bestFitStrategy = new BestFitStrategy();
        fcfsPolicy = new FcfsEligiblePolicy();
        combinatorialPolicy = new CombinatorialPolicy();
    }

    @Test
    @DisplayName("FCFS-among-eligible promotes earliest eligible request and continues scanning to satisfy multiple disjoint entries")
    void testFcfsMultiplePromotion() {
        // Seat 1 has A->B (occupies leg 0)
        // Seat 2 is fully booked A->D
        seatMap.reserve(1, Segment.of(0, 1));
        seatMap.reserve(2, Segment.of(0, 3));

        // Waiting entries:
        // Entry 1 (seq 1): wants A->D (cannot fit because neither seat has 3 free legs)
        // Entry 2 (seq 2): wants B->C (fits on Seat 1)
        // Entry 3 (seq 3): wants C->D (fits on Seat 1 after Entry 2 is placed!)
        User u1 = new User(); u1.setUserId(101L);
        User u2 = new User(); u2.setUserId(102L);
        User u3 = new User(); u3.setUserId(103L);
        Trip trip = new Trip(); trip.setTripId(1L);

        WaitlistEntry w1 = new WaitlistEntry(trip, u1, 0, 3, 1L);
        WaitlistEntry w2 = new WaitlistEntry(trip, u2, 1, 2, 2L);
        WaitlistEntry w3 = new WaitlistEntry(trip, u3, 2, 3, 3L);

        List<WaitlistEntry> waitlist = List.of(w1, w2, w3);

        Map<WaitlistEntry, Integer> promoted = fcfsPolicy.findPromotableEntries(waitlist, seatMap, bestFitStrategy);

        // Entry 1 cannot be promoted, but Entry 2 and Entry 3 are both promoted onto Seat 1
        assertFalse(promoted.containsKey(w1), "Entry 1 wants entire route and cannot fit");
        assertTrue(promoted.containsKey(w2), "Entry 2 must be promoted");
        assertTrue(promoted.containsKey(w3), "Entry 3 must be promoted");

        assertEquals(1, promoted.get(w2));
        assertEquals(1, promoted.get(w3));
    }

    @Test
    @DisplayName("CombinatorialPolicy pairs disjoint requests to fill a freed long segment")
    void testCombinatorialPairing() {
        // Seat 1 is completely free (legs 0, 1, 2)
        // Waitlist has no single A->D request, but has A->B and B->D
        User u1 = new User(); u1.setUserId(101L);
        User u2 = new User(); u2.setUserId(102L);
        Trip trip = new Trip(); trip.setTripId(1L);

        WaitlistEntry w1 = new WaitlistEntry(trip, u1, 0, 1, 1L); // A->B
        WaitlistEntry w2 = new WaitlistEntry(trip, u2, 1, 3, 2L); // B->D

        List<WaitlistEntry> list = List.of(w1, w2);
        Map<WaitlistEntry, Integer> promoted = combinatorialPolicy.findPromotableEntries(list, seatMap, bestFitStrategy);

        assertEquals(2, promoted.size());
        assertEquals(1, promoted.get(w1));
        assertEquals(1, promoted.get(w2));
    }
}
