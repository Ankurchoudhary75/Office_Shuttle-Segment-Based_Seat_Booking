package com.officeshuttle.booking.service;

import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.*;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.exception.UnauthorizedActionException;
import com.officeshuttle.booking.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisScriptExecutor redisScriptExecutor;
    private final PromotionPolicy promotionPolicy;
    private final SeatAllocationStrategy seatAllocationStrategy;
    private final NotificationService notificationService;

    // Metrics
    private final Counter waitlistJoinedCounter;
    private final Counter promotionSuccessCounter;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            TripRepository tripRepository,
            UserRepository userRepository,
            SeatRepository seatRepository,
            BookingRepository bookingRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository,
            RedisScriptExecutor redisScriptExecutor,
            PromotionPolicy promotionPolicy,
            SeatAllocationStrategy seatAllocationStrategy,
            NotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.waitlistRepository = waitlistRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
        this.redisScriptExecutor = redisScriptExecutor;
        this.promotionPolicy = promotionPolicy;
        this.seatAllocationStrategy = seatAllocationStrategy;
        this.notificationService = notificationService;

        this.waitlistJoinedCounter = Counter.builder("booking_requests_total")
                .tag("result", "WAITLISTED")
                .description("Total waitlist joins")
                .register(meterRegistry);
        this.promotionSuccessCounter = Counter.builder("promotion_success_total")
                .description("Total auto-promoted waitlist passengers")
                .register(meterRegistry);
    }

    @Transactional
    public WaitlistEntry joinWaitlist(Long tripId, Long userId, int fromStopIdx, int toStopIdx) {
        Segment segment;
        try {
            segment = Segment.of(fromStopIdx, toStopIdx);
        } catch (IllegalArgumentException ex) {
            throw new InvalidSegmentException(ex.getMessage());
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new InvalidSegmentException("Trip not found: " + tripId));

        if (segment.getToIdx() > trip.getRoute().getTotalStops() - 1) {
            throw new InvalidSegmentException("Requested stop index exceeds route total stops");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedActionException("User not found: " + userId));

        // Check for existing active waitlist entry for this user and trip
        Optional<WaitlistEntry> existing = waitlistRepository.findByTrip_TripIdAndUser_UserIdAndStatus(
                tripId, userId, WaitlistStatus.WAITING
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        Long maxSeq = waitlistRepository.findMaxSequenceNoByTripId(tripId);
        Long sequenceNo = maxSeq + 1L;

        WaitlistEntry entry = new WaitlistEntry(trip, user, segment.getFromIdx(), segment.getToIdx(), sequenceNo);
        entry = waitlistRepository.save(entry);

        // Mirror in Redis Sorted Set for fast O(1) peek
        redisScriptExecutor.addWaitlistScore(tripId, userId, sequenceNo);

        // Outbox & Audit
        String payload = String.format("{\"waitlistId\":%d,\"tripId\":%d,\"userId\":%d,\"seq\":%d,\"segment\":\"%s\"}",
                entry.getWaitlistId(), tripId, userId, sequenceNo, segment);
        outboxEventRepository.save(new OutboxEvent(String.valueOf(entry.getWaitlistId()), "WAITLIST_JOINED", payload));
        auditLogRepository.save(new AuditLog("WAITLIST", "JOIN", userId, payload));

        waitlistJoinedCounter.increment();

        notificationService.notifyAllChannels(
                user.getEmail(),
                "Waitlist Confirmation",
                String.format("You are #%d on the waitlist for Trip %d (Segment: %s)", sequenceNo, tripId, segment)
        );

        return entry;
    }

    /**
     * Executes Smart Promotion Sweep:
     * 1. Constructs current SeatMap state from DB.
     * 2. Fetches all WAITING entries ordered by sequenceNo ascending.
     * 3. Evaluates promotable entries via PromotionPolicy (FCFS / Combinatorial).
     * 4. Converts eligible waitlist entries to CONFIRMED bookings.
     * 5. Syncs Redis bitmaps and outbox events.
     */
    @Transactional
    public List<Booking> runPromotionSweep(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) return Collections.emptyList();

        List<WaitlistEntry> waitingEntries = waitlistRepository
                .findByTrip_TripIdAndStatusOrderBySequenceNoAsc(tripId, WaitlistStatus.WAITING);

        if (waitingEntries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Booking> confirmedBookings = bookingRepository
                .findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);

        int totalStops = trip.getRoute().getTotalStops();
        int seatCount = trip.getBus().getSeatCount();
        SeatMap seatMap = new SeatMap(totalStops, seatCount);

        for (Booking b : confirmedBookings) {
            seatMap.reserve(b.getSeat().getSeatNo(), Segment.of(b.getFromStopIdx(), b.getToStopIdx()));
        }

        Map<WaitlistEntry, Integer> promotable = promotionPolicy.findPromotableEntries(
                waitingEntries, seatMap, seatAllocationStrategy
        );

        List<Booking> newlyConfirmed = new ArrayList<>();

        for (Map.Entry<WaitlistEntry, Integer> item : promotable.entrySet()) {
            WaitlistEntry entry = item.getKey();
            int seatNo = item.getValue();
            Segment segment = Segment.of(entry.getFromStopIdx(), entry.getToStopIdx());

            Seat seat = seatRepository.findByTrip_TripIdAndSeatNo(tripId, seatNo)
                    .orElseThrow(() -> new IllegalStateException("Seat not found: " + seatNo));

            // Generate unique idempotency key for auto-promotion
            String promoKey = String.format("promo-w%d-t%d", entry.getWaitlistId(), tripId);

            Booking booking = new Booking(trip, seat, entry.getUser(), segment.getFromIdx(), segment.getToIdx(), promoKey);
            booking = bookingRepository.save(booking);

            // Update waitlist entry status
            entry.setStatus(WaitlistStatus.PROMOTED);
            entry.setPromotedAt(OffsetDateTime.now());
            waitlistRepository.save(entry);

            // Update Redis ZSET
            redisScriptExecutor.removeWaitlistScore(tripId, entry.getUser().getUserId());

            // Outbox & Audit
            String payload = String.format("{\"bookingId\":%d,\"promotedWaitlistId\":%d,\"tripId\":%d,\"seatNo\":%d,\"segment\":\"%s\"}",
                    booking.getBookingId(), entry.getWaitlistId(), tripId, seatNo, segment);
            outboxEventRepository.save(new OutboxEvent(String.valueOf(booking.getBookingId()), "WAITLIST_PROMOTED", payload));
            auditLogRepository.save(new AuditLog("WAITLIST", "PROMOTE", entry.getUser().getUserId(), payload));

            promotionSuccessCounter.increment();
            newlyConfirmed.add(booking);

            notificationService.notifyAllChannels(
                    entry.getUser().getEmail(),
                    "Congratulations! Waitlist Promoted",
                    String.format("You have been promoted from the waitlist! Booking #%d confirmed for Seat %d.",
                            booking.getBookingId(), seatNo)
            );
        }

        // Resync Redis bitmaps with latest confirmed state
        if (!newlyConfirmed.isEmpty()) {
            List<Booking> allActive = bookingRepository.findByTrip_TripIdAndStatus(tripId, BookingStatus.CONFIRMED);
            redisScriptExecutor.rebuildTripBitmaps(tripId, totalStops, seatCount, allActive);
        }

        return newlyConfirmed;
    }
}
