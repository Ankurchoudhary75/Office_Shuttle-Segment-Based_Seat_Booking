# 🚌 Office Shuttle: Segment-Based Seat Booking System

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)]()
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)]()
[![Observability](https://img.shields.io/badge/Prometheus%20%2B%20Grafana-Ready-purple.svg)]()

> **"One seat, many journeys — made correct by the database, made fast by bitwise arithmetic."**

---

## 📌 Problem Summary

A traditional bus booking system treats a seat as a single binary flag (occupied vs. free for the entire route). On a multi-stop shuttle route ($A \rightarrow B \rightarrow C \rightarrow D$), this wastes massive capacity. A passenger traveling $A \rightarrow B$ and a passenger traveling $B \rightarrow D$ can safely share the exact same physical seat because their journeys never share a common road leg.

This project delivers a **segment-based seat booking system** where every reservation is modeled as a half-open interval $[X_s, X_d)$, availability queries execute in $O(1)$ time via bitwise machine words, and concurrency correctness is guaranteed by a **Four-Layer Defense Stack**.

---

## 🛡️ Four-Layer Concurrency Defense

```
[ Request: Book B -> D ]
           │
           ▼
┌────────────────────────────────────────────────────────┐
│ Layer 1: In-Memory / Redis Bitmap Filter               │
│ • Column-wise bitsets find all qualifying seats in <1µs│
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ Layer 2: Atomic Redis Lua Script                       │
│ • Indivisible check-and-reserve closes race at cache   │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ Layer 3: PostgreSQL Row-Level Lock & Recheck           │
│ • SELECT ... FOR UPDATE SKIP LOCKED inside ACID txn    │
└──────────────────────────┬─────────────────────────────┘
                           ▼
┌────────────────────────────────────────────────────────┐
│ Layer 4: PostgreSQL GiST Exclusion Constraint          │
│ • EXCLUDE USING gist (seat_id WITH =, int4range &&)    │
│ • Physically rejects conflicting double-booking        │
└────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start & Docker Deployment

### 1. Run via Docker Compose (Recommended)

Start PostgreSQL, Redis, Spring Boot Application, Prometheus, and Grafana with one command:

```bash
docker compose up --build
```

### Services Access:
- **API Server**: `http://localhost:8080`
- **Prometheus Metrics**: `http://localhost:9090` (Scraping `/actuator/prometheus`)
- **Grafana Dashboard**: `http://localhost:3000` (User: `admin` / Password: `admin`)

---

### 2. Local Development Run

Ensure local PostgreSQL (`port 5432`) and Redis (`port 6379`) are running:

```bash
# Run database migrations and test suite
mvn clean test

# Start the Spring Boot application
mvn spring-boot:run
```

---

## ⚡ Core Engine & Bitwise Model

- **Stops & Legs**: A route with $M$ stops contains $M - 1$ legs. Stop indices: $A=0, B=1, C=2, D=3$.
- **Interval Arithmetic**: Journey $B \rightarrow D$ covers legs 1 and 2 (interval $[1, 3)$).
- **Bitmask Generation**:
  $$\text{rangeMask}(X_s, X_d) = ((1 \ll (X_d - X_s)) - 1) \ll X_s$$
- **Seat Weaving**: $A \rightarrow B$ ($[0,1)$, mask `001`) and $B \rightarrow D$ ($[1,3)$, mask `110`) have `(001 & 110) == 0`. They fit on the same seat without conflicts!

---

## 🎯 Seat Allocation & Promotion Strategies

- **Best-Fit Allocation (Default)**: Selects the qualifying seat that leaves the smallest remaining free capacity after the booking, packing the bus efficiently and preventing seat fragmentation.
- **First-Fit Allocation**: Pluggable alternative picking the lowest seat index.
- **FCFS-Among-Eligible Waitlist Promotion**: Scans waitlist entries in strict monotonic sequence order. When a booking is cancelled, auto-promotes eligible passengers and continues scanning to satisfy multiple disjoint journeys from a single cancellation.
- **Combinatorial Promotion**: Optional policy pairing complementary disjoint requests (e.g. $A \rightarrow B$ and $B \rightarrow D$) to fill a freed full-route segment ($A \rightarrow D$).

---

## 🏢 Real-World Edge Cases Handled

1. **Driver QR Check-In**: Validates boarding at the exact scheduled stop index (`fromStopIdx`), rejecting mismatched boarding attempts.
2. **No-Show Downstream Seat Reclamation**: If a passenger fails to check in past the grace window, their status transitions to `NO_SHOW`. Only remaining downstream legs ($[departedStop, toStop)$) are released back for waitlist promotion; elapsed legs are never resold.
3. **Mid-Route Interval Trimming**: Passengers can modify their boarding stop (e.g. $[0, 3) \rightarrow [1, 3)$), releasing the trimmed range $[0, 1)$ immediately for downstream passenger booking.
4. **Vehicle Breakdown Re-accommodation**: When a trip is swapped to a smaller replacement bus, the system keeps the earliest-booked passengers seated and automatically moves excess passengers to a priority waitlist.

---

## 📊 Postman API Collection

Import the included collection from `postman/office_shuttle_api.postman_collection.json`:

| Method | Endpoint | Description | Role Required |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Authenticate user & get JWT token | Public |
| `POST` | `/api/v1/routes` | Create route with ordered stops | `ADMIN` |
| `POST` | `/api/v1/trips` | Schedule trip for route + bus + date | `ADMIN` |
| `GET` | `/api/v1/trips/{id}/availability?from=...&to=...` | Query qualifying seats & utilization ratio | `EMPLOYEE` |
| `POST` | `/api/v1/trips/{id}/bookings` | Book a segment (`Idempotency-Key` header) | `EMPLOYEE` |
| `POST` | `/api/v1/bookings/{id}/cancel` | Cancel booking & trigger waitlist sweep | `EMPLOYEE` |
| `PATCH` | `/api/v1/bookings/{id}/trim` | Trim boarding interval | `EMPLOYEE` |
| `POST` | `/api/v1/trips/{id}/waitlist` | Join waitlist for unavailable segment | `EMPLOYEE` |
| `POST` | `/api/v1/bookings/{id}/check-in` | Driver QR check-in at stop | `DRIVER` |
| `PATCH` | `/api/v1/trips/{id}/reassign-bus` | Reassign trip bus / re-accommodate | `OPERATOR` |

---

## 🧪 Comprehensive Test Suite

Run the full automated test suite:

```bash
mvn test
```

### Included Tests:
- **`SegmentTest`**: Boundary tests, half-open interval checks, touch-point disjoint validation.
- **`SeatMapTest`**: Bitwise candidate filtering, seat weaving, utilization calculations.
- **`SeatAllocationStrategyTest`**: Best-Fit vs First-Fit optimization behavior.
- **`PromotionPolicyTest`**: FCFS-among-eligible sweep and Combinatorial promotion pairing.
- **`ConcurrentBookingTest`**: Multi-threaded race condition stress test with $N$ threads competing for 1 seat.
- **`HighValueInvariantTest`**: Mathematical validation ensuring no two active bookings on any seat overlap.
- **`IntervalTrimmingTest`, `NoShowTest`, `BusReassignmentTest`**: Real-world edge case validation.

---

## 📈 Git Commit Progression

```
* 9e07362 test: comprehensive unit, concurrency & invariant test suite
* 795c476 feat: monitoring + dashboards + outbox pattern
* 4273e84 feat: REST controllers, DTOs & API endpoints
* e93e24b feat: auth + RBAC + idempotency filter & RFC 7807 errors
* fb5a796 feat: boarding, no-show and interval trimming edge cases
* f83b178 feat: FCFS and combinatorial waitlist promotion engine
* 7880f27 feat: four-layer concurrency defense booking service & transactional outbox
* dd96fef feat: atomic Redis Lua scripts & distributed availability cache
* 18eb15e feat: SeatMap bitmap engine + unit tests
* be6aec7 feat: route/stop schema + GIST exclusion constraint
* 2758944 chore: initialize Spring Boot 3 skeleton, dependencies, and configuration
```
