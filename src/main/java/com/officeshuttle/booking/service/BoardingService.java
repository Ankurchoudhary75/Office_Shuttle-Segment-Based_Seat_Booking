package com.officeshuttle.booking.service;

import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.RedisScriptExecutor;
import com.officeshuttle.booking.engine.Segment;
import com.officeshuttle.booking.exception.*;
import com.officeshuttle.booking.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Handles Driver Check-In, No-Show lifecycle, Mid-Route Interval Trimming,
 * and Bus Reassignment Re-accommodation.
 */
@Service
public class BoardingService {

    private static final Logger log = LoggerFactory.getLogger(BoardingService.class);

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final SeatRepository seatRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisScriptExecutor redisScriptExecutor;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;

    private final Counter checkInCounter;
    private final Counter noShowCounter;
    private final Counter intervalTrimmedCounter;

    public BoardingService(
            BookingRepository bookingRepository,
            TripRepository tripRepository,
            BusRepository busRepository,
            SeatRepository seatRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository,
            RedisScriptExecutor redisScriptExecutor,
            WaitlistService waitlistService,
            NotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.tripRepository = tripRepository;
        this.busRepository = busRepository;
        this.seatRepository = seatRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisScriptExecutor = redisScriptExecutor;
        this.waitlistService = waitlistService;
        this.notificationService = notificationService;

        this.checkInCounter = Counter.builder("passenger_checkins_total").register(meterRegistry);
        this.noShowCounter = Counter.builder("passenger_noshows_total").register(meterRegistry);
        this.intervalTrimmedCounter = Counter.builder("booking_interval_trims_total").register(meterRegistry);
    }

