package com.officeshuttle.booking.engine;

import java.util.Objects;

/**
 * Value object representing a route segment as a half-open interval [fromIdx, toIdx).
 * For example, Stop 0 to Stop 2 occupies road legs 0 and 1.
 */
public final class Segment {
    private final int fromIdx;
    private final int toIdx;

    public Segment(int fromIdx, int toIdx) {
        if (fromIdx < 0) {
            throw new IllegalArgumentException("fromIdx must be non-negative: " + fromIdx);
        }
        if (fromIdx >= toIdx) {
            throw new IllegalArgumentException(
                String.format("Invalid segment interval: fromIdx (%d) must be strictly less than toIdx (%d)", fromIdx, toIdx)
            );
        }
        this.fromIdx = fromIdx;
        this.toIdx = toIdx;
    }

    public static Segment of(int fromIdx, int toIdx) {
        return new Segment(fromIdx, toIdx);
    }

    public int getFromIdx() {
        return fromIdx;
    }

    public int getToIdx() {
        return toIdx;
    }

    public int getLegCount() {
        return toIdx - fromIdx;
    }

    /**
     * Checks if this segment overlaps with another segment.
     * In half-open interval arithmetic [Xs, Xd) and [Ys, Yd), they conflict iff:
     * Xs < Yd AND Ys < Xd.
     * Touching at a stop (e.g. [0, 1) and [1, 3)) naturally evaluates to false.
     */
    public boolean overlaps(Segment other) {
        Objects.requireNonNull(other, "other segment must not be null");
        return this.fromIdx < other.toIdx && other.fromIdx < this.toIdx;
    }

    /**
     * Generates a 64-bit mask for this segment's legs.
     * e.g. from 1 to 3 -> legs 1, 2 -> bits 1 and 2 set -> ( (1L << 2) - 1 ) << 1 = 3 << 1 = 6 (binary 110)
     */
    public long getRangeMask() {
        return ((1L << (toIdx - fromIdx)) - 1) << fromIdx;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Segment segment)) return false;
        return fromIdx == segment.fromIdx && toIdx == segment.toIdx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromIdx, toIdx);
    }

    @Override
    public String toString() {
        return String.format("[%d, %d)", fromIdx, toIdx);
    }
}
