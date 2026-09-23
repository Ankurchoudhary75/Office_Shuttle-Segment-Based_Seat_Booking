package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.service.BoardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class BoardingController {

    private final BoardingService boardingService;

    public BoardingController(BoardingService boardingService) {
        this.boardingService = boardingService;
    }

    @PostMapping("/bookings/{id}/check-in")
    public ResponseEntity<Map<String, Object>> checkIn(
            @PathVariable("id") Long bookingId,
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        Long driverUserId = auth != null && auth.getDetails() instanceof Long uid ? uid : null;
        int currentStopIdx = Integer.parseInt(request.get("currentStopIdx").toString());

        Booking checkedIn = boardingService.checkIn(bookingId, driverUserId, currentStopIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", checkedIn.getBookingId());
        response.put("status", checkedIn.getStatus().name());
        response.put("stopIdx", currentStopIdx);
        response.put("message", "Passenger check-in successful.");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/bookings/{id}/no-show")
    public ResponseEntity<Map<String, Object>> markNoShow(
            @PathVariable("id") Long bookingId,
            @RequestBody Map<String, Object> request) {
        int departedStopIdx = Integer.parseInt(request.get("departedStopIdx").toString());
        Booking noShow = boardingService.processNoShow(bookingId, departedStopIdx);

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", noShow.getBookingId());
        response.put("status", noShow.getStatus().name());
        response.put("message", "Booking updated to NO_SHOW. Downstream capacity released.");

        return ResponseEntity.ok(response);
    }
}
