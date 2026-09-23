package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByTrip_TripIdAndStatus(Long tripId, BookingStatus status);

    List<Booking> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT b FROM Booking b WHERE b.seat.seatId = :seatId AND b.status = 'CONFIRMED' " +
           "AND b.fromStopIdx < :requestedTo AND b.toStopIdx > :requestedFrom")
    List<Booking> findOverlappingBookings(@Param("seatId") Long seatId,
                                         @Param("requestedFrom") Integer requestedFrom,
                                         @Param("requestedTo") Integer requestedTo);

    @Query(value = "SELECT * FROM bookings WHERE trip_id = :tripId AND status = 'CONFIRMED'", nativeQuery = true)
    List<Booking> findAllConfirmedByTripIdNative(@Param("tripId") Long tripId);
}
