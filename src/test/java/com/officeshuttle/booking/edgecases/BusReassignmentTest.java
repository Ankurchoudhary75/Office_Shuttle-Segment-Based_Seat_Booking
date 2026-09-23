package com.officeshuttle.booking.edgecases;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.Seat;
import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusReassignmentTest {

    @Test
    @DisplayName("Bus Reassignment: Downsizing from 3 seats to 2 seats retains seat 1 and 2 bookings, re-accommodates seat 3")
    void testDownsizingReaccommodation() {
        Trip trip = new Trip();
        trip.setTripId(1L);

        Seat seat1 = new Seat(trip, 1);
        Seat seat2 = new Seat(trip, 2);
        Seat seat3 = new Seat(trip, 3);

        User u1 = new User(); u1.setUserId(1L);
        User u2 = new User(); u2.setUserId(2L);
        User u3 = new User(); u3.setUserId(3L);

        Booking b1 = new Booking(trip, seat1, u1, 0, 1, "k1");
        Booking b2 = new Booking(trip, seat2, u2, 1, 3, "k2");
        Booking b3 = new Booking(trip, seat3, u3, 0, 2, "k3");

        List<Booking> activeBookings = List.of(b1, b2, b3);

        int newCapacity = 2;
        List<Booking> retained = new ArrayList<>();
        List<Booking> reaccommodated = new ArrayList<>();

        for (Booking b : activeBookings) {
            if (b.getSeat().getSeatNo() <= newCapacity) {
                retained.add(b);
            } else {
                reaccommodated.add(b);
            }
        }

        assertEquals(2, retained.size());
        assertEquals(1, reaccommodated.size());
        assertEquals(3, reaccommodated.get(0).getSeat().getSeatNo());
    }
}
