package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.WaitlistEntry;
import com.officeshuttle.booking.domain.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByTrip_TripIdAndStatusOrderBySequenceNoAsc(Long tripId, WaitlistStatus status);

    @Query("SELECT COALESCE(MAX(w.sequenceNo), 0) FROM WaitlistEntry w WHERE w.trip.tripId = :tripId")
    Long findMaxSequenceNoByTripId(@Param("tripId") Long tripId);

    List<WaitlistEntry> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    Optional<WaitlistEntry> findByTrip_TripIdAndUser_UserIdAndStatus(Long tripId, Long userId, WaitlistStatus status);
}