    /**
     * Driver / QR Check-in at stop.
     */
    @Transactional
    public Booking checkIn(Long bookingId, Long driverUserId, int currentStopIdx) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new InvalidSegmentException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot check-in booking with status: " + booking.getStatus());
        }

        // Validate boarding stop matches
        if (booking.getFromStopIdx() != currentStopIdx) {
            throw new InvalidSegmentException(String.format(
                    "Mismatched boarding stop: booking starts at stop index %d, current stop is %d",
                    booking.getFromStopIdx(), currentStopIdx
            ));
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking = bookingRepository.save(booking);

        checkInCounter.increment();

        String payload = String.format("{\"bookingId\":%d,\"tripId\":%d,\"stopIdx\":%d,\"status\":\"CHECKED_IN\"}",
                bookingId, booking.getTrip().getTripId(), currentStopIdx);
        outboxEventRepository.save(new OutboxEvent(String.valueOf(bookingId), "PASSENGER_CHECKED_IN", payload));
        auditLogRepository.save(new AuditLog("BOOKING", "CHECK_IN", driverUserId, payload));

        return booking;
    }

    /**
     * No-Show Grace Period Expiration:
     * Transitions un-checked-in booking to NO_SHOW.
     * Downstream remaining legs are released back to availability engine and evaluated for waitlist promotion.
     */
    @Transactional
    public Booking processNoShow(Long bookingId, int departedStopIdx) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new InvalidSegmentException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return booking;
        }

        booking.setStatus(BookingStatus.NO_SHOW);
        booking = bookingRepository.save(booking);
        noShowCounter.increment();

        Long tripId = booking.getTrip().getTripId();
        int seatNo = booking.getSeat().getSeatNo();

        // Release downstream legs only: [departedStopIdx + 1, toStopIdx)
        int downstreamStart = Math.max(booking.getFromStopIdx(), departedStopIdx);
        if (downstreamStart < booking.getToStopIdx()) {
            Segment downstreamSegment = Segment.of(downstreamStart, booking.getToStopIdx());
            redisScriptExecutor.releaseSegment(tripId, seatNo, downstreamSegment);
            log.info("Released downstream legs {} for no-show booking #{}", downstreamSegment, bookingId);

            // Trigger waitlist promotion on freed downstream legs
            waitlistService.runPromotionSweep(tripId);
        }

        String payload = String.format("{\"bookingId\":%d,\"tripId\":%d,\"seatNo\":%d,\"status\":\"NO_SHOW\"}",
                bookingId, tripId, seatNo);
        outboxEventRepository.save(new OutboxEvent(String.valueOf(bookingId), "PASSENGER_NO_SHOW", payload));
        auditLogRepository.save(new AuditLog("BOOKING", "NO_SHOW", null, payload));

        return booking;
    }

    /**
     * Mid-Route Boarding Change (Interval Trimming):
     * e.g. Changes [0, 3) to [1, 3), releasing [0, 1) back into the availability engine.
     */
    @Transactional
    public Booking trimBookingInterval(Long bookingId, Long requestingUserId, int newFromStopIdx) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new InvalidSegmentException("Booking not found: " + bookingId));

        if (!booking.getUser().getUserId().equals(requestingUserId) && requestingUserId != null) {
            throw new UnauthorizedActionException("You are not authorized to modify this booking");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be trimmed");
        }

        if (newFromStopIdx <= booking.getFromStopIdx() || newFromStopIdx >= booking.getToStopIdx()) {
            throw new InvalidSegmentException(String.format(
                    "Invalid trim: newFromStopIdx (%d) must be greater than current (%d) and less than toStopIdx (%d)",
                    newFromStopIdx, booking.getFromStopIdx(), booking.getToStopIdx()
            ));
        }

        int oldFrom = booking.getFromStopIdx();
        Segment trimmedSegment = Segment.of(oldFrom, newFromStopIdx);

        booking.setFromStopIdx(newFromStopIdx);
        booking = bookingRepository.save(booking);

        Long tripId = booking.getTrip().getTripId();
        int seatNo = booking.getSeat().getSeatNo();

        // Release the trimmed segment on Redis
        redisScriptExecutor.releaseSegment(tripId, seatNo, trimmedSegment);

        intervalTrimmedCounter.increment();

        String payload = String.format("{\"bookingId\":%d,\"tripId\":%d,\"freedSegment\":\"%s\",\"newSegment\":\"[%d,%d)\"}",
                bookingId, tripId, trimmedSegment, newFromStopIdx, booking.getToStopIdx());
        outboxEventRepository.save(new OutboxEvent(String.valueOf(bookingId), "BOOKING_INTERVAL_TRIMMED", payload));
        auditLogRepository.save(new AuditLog("BOOKING", "TRIM_INTERVAL", requestingUserId, payload));

        // Sweep waitlist to immediately fill the newly freed sub-range
        waitlistService.runPromotionSweep(tripId);

        notificationService.notifyAllChannels(
                booking.getUser().getEmail(),
                "Boarding Stop Updated",
                String.format("Your booking #%d boarding stop is now stop index %d. Freed interval: %s",
                        bookingId, newFromStopIdx, trimmedSegment)
        );

        return booking;
    }

    /**
     * Bus Reassignment & Re-accommodation:
     * Moves a trip to a replacement bus. If replacement bus is smaller,
     * retains earliest bookings and re-accommodates excess passengers to priority waitlist.
     */
    @Transactional
    public Trip reassignBus(Long tripId, Long newBusId, Long operatorUserId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new InvalidSegmentException("Trip not found: " + tripId));
        Bus newBus = busRepository.findById(newBusId)
                .orElseThrow(() -> new InvalidSegmentException("New bus not found: " + newBusId));

        int newCapacity = newBus.getSeatCount();
        List<Seat> currentSeats = seatRepository.findByTrip_TripIdOrderBySeatNoAsc(tripId);
        int currentCapacity = currentSeats.size();

        if (newCapacity < currentCapacity) {
            // Handle down-sizing re-accommodation
            List<Booking> activeBookings = bookingRepository.findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);
            activeBookings.sort(Comparator.comparing(Booking::getCreatedAt));

            // Seats > newCapacity need re-accommodation
            for (Booking b : activeBookings) {
                if (b.getSeat().getSeatNo() > newCapacity) {
                    b.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(b);

                    // Move to priority waitlist
                    waitlistService.joinWaitlist(tripId, b.getUser().getUserId(), b.getFromStopIdx(), b.getToStopIdx());

                    notificationService.notifyAllChannels(
                            b.getUser().getEmail(),
                            "Trip Bus Resized - Re-accommodation Notice",
                            String.format("Due to vehicle replacement, booking #%d has been moved to priority waitlist.", b.getBookingId())
                    );
                }
            }
        } else if (newCapacity > currentCapacity) {
            // Add new seat rows for increased capacity
            for (int s = currentCapacity + 1; s <= newCapacity; s++) {
                Seat seat = new Seat(trip, s);
                seatRepository.save(seat);
            }
        }

        trip.setBus(newBus);
        trip = tripRepository.save(trip);

        // Rebuild Redis Bitmaps for the new bus capacity
        List<Booking> remainingActive = bookingRepository.findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);
        redisScriptExecutor.rebuildTripBitmaps(tripId, trip.getRoute().getTotalStops(), newCapacity, remainingActive);

        // Run waitlist promotion sweep if capacity increased
        if (newCapacity > currentCapacity) {
            waitlistService.runPromotionSweep(tripId);
        }

        String payload = String.format("{\"tripId\":%d,\"oldBusCapacity\":%d,\"newBusCapacity\":%d}",
                tripId, currentCapacity, newCapacity);
        outboxEventRepository.save(new OutboxEvent(String.valueOf(tripId), "TRIP_BUS_REASSIGNED", payload));
        auditLogRepository.save(new AuditLog("TRIP", "REASSIGN_BUS", operatorUserId, payload));

        return trip;
    }
}
