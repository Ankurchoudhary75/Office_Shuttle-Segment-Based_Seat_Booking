# Office Shuttle: Segment-Based Seat Booking System Design

> *"One seat, many journeys — made correct by the database, made fast by bitwise arithmetic."*

## 1. Executive Summary

A bus-ticketing system that treats a seat as a single binary flag — booked or free for the whole trip — wastes capacity the moment the vehicle serves more than two stops. An office shuttle running $A \rightarrow B \rightarrow C \rightarrow D$ can legally sell seat #5 to one passenger for $A \rightarrow B$ and to a completely different passenger for $B \rightarrow D$, because the two journeys never share a road segment.

This backend system models every booking as a half-open stop interval $[X_s, X_d)$, makes seat availability an interval-overlap query rather than a boolean, and wraps the query in a **Four-Layer Concurrency Defense Stack**:

1. **In-Memory Bitmaps** for sub-microsecond candidate searches.
2. **Atomic Redis Lua Scripts** for indivisible check-and-reserve on the hot path.
3. **PostgreSQL Row-Level Locking** for authoritative ACID re-validation.
4. **PostgreSQL GiST Exclusion Constraints** (`int4range`) making double-booking physically impossible to persist.

Around this core, the system provides:
- **Best-Fit Seat Allocation Strategy** (minimizing bus fragmentation and packing seats tightly).
- **Fair, Utilization-Aware Waitlist Engine** with automated FCFS and Combinatorial promotion sweeps.
- **Real-World Edge Case Handling**: Driver QR Check-In, No-Show downstream seat reclamation, Mid-Route Interval Trimming, and Vehicle Breakdown Re-accommodation.
- **Enterprise Observability**: Micrometer / Prometheus metrics, Grafana dashboards, RFC 7807 problem details, and Transactional Outbox pattern.

---

## 2. Design Trade-offs Matrix

| Decision | Chosen Solution | Alternative Considered | Trade-off / Cost Rationale |
|---|---|---|---|
| **Availability Data Structure** | Per-leg Bitmaps | Segment Tree / Interval Tree | $O(1)$ bitwise operations, tiny memory footprint. Fits shuttle routes with up to 64 stops. Segment tree is kept as the documented scaling path for routes with thousands of stops. |
| **Concurrency Defense** | 4-Layer Defense Stack | Single-layer trust | Negligible overhead for absolute mathematical correctness that does not depend on any single layer being bug-free. |
| **Hot-Path Storage** | Redis alongside PostgreSQL | PostgreSQL-only | Sub-millisecond latency and atomic Lua scripting primitive; self-healed via startup rebuild and periodic reconciliation. |
| **Seat Allocation Heuristic** | Best-Fit Allocation | First-Fit Allocation | Higher bus utilization and less fragmentation by prioritizing seats already partially booked. |
| **Waitlist Policy** | FCFS-Among-Eligible | Pure Utilization Maximizing | Simple, fair, and explainable to employees while still auto-promoting multiple disjoint requests on single cancellation. |
| **Architecture Topology** | Modular Monolith | Microservices from Day 1 | Keeps booking, cancellation, and waitlist promotion within a single transactional boundary without distributed transaction complexity. |
