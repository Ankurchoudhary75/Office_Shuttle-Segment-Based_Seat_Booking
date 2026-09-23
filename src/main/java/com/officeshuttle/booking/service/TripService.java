package com.officeshuttle.booking.service;

import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.RedisScriptExecutor;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusRepository busRepository;
    private final SeatRepository seatRepository;
    private final RedisScriptExecutor redisScriptExecutor;

    public TripService(
            TripRepository tripRepository,
            RouteRepository routeRepository,
            BusRepository busRepository,
            SeatRepository seatRepository,
            RedisScriptExecutor redisScriptExecutor) {
        this.tripRepository = tripRepository;
        this.routeRepository = routeRepository;
        this.busRepository = busRepository;
        this.seatRepository = seatRepository;
        this.redisScriptExecutor = redisScriptExecutor;
    }

    public List<Trip> getTripsByDate(LocalDate date) {
        return tripRepository.findByServiceDateAndStatus(date, TripStatus.SCHEDULED);
    }

    public Trip getTripById(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new InvalidSegmentException("Trip not found: " + tripId));
    }

    /**
     * Factory pattern: Schedules a Trip, generates its physical seat rows (1..N),
     * and initializes its Redis bitmap keys consistently.
     */
    @Transactional
    public Trip scheduleTrip(Long routeId, Long busId, LocalDate serviceDate) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new InvalidSegmentException("Route not found: " + routeId));
        Bus bus = busRepository.findById(busId)
                .orElseThrow(() -> new InvalidSegmentException("Bus not found: " + busId));

        Trip trip = new Trip(route, serviceDate, bus, TripStatus.SCHEDULED);
        trip = tripRepository.save(trip);

        int seatCount = bus.getSeatCount();
        for (int i = 1; i <= seatCount; i++) {
            Seat seat = new Seat(trip, i);
            seatRepository.save(seat);
            trip.addSeat(seat);
        }

        // Initialize Redis bitmaps for this trip (all free initially)
        redisScriptExecutor.rebuildTripBitmaps(trip.getTripId(), route.getTotalStops(), seatCount, Collections.emptyList());

        return trip;
    }
}
