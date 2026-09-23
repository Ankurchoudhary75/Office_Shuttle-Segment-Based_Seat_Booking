package com.officeshuttle.booking.engine;

import java.util.BitSet;

/**
 * Encapsulates all bitmap arithmetic for a Trip.
 * Operates on half-open route intervals [fromIdx, toIdx) and route legs 0..M-2.
 * This class is the sole encapsulation boundary for raw bitwise manipulation.
 */
public class SeatMap {
    private final int totalStops;
    private final int legCount;
    private final int seatCount;

    // seatMasks[s]: bit i is 1 if leg i on seat s is occupied
    // seat numbers are 1-indexed internally mapped to 0-indexed array
    private final long[] seatMasks;

    // freeSeatsOnLeg[i]: transpose bitset where bit s is 1 if seat s (0-indexed) is FREE on leg i
    private final BitSet[] freeSeatsOnLeg;

    public SeatMap(int totalStops, int seatCount) {
        if (totalStops < 2) {
            throw new IllegalArgumentException("Route must have at least 2 stops");
        }
        if (totalStops > 65) {
            throw new IllegalArgumentException("Bitmap model supports up to 64 legs (65 stops) in a single 64-bit word");
        }
        if (seatCount <= 0) {
            throw new IllegalArgumentException("Seat count must be positive");
        }

        this.totalStops = totalStops;
        this.legCount = totalStops - 1;
        this.seatCount = seatCount;

        this.seatMasks = new long[seatCount];
        this.freeSeatsOnLeg = new BitSet[legCount];

        for (int i = 0; i < legCount; i++) {
            this.freeSeatsOnLeg[i] = new BitSet(seatCount);
            // Initially all seats are free across all legs
            this.freeSeatsOnLeg[i].set(0, seatCount);
        }
    }

    /**
     * Compute a bitmask for the interval [Xs, Xd).
     * Occupies legs Xs through Xd - 1.
     */
    public static long rangeMask(int Xs, int Xd) {
        return ((1L << (Xd - Xs)) - 1) << Xs;
    }

    /**
     * Check if a 1-based seat number is free on the given segment.
     */
    public boolean isSeatFree(int seatNo, Segment segment) {
        validateSeatNo(seatNo);
        validateSegment(segment);
        int s = seatNo - 1;
        long mask = segment.getRangeMask();
        return (seatMasks[s] & mask) == 0L;
    }

    /**
     * Finds every qualifying seat for the requested segment via column-wise AND-reduction.
     * Returns a BitSet where bit s is 1 if seat (s + 1) is free on all legs in the segment.
     * Runs in O((Xd - Xs) * seatCount / 64) -> practically O(1).
     */
    public BitSet getQualifyingSeats(Segment segment) {
        validateSegment(segment);
        int Xs = segment.getFromIdx();
        int Xd = segment.getToIdx();

        BitSet result = (BitSet) freeSeatsOnLeg[Xs].clone();
        for (int i = Xs + 1; i < Xd; i++) {
            result.and(freeSeatsOnLeg[i]);
        }
        return result;
    }

    /**
     * Reserve the segment on a 1-based seat number.
     */
    public synchronized void reserve(int seatNo, Segment segment) {
        validateSeatNo(seatNo);
        validateSegment(segment);
        if (!isSeatFree(seatNo, segment)) {
            throw new IllegalStateException(
                String.format("Seat %d is already occupied on segment %s", seatNo, segment)
            );
        }

        int s = seatNo - 1;
        long mask = segment.getRangeMask();
        seatMasks[s] |= mask;

        for (int i = segment.getFromIdx(); i < segment.getToIdx(); i++) {
            freeSeatsOnLeg[i].clear(s);
        }
    }

    /**
     * Release the segment on a 1-based seat number (e.g. on cancellation or interval trimming).
     */
    public synchronized void release(int seatNo, Segment segment) {
        validateSeatNo(seatNo);
        validateSegment(segment);

        int s = seatNo - 1;
        long mask = segment.getRangeMask();
        seatMasks[s] &= ~mask;

        for (int i = segment.getFromIdx(); i < segment.getToIdx(); i++) {
            freeSeatsOnLeg[i].set(s);
        }
    }

    /**
     * Returns the remaining free leg count on a 1-based seat number.
     */
    public int getRemainingFreeLegs(int seatNo) {
        validateSeatNo(seatNo);
        int s = seatNo - 1;
        int occupiedLegs = Long.bitCount(seatMasks[s]);
        return legCount - occupiedLegs;
    }

    /**
     * Total booked legs divided by total possible legs (seatCount * legCount).
     */
    public double getUtilizationRatio() {
        int totalOccupiedLegs = 0;
        for (int s = 0; s < seatCount; s++) {
            totalOccupiedLegs += Long.bitCount(seatMasks[s]);
        }
        return (double) totalOccupiedLegs / (seatCount * legCount);
    }

    public long getSeatMask(int seatNo) {
        validateSeatNo(seatNo);
        return seatMasks[seatNo - 1];
    }

    public int getTotalStops() { return totalStops; }
    public int getLegCount() { return legCount; }
    public int getSeatCount() { return seatCount; }

    private void validateSeatNo(int seatNo) {
        if (seatNo < 1 || seatNo > seatCount) {
            throw new IllegalArgumentException(
                String.format("Seat number %d out of range [1, %d]", seatNo, seatCount)
            );
        }
    }

    private void validateSegment(Segment segment) {
        if (segment.getToIdx() > totalStops - 1 && segment.getToIdx() > legCount) {
            // Note: Stop indices are 0..totalStops-1. A segment to stop idx M-1 has toIdx = M-1.
            if (segment.getToIdx() > totalStops - 1) {
                throw new IllegalArgumentException(
                    String.format("Segment %s exceeds route total stops (%d)", segment, totalStops)
                );
            }
        }
    }
}
