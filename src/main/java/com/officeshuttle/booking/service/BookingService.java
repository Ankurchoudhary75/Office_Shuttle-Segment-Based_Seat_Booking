package com.officeshuttle.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.SeatAllocationStrategy;
import com.officeshuttle.booking.engine.SeatMap;
import com.officeshuttle.booking.engine.Segment;
import com.officeshuttle.booking.engine.RedisScriptExecutor;
import com.officeshuttle.booking.exception.*;
import com.officeshuttle.booking.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisScriptExecutor redisScriptExecutor;
    private final SeatAllocationStrategy seatAllocationStrategy;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Metrics
    private final Counter confirmedCounter;
    private final Counter rejectedCounter;
    private final Counter raceConflictCounter;
    private final Timer bookingTimer;

    public BookingService(
            TripRepository tripRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            UserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository,
            RedisScriptExecutor redisScriptExecutor,
            SeatAllocationStrategy seatAllocationStrategy,
            NotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.tripRepository = tripRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisScriptExecutor = redisScriptExecutor;
        this.seatAllocationStrategy = seatAllocationStrategy;
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper();

        this.confirmedCounter = Counter.builder("booking_requests_total")
                .tag("result", "CONFIRMED")
                .description("Total confirmed bookings")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("booking_requests_total")
                .tag("result", "REJECTED")
                .description("Total rejected bookings")
                .register(meterRegistry);
        this.raceConflictCounter = Counter.builder("booking_race_conflicts_total")
                .description("Total concurrent race conflicts caught by Layer 2/3/4")
                .register(meterRegistry);
        this.bookingTimer = Timer.builder("booking_latency_seconds")
                .description("Latency of booking requests")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * Executes the Four-Layer Concurrency Defense Booking Saga:
     * Layer 1/2: Atomic Redis Lua candidate search & provisional reservation
     * Layer 3: DB Row Lock + recheck inside commit transaction
     * Layer 4: DB GiST exclusion constraint safety net
     */
    public Booking bookSegment(Long tripId, Long userId, int fromStopIdx, int toStopIdx, String idempotencyKey) {
        return bookingTimer.record(() -> {
            // Idempotency check: if key already exists, return the existing booking
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<Booking> existing = bookingRepository.findByIdempotencyKey(idempotencyKey);
                if (existing.isPresent()) {
                    log.info("Idempotent replay detected for key: {}", idempotencyKey);
                    return existing.get();
                }
            }

            // Validate segment
            Segment segment;
            try {
                segment = Segment.of(fromStopIdx, toStopIdx);
            } catch (IllegalArgumentException ex) {
                throw new InvalidSegmentException(ex.getMessage());
            }

            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new InvalidSegmentException("Trip not found with id: " + tripId));

            if (trip.getStatus() != TripStatus.SCHEDULED) {
                throw new TripNotOperationalException("Trip is not operational: " + trip.getStatus());
            }

            if (segment.getToIdx() > trip.getRoute().getTotalStops() - 1) {
                throw new InvalidSegmentException("Requested stop index exceeds route total stops");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UnauthorizedActionException("User not found: " + userId));

            int totalStops = trip.getRoute().getTotalStops();
            int seatCount = trip.getBus().getSeatCount();

            // --- Layer 2: Atomic Redis Lua Reservation ---
            int provisionalSeatNo = redisScriptExecutor.reserveSegment(tripId, seatCount, segment);

            if (provisionalSeatNo == -1) {
                rejectedCounter.increment();
                throw new SegmentUnavailableException(
                        "No seat is free for the entire requested segment.",
                        String.format("POST /api/v1/trips/%d/waitlist", tripId)
                );
            }

            // --- Layer 3 & Layer 4: PostgreSQL Commit Transaction ---
            try {
                return executeTransactionalCommit(trip, user, segment, provisionalSeatNo, idempotencyKey);
            } catch (Exception ex) {
                // Compensating action: release Redis bit if DB write failed
                if (provisionalSeatNo > 0) {
                    redisScriptExecutor.releaseSegment(tripId, provisionalSeatNo, segment);
                }
                throw ex;
            }
        });
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking executeTransactionalCommit(Trip trip, User user, Segment segment, int provisionalSeatNo, String idempotencyKey) {
        Long tripId = trip.getTripId();

        // If Redis was unavailable (-2), compute candidate using DB state & SeatMap
        int finalSeatNo = provisionalSeatNo;
        if (finalSeatNo <= 0) {
            List<Booking> confirmedBookings = bookingRepository.findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);
            SeatMap seatMap = new SeatMap(trip.getRoute().getTotalStops(), trip.getBus().getSeatCount());
            for (Booking b : confirmedBookings) {
                seatMap.reserve(b.getSeat().getSeatNo(), Segment.of(b.getFromStopIdx(), b.getToStopIdx()));
            }
            BitSet candidates = seatMap.getQualifyingSeats(segment);
            finalSeatNo = seatAllocationStrategy.chooseSeat(candidates, seatMap, segment);
            if (finalSeatNo == -1) {
                rejectedCounter.increment();
                throw new SegmentUnavailableException(
                        "No seat is free for the entire requested segment.",
                        String.format("POST /api/v1/trips/%d/waitlist", tripId)
                );
            }
        }

        // Layer 3: Authoritative re-validation against PostgreSQL
        Seat seat = seatRepository.findByTrip_TripIdAndSeatNo(tripId, finalSeatNo)
                .orElseThrow(() -> new IllegalStateException("Seat not found for trip: " + tripId + ", seatNo: " + finalSeatNo));

        List<Booking> overlapping = bookingRepository.findOverlappingBookings(
                seat.getSeatId(), segment.getFromIdx(), segment.getToIdx()
        );

        if (!overlapping.isEmpty()) {
            raceConflictCounter.increment();
            log.warn("Layer 3 detected race conflict on seat {} for segment {}", finalSeatNo, segment);
            throw new SegmentUnavailableException(
                    "Seat conflict occurred during authoritative check.",
                    String.format("POST /api/v1/trips/%d/waitlist", tripId)
            );
        }

        // Layer 4: Insert booking protected by GiST exclusion constraint
        try {
            Booking booking = new Booking(trip, seat, user, segment.getFromIdx(), segment.getToIdx(), idempotencyKey);
            booking = bookingRepository.saveAndFlush(booking);

            // Transactional Outbox Event
            String payload = String.format("{\"bookingId\":%d,\"tripId\":%d,\"userId\":%d,\"seatNo\":%d,\"from\":%d,\"to\":%d}",
                    booking.getBookingId(), tripId, user.getUserId(), finalSeatNo, segment.getFromIdx(), segment.getToIdx());
            OutboxEvent outboxEvent = new OutboxEvent(String.valueOf(booking.getBookingId()), "BOOKING_CONFIRMED", payload);
            outboxEventRepository.save(outboxEvent);

            // Audit Log
            AuditLog auditLog = new AuditLog("BOOKING", "CREATE", user.getUserId(), payload);
            auditLogRepository.save(auditLog);

            confirmedCounter.increment();

            // Notification
            notificationService.notifyAllChannels(
                    user.getEmail(),
                    "Shuttle Booking Confirmed",
                    String.format("Booking #%d confirmed for Seat %d (Segment: %s)", booking.getBookingId(), finalSeatNo, segment)
            );

            return booking;
        } catch (DataIntegrityViolationException ex) {
            raceConflictCounter.increment();
            log.error("Layer 4 GiST exclusion constraint prevented double-booking: {}", ex.getMessage());
            throw new SegmentUnavailableException(
                    "Double-booking prevented by database exclusion constraint.",
                    String.format("POST /api/v1/trips/%d/waitlist", tripId)
            );
        }
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new InvalidSegmentException("Booking not found: " + bookingId));

        if (!booking.getUser().getUserId().equals(requestingUserId) && requestingUserId != null) {
            throw new UnauthorizedActionException("You are not authorized to cancel this booking");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be cancelled. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Segment segment = Segment.of(booking.getFromStopIdx(), booking.getToStopIdx());
        int seatNo = booking.getSeat().getSeatNo();
        Long tripId = booking.getTrip().getTripId();

        // Release on Redis
        redisScriptExecutor.releaseSegment(tripId, seatNo, segment);

        // Outbox & Audit
        String payload = String.format("{\"bookingId\":%d,\"tripId\":%d,\"seatNo\":%d,\"freedSegment\":\"%s\"}",
                bookingId, tripId, seatNo, segment);
        outboxEventRepository.save(new OutboxEvent(String.valueOf(bookingId), "BOOKING_CANCELLED", payload));
        auditLogRepository.save(new AuditLog("BOOKING", "CANCEL", requestingUserId, payload));

        notificationService.notifyAllChannels(
                booking.getUser().getEmail(),
                "Shuttle Booking Cancelled",
                String.format("Your booking #%d has been cancelled. Seat %d released.", bookingId, seatNo)
        );

        return booking;
    }
}
