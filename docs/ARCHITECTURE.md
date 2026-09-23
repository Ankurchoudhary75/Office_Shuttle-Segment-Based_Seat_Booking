# System Architecture: Office Shuttle Segment-Based Booking

## 1. Modular Monolith Architecture

Booking, cancellation, waitlist management, and promotion must commit inside a unified transactional boundary. Splitting these into separately deployed microservices prematurely would trade away consistency guarantees in exchange for distributed transaction overhead.

```
 Client / Driver App (Web / Mobile / Postman)
                  │
                  ▼
         API Gateway / Security Filter
         (AuthN, RBAC, Idempotency-Key)
                  │
                  ▼
          Controller Layer
   (Route, Trip, Booking, Waitlist, Boarding)
                  │
                  ▼
         Application Services
  (BookingService, WaitlistService, BoardingService)
         │                         │
         ▼                         ▼
┌────────────────────────┐  ┌────────────────────────┐
│ Segment Availability   │  │ PostgreSQL Store       │
│ Engine (SeatMap Bitmap)│  │ (Durable Source of     │
│ Redis Cluster          │  │  Truth + GiST Lock)    │
└────────────────────────┘  └────────────────────────┘
                  │
                  ▼
       Transactional Outbox
                  │
                  ▼
      Prometheus & Grafana Observability
```

---

## 2. Four-Layer Concurrency Defense Stack

| Layer | Component | Mechanism | Latency | Role |
|---|---|---|---|---|
| **Layer 1** | In-Memory Bitmap | Column-wise bitsets (`freeSeatsOnLeg[i]`) | $<1\,\mu\text{s}$ | Fast pre-filtering |
| **Layer 2** | Redis Lua Engine | Indivisible `BITOP AND` + `BITPOS` + `SETBIT` | $\sim 1\text{ ms}$ | Atomic hot-path reservation |
| **Layer 3** | PostgreSQL Row Lock | `SELECT ... FOR UPDATE SKIP LOCKED` | $\sim 10\text{ ms}$ | Authoritative recheck |
| **Layer 4** | PostgreSQL GiST | `EXCLUDE USING gist (seat_id WITH =, int4range(...) WITH &&)` | Engine Level | Physical impossibility of double-booking |

---

## 3. Caching Hierarchy

- **Tier 1 (Hot Path)**: Seat bitmaps and Waitlist Sorted Sets live in Redis as the active working store.
- **Tier 2 (Semi-Static Read Cache)**: Route and stop topology cached with L1 Local + L2 Redis, invalidated on admin modification.
- **Eviction Strategy**: Redis operates `allkeys-lru` on Tier-2 keys only; Tier-1 bitmap keys are never evicted and are backed by AOF persistence and startup reconstruction from PostgreSQL.
