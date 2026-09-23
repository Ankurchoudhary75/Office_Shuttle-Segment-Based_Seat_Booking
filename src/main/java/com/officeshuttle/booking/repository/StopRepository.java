package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StopRepository extends JpaRepository<Stop, Long> {
    List<Stop> findByRoute_RouteIdOrderByStopOrderAsc(Long routeId);
    Optional<Stop> findByRoute_RouteIdAndStopOrder(Long routeId, Integer stopOrder);
    Optional<Stop> findByRoute_RouteIdAndNameIgnoreCase(Long routeId, String name);
}
