package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.BookingStatus;
import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.engine.SeatMap;
import com.officeshuttle.booking.engine.Segment;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.repository.BookingRepository;
import com.officeshuttle.booking.repository.StopRepository;
import com.officeshuttle.booking.service.BoardingService;
import com.officeshuttle.booking.service.TripService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;
    private final BookingRepository bookingRepository;
    private final StopRepository stopRepository;
    private final BoardingService boardingService;

    public TripController(
            TripService tripService,
            BookingRepository bookingRepository,
            StopRepository stopRepository,
            BoardingService boardingService) {
        this.tripService = tripService;
        this.bookingRepository = bookingRepository;
        this.stopRepository = stopRepository;
        this.boardingService = boardingService;
    }

    @GetMapping
    public ResponseEntity<List<Trip>> getTrips(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate searchDate = date != null ? date : LocalDate.now().plusDays(1);
        return ResponseEntity.ok(tripService.getTripsByDate(searchDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Trip> getTripById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @PostMapping
    public ResponseEntity<Trip> scheduleTrip(@RequestBody Map<String, Object> request) {
        Long routeId = Long.valueOf(request.get("routeId").toString());
        Long busId = Long.valueOf(request.get("busId").toString());
        LocalDate serviceDate = LocalDate.parse(request.get("serviceDate").toString());

        Trip scheduled = tripService.scheduleTrip(routeId, busId, serviceDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduled);
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<Map<String, Object>> getAvailability(
            @PathVariable("id") Long tripId,
            @RequestParam("from") String fromStop,
            @RequestParam("to") String toStop) {

        Trip trip = tripService.getTripById(tripId);
        Long routeId = trip.getRoute().getRouteId();

        int fromIdx = resolveStopIndex(routeId, fromStop);
        int toIdx = resolveStopIndex(routeId, toStop);

        Segment segment = Segment.of(fromIdx, toIdx);

        List<Booking> confirmedBookings = bookingRepository.findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);
        SeatMap seatMap = new SeatMap(trip.getRoute().getTotalStops(), trip.getBus().getSeatCount());

        for (Booking b : confirmedBookings) {
            seatMap.reserve(b.getSeat().getSeatNo(), Segment.of(b.getFromStopIdx(), b.getToStopIdx()));
        }

        BitSet qualifying = seatMap.getQualifyingSeats(segment);
        List<Integer> qualifyingSeatNumbers = new ArrayList<>();
        for (int s = qualifying.nextSetBit(0); s >= 0; s = qualifying.nextSetBit(s + 1)) {
            qualifyingSeatNumbers.add(s + 1);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("tripId", tripId);
        response.put("segment", Map.of("from", fromStop, "to", toStop, "fromIdx", fromIdx, "toIdx", toIdx));
        response.put("qualifyingSeats", qualifyingSeatNumbers);
        response.put("availableCount", qualifyingSeatNumbers.size());
        response.put("tripUtilizationRatio", seatMap.getUtilizationRatio());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reassign-bus")
    public ResponseEntity<Trip> reassignBus(
            @PathVariable("id") Long tripId,
            @RequestBody Map<String, Object> request,
            Authentication auth) {
        Long newBusId = Long.valueOf(request.get("newBusId").toString());
        Long operatorId = auth != null && auth.getDetails() instanceof Long uid ? uid : null;
        Trip updated = boardingService.reassignBus(tripId, newBusId, operatorId);
        return ResponseEntity.ok(updated);
    }

    private int resolveStopIndex(Long routeId, String stopParam) {
        try {
            return Integer.parseInt(stopParam);
        } catch (NumberFormatException ignored) {
            // Lookup by name
            return stopRepository.findByRoute_RouteIdAndNameIgnoreCase(routeId, stopParam)
                    .orElseThrow(() -> new InvalidSegmentException("Stop not found on route: " + stopParam))
                    .getStopOrder();
        }
    }
}
