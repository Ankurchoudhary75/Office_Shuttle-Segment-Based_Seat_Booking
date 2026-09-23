package com.officeshuttle.booking.engine;

import com.officeshuttle.booking.domain.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Layer 2 Concurrency Defense: Executes atomic Redis Lua scripts for fast candidate
 * check-and-reserve on the hot path.
 */
@Component
public class RedisScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(RedisScriptExecutor.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> bookSegmentScript;
    private final RedisScript<Long> releaseSegmentScript;

    public RedisScriptExecutor(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("bookSegmentScript") RedisScript<Long> bookSegmentScript,
            @Qualifier("releaseSegmentScript") RedisScript<Long> releaseSegmentScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bookSegmentScript = bookSegmentScript;
        this.releaseSegmentScript = releaseSegmentScript;
    }

    public static String getLegKey(Long tripId, int legIndex) {
        return String.format("trip:%d:leg:%d:free", tripId, legIndex);
    }

    public static String getWaitlistZSetKey(Long tripId) {
        return String.format("trip:%d:waitlist:zset", tripId);
    }

    /**
     * Atomically checks availability and reserves a seat on Redis.
     * Returns 1-based seat number if successfully reserved, or -1 if unavailable.
     * Returns -2 if Redis is down (signals to fall back to Layer 3 DB-only path).
     */
    public int reserveSegment(Long tripId, int seatCount, Segment segment) {
        List<String> keys = new ArrayList<>();
        for (int i = segment.getFromIdx(); i < segment.getToIdx(); i++) {
            keys.add(getLegKey(tripId, i));
        }

        try {
            Long result = stringRedisTemplate.execute(
                bookSegmentScript,
                keys,
                String.valueOf(seatCount)
            );
            return result != null ? result.intValue() : -1;
        } catch (Exception ex) {
            log.warn("Redis Lua reservation failed (falling back to DB path): {}", ex.getMessage());
            return -2; // Redis failure fallback
        }
    }

    /**
     * Atomically releases a reserved segment on Redis.
     */
    public boolean releaseSegment(Long tripId, int seatNo, Segment segment) {
        List<String> keys = new ArrayList<>();
        for (int i = segment.getFromIdx(); i < segment.getToIdx(); i++) {
            keys.add(getLegKey(tripId, i));
        }

        try {
            int seatBit = seatNo - 1; // 0-based bit index
            Long result = stringRedisTemplate.execute(
                releaseSegmentScript,
                keys,
                String.valueOf(seatBit)
            );
            return result != null && result > 0;
        } catch (Exception ex) {
            log.warn("Redis release failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Reconstructs a Trip's Redis bitmaps from PostgreSQL confirmed bookings.
     * Used on startup, Redis node restart, or periodic reconciliation self-healing.
     */
    public void rebuildTripBitmaps(Long tripId, int totalStops, int seatCount, List<Booking> confirmedBookings) {
        try {
            SeatMap seatMap = new SeatMap(totalStops, seatCount);
            for (Booking booking : confirmedBookings) {
                Segment seg = Segment.of(booking.getFromStopIdx(), booking.getToStopIdx());
                seatMap.reserve(booking.getSeat().getSeatNo(), seg);
            }

            int legCount = totalStops - 1;
            for (int leg = 0; leg < legCount; leg++) {
                String legKey = getLegKey(tripId, leg);
                // Clear existing key
                stringRedisTemplate.delete(legKey);
                // Set bits for each seat
                for (int seat = 1; seat <= seatCount; seat++) {
                    int seatBit = seat - 1;
                    boolean isFree = seatMap.isSeatFree(seat, Segment.of(leg, leg + 1));
                    stringRedisTemplate.opsForValue().setBit(legKey, seatBit, isFree);
                }
            }
            log.info("Successfully rebuilt Redis bitmaps for trip {} (stops={}, seats={}, bookings={})",
                    tripId, totalStops, seatCount, confirmedBookings.size());
        } catch (Exception ex) {
            log.warn("Could not rebuild Redis bitmaps for trip {}: {}", tripId, ex.getMessage());
        }
    }

    /**
     * Push user to Redis Sorted Set for fast O(1) waitlist peek and ranking.
     */
    public void addWaitlistScore(Long tripId, Long userId, Long sequenceNo) {
        try {
            String zsetKey = getWaitlistZSetKey(tripId);
            stringRedisTemplate.opsForZSet().add(zsetKey, String.valueOf(userId), sequenceNo.doubleValue());
        } catch (Exception ex) {
            log.warn("Failed to add to Redis waitlist ZSET: {}", ex.getMessage());
        }
    }

    /**
     * Remove user from Redis waitlist ZSET.
     */
    public void removeWaitlistScore(Long tripId, Long userId) {
        try {
            String zsetKey = getWaitlistZSetKey(tripId);
            stringRedisTemplate.opsForZSet().remove(zsetKey, String.valueOf(userId));
        } catch (Exception ex) {
            log.warn("Failed to remove from Redis waitlist ZSET: {}", ex.getMessage());
        }
    }
}
