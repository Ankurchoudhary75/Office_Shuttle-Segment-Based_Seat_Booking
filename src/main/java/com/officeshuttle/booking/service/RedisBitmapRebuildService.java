package com.officeshuttle.booking.service;

import com.officeshuttle.booking.domain.Booking;
import com.officeshuttle.booking.domain.BookingStatus;
import com.officeshuttle.booking.domain.Trip;
import com.officeshuttle.booking.domain.TripStatus;
import com.officeshuttle.booking.engine.RedisScriptExecutor;
import com.officeshuttle.booking.repository.BookingRepository;
import com.officeshuttle.booking.repository.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Fault Recovery: Reconstructs Redis bitmaps from PostgreSQL on startup,
 * and runs periodic background reconciliation to detect and self-heal any drift.
 */
@Service
public class RedisBitmapRebuildService {

    private static final Logger log = LoggerFactory.getLogger(RedisBitmapRebuildService.class);

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final RedisScriptExecutor redisScriptExecutor;

    public RedisBitmapRebuildService(
            TripRepository tripRepository,
            BookingRepository bookingRepository,
            RedisScriptExecutor redisScriptExecutor) {
        this.tripRepository = tripRepository;
        this.bookingRepository = bookingRepository;
        this.redisScriptExecutor = redisScriptExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void onApplicationReady() {
        log.info("Initializing / rebuilding Redis bitmaps for active and upcoming trips...");
        rebuildActiveTrips();
    }

    /**
     * Periodic reconciliation job: runs every 5 minutes to ensure Redis bitmaps match PostgreSQL.
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional(readOnly = true)
    public void scheduledReconciliation() {
        rebuildActiveTrips();
    }

    public void rebuildActiveTrips() {
        List<Trip> upcomingTrips = tripRepository.findByServiceDateAndStatus(
                LocalDate.now(), TripStatus.SCHEDULED
        );
        for (Trip trip : upcomingTrips) {
            rebuildTrip(trip.getTripId());
        }
    }

    @Transactional(readOnly = true)
    public void rebuildTrip(Long tripId) {
        tripRepository.findById(tripId).ifPresent(trip -> {
            List<Booking> confirmedBookings = bookingRepository.findByTrip_TripIdAndStatus(
                    tripId, BookingStatus.CONFIRMED
            );
            int totalStops = trip.getRoute().getTotalStops();
            int seatCount = trip.getBus().getSeatCount();
            redisScriptExecutor.rebuildTripBitmaps(tripId, totalStops, seatCount, confirmedBookings);
        });
    }
}
