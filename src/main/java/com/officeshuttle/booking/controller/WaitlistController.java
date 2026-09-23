package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.WaitlistEntry;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.repository.StopRepository;
import com.officeshuttle.booking.repository.TripRepository;
import com.officeshuttle.booking.repository.WaitlistRepository;
import com.officeshuttle.booking.service.WaitlistService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final WaitlistRepository waitlistRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;

    public WaitlistController(
            WaitlistService waitlistService,
            WaitlistRepository waitlistRepository,
            TripRepository tripRepository,
            StopRepository stopRepository) {
        this.waitlistService = waitlistService;
        this.waitlistRepository = waitlistRepository;
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
    }

    @PostMapping("/trips/{id}/waitlist")
    public ResponseEntity<Map<String, Object>> joinWaitlist(
            @PathVariable("id") Long tripId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        Long userId = (Long) auth.getDetails();
        String fromStop = request.get("fromStop");
        String toStop = request.get("toStop");

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new InvalidSegmentException("Trip not found: " + tripId));
        Long routeId = trip.getRoute().getRouteId();

        int fromIdx = resolveStopIndex(routeId, fromStop);
        int toIdx = resolveStopIndex(routeId, toStop);

        WaitlistEntry entry = waitlistService.joinWaitlist(tripId, userId, fromIdx, toIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("waitlistId", entry.getWaitlistId());
        response.put("tripId", tripId);
        response.put("sequenceNo", entry.getSequenceNo());
        response.put("status", entry.getStatus().name());
        response.put("segment", Map.of("from", fromStop, "to", toStop, "fromIdx", fromIdx, "toIdx", toIdx));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/users/me/waitlist")
    public ResponseEntity<List<WaitlistEntry>> getMyWaitlist(Authentication auth) {
        Long userId = (Long) auth.getDetails();
        return ResponseEntity.ok(waitlistRepository.findByUser_UserIdOrderByCreatedAtDesc(userId));
    }

    private int resolveStopIndex(Long routeId, String stopParam) {
        try {
            return Integer.parseInt(stopParam);
        } catch (NumberFormatException ignored) {
            return stopRepository.findByRoute_RouteIdAndNameIgnoreCase(routeId, stopParam)
                    .orElseThrow(() -> new InvalidSegmentException("Stop not found on route: " + stopParam))
                    .getStopOrder();
        }
    }
}
