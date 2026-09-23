package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByServiceDateAndStatus(LocalDate serviceDate, TripStatus status);
    List<Trip> findByRoute_RouteIdAndServiceDate(Long routeId, LocalDate serviceDate);
}
