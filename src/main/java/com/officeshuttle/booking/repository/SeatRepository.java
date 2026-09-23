package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByTrip_TripIdOrderBySeatNoAsc(Long tripId);

    Optional<Seat> findByTrip_TripIdAndSeatNo(Long tripId, Integer seatNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.trip.tripId = :tripId ORDER BY s.seatNo ASC")
    List<Seat> findByTripIdForUpdate(@Param("tripId") Long tripId);
}
