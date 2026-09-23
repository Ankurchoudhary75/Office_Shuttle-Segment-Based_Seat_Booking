package com.officeshuttle.booking.concurrency;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.BookingStatus;
import com.officeshuttle.booking.domain.Seat;
import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

public class HighValueInvariantTest {

    @Test
    @DisplayName("High-Value Invariant: No two active (CONFIRMED) bookings on the same physical seat may EVER overlap")
    void testNoOverlappingBookingsOnSameSeat() {
        Trip trip = new Trip();
        trip.setTripId(1L);

        Seat seat1 = new Seat(trip, 1);
        Seat seat2 = new Seat(trip, 2);

        User u1 = new User(); u1.setUserId(1L);
        User u2 = new User(); u2.setUserId(2L);
        User u3 = new User(); u3.setUserId(3L);

        List<Booking> bookings = new ArrayList<>();

        // Valid scenario: Seat 1 has A->B [0,1) and B->D [1,3)
        Booking b1 = new Booking(trip, seat1, u1, 0, 1, "k1");
        Booking b2 = new Booking(trip, seat1, u2, 1, 3, "k2");

        // Seat 2 has A->C [0,2)
        Booking b3 = new Booking(trip, seat2, u3, 0, 2, "k3");

        bookings.add(b1);
        bookings.add(b2);
        bookings.add(b3);

        assertInvariantHolds(bookings);
    }

    /**
     * Mathematical invariant validator checking half-open intervals [Xs, Xd) and [Ys, Yd).
     */
    public static void assertInvariantHolds(List<Booking> bookings) {
        Map<Long, List<Booking>> bookingsBySeat = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.groupingBy(b -> b.getSeat().getSeatId() != null ? b.getSeat().getSeatId() : (long) b.getSeat().getSeatNo()));

        for (Map.Entry<Long, List<Booking>> entry : bookingsBySeat.entrySet()) {
            List<Booking> seatBookings = entry.getValue();
            int n = seatBookings.size();

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    Booking b1 = seatBookings.get(i);
                    Booking b2 = seatBookings.get(j);

                    boolean overlaps = (b1.getFromStopIdx() < b2.getToStopIdx()) && (b2.getFromStopIdx() < b1.getToStopIdx());
                    if (overlaps) {
                        fail(String.format(
                                "CRITICAL INVARIANT VIOLATED on Seat %d: Booking #%d [%d, %d) overlaps with Booking #%d [%d, %d)",
                                entry.getKey(),
                                b1.getBookingId(), b1.getFromStopIdx(), b1.getToStopIdx(),
                                b2.getBookingId(), b2.getFromStopIdx(), b2.getToStopIdx()
                        ));
                    }
                }
            }
        }
    }
}
