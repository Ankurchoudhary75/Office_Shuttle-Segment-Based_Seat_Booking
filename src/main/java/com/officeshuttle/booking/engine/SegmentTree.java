package com.officeshuttle.booking.engine;

/**
 * Segment Tree implementation tracking max booked count per range in O(log M).
 * Documented as the scaling architecture path for long-haul routes with hundreds
 * or thousands of stops (where word-level bitwise operations exceed 64-bit machine words).
 */
public class SegmentTree {
    private final int n; // Number of legs: totalStops - 1
    private final int capacity;
    private final int[] tree;
    private final int[] lazy;

    public SegmentTree(int legCount, int capacity) {
        this.n = legCount;
        this.capacity = capacity;
        this.tree = new int[4 * Math.max(1, legCount)];
        this.lazy = new int[4 * Math.max(1, legCount)];
    }

    private void applyLazy(int node, int start, int end) {
        if (lazy[node] != 0) {
            tree[node] += lazy[node];
            if (start != end) {
                lazy[2 * node] += lazy[node];
                lazy[2 * node + 1] += lazy[node];
            }
            lazy[node] = 0;
        }
    }

    public synchronized boolean isRangeAvailable(int ql, int qr) {
        if (ql >= qr || ql < 0 || qr > n) return false;
        int maxBooked = queryMax(1, 0, n - 1, ql, qr - 1);
        return maxBooked < capacity;
    }

    public synchronized boolean bookRange(int ql, int qr) {
        if (!isRangeAvailable(ql, qr)) return false;
        updateRange(1, 0, n - 1, ql, qr - 1, 1);
        return true;
    }

    public synchronized void releaseRange(int ql, int qr) {
        if (ql >= qr || ql < 0 || qr > n) return;
        updateRange(1, 0, n - 1, ql, qr - 1, -1);
    }

    private int queryMax(int node, int start, int end, int l, int r) {
        applyLazy(node, start, end);
        if (r < start || end < l) return 0;
        if (l <= start && end <= r) return tree[node];

        int mid = (start + end) / 2;
        int leftMax = queryMax(2 * node, start, mid, l, r);
        int rightMax = queryMax(2 * node + 1, mid + 1, end, l, r);
        return Math.max(leftMax, rightMax);
    }

    private void updateRange(int node, int start, int end, int l, int r, int val) {
        applyLazy(node, start, end);
        if (r < start || end < l) return;
        if (l <= start && end <= r) {
            lazy[node] += val;
            applyLazy(node, start, end);
            return;
        }

        int mid = (start + end) / 2;
        updateRange(2 * node, start, mid, l, r, val);
        updateRange(2 * node + 1, mid + 1, end, l, r, val);
        tree[node] = Math.max(tree[2 * node], tree[2 * node + 1]);
    }
}
