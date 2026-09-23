package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.Stop;
import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.repository.BookingRepository;
import com.officeshuttle.booking.repository.StopRepository;
import com.officeshuttle.booking.repository.TripRepository;
import com.officeshuttle.booking.service.BoardingService;
import com.officeshuttle.booking.service.BookingService;
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
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final BoardingService boardingService;
    private final WaitlistService waitlistService;

    public BookingController(
            BookingService bookingService,
            BookingRepository bookingRepository,
            TripRepository tripRepository,
            StopRepository stopRepository,
            BoardingService boardingService,
            WaitlistService waitlistService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.stopRepository = stopRepository;
        this.boardingService = boardingService;
        this.waitlistService = waitlistService;
    }

    @PostMapping("/trips/{tripId}/bookings")
    public ResponseEntity<Map<String, Object>> bookSegment(
            @PathVariable("tripId") Long tripId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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

        Booking booking = bookingService.bookSegment(tripId, userId, fromIdx, toIdx, idempotencyKey);

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", "bk_" + booking.getBookingId());
        response.put("tripId", tripId);
        response.put("seatNo", booking.getSeat().getSeatNo());
        response.put("segment", Map.of("from", fromStop, "to", toStop, "fromIdx", fromIdx, "toIdx", toIdx));
        response.put("status", booking.getStatus().name());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelBooking(
            @PathVariable("id") Long bookingId,
            Authentication auth) {
        Long userId = (Long) auth.getDetails();
        Booking cancelled = bookingService.cancelBooking(bookingId, userId);

        // Run waitlist promotion sweep immediately
        List<Booking> promoted = waitlistService.runPromotionSweep(cancelled.getTrip().getTripId());

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", cancelled.getBookingId());
        response.put("status", cancelled.getStatus().name());
        response.put("freedSeatNo", cancelled.getSeat().getSeatNo());
        response.put("promotedCount", promoted.size());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/bookings/{id}/trim")
    public ResponseEntity<Map<String, Object>> trimBooking(
            @PathVariable("id") Long bookingId,
            @RequestBody Map<String, String> request,
            Authentication auth) {
        Long userId = (Long) auth.getDetails();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new InvalidSegmentException("Booking not found: " + bookingId));

        String newFromStop = request.get("newFromStop");
        int newFromIdx = resolveStopIndex(booking.getTrip().getRoute().getRouteId(), newFromStop);

        Booking trimmed = boardingService.trimBookingInterval(bookingId, userId, newFromIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", trimmed.getBookingId());
        response.put("newFromStopIdx", trimmed.getFromStopIdx());
        response.put("toStopIdx", trimmed.getToStopIdx());
        response.put("status", trimmed.getStatus().name());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/me/bookings")
    public ResponseEntity<List<Booking>> getMyBookings(Authentication auth) {
        Long userId = (Long) auth.getDetails();
        return ResponseEntity.ok(bookingRepository.findByUser_UserIdOrderByCreatedAtDesc(userId));
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
