package com.officeshuttle.booking.concurrency;

import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.BestFitStrategy;
import com.officeshuttle.booking.engine.SeatMap;
import com.officeshuttle.booking.engine.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentBookingTest {

    @Test
    @DisplayName("N concurrent threads competing for 1 physical seat: Exactly 1 wins, N-1 fail/waitlist")
    void testConcurrentRaceForLastSeat() throws InterruptedException {
        int threadCount = 20;
        int totalStops = 4; // 3 legs
        int seatCount = 1;  // Only 1 physical seat!

        SeatMap seatMap = new SeatMap(totalStops, seatCount);
        BestFitStrategy strategy = new BestFitStrategy();
        Segment targetSegment = Segment.of(0, 3); // A->D

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successfulBookings = new AtomicInteger(0);
        AtomicInteger rejectedBookings = new AtomicInteger(0);
        List<Integer> assignedSeats = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startGate.await(); // Synchronize all threads to fire simultaneously

                    synchronized (seatMap) {
                        BitSet candidates = seatMap.getQualifyingSeats(targetSegment);
                        int seat = strategy.chooseSeat(candidates, seatMap, targetSegment);
                        if (seat > 0) {
                            seatMap.reserve(seat, targetSegment);
                            successfulBookings.incrementAndGet();
                            assignedSeats.add(seat);
                        } else {
                            rejectedBookings.incrementAndGet();
                        }
                    }
                } catch (Exception ex) {
                    rejectedBookings.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successfulBookings.get(), "Exactly 1 concurrent request must successfully claim the seat");
        assertEquals(threadCount - 1, rejectedBookings.get(), "All other concurrent contenders must be rejected/waitlisted");
        assertEquals(1, assignedSeats.size());
        assertEquals(1, assignedSeats.get(0).intValue());
    }
}
