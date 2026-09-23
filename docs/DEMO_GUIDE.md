# 🎬 System Demonstration & Verification Guide

This document provides a step-by-step procedure to run, test, and verify the **Office Shuttle Segment-Based Seat Booking** system across all functional capabilities, concurrency invariants, and edge cases.

---

## 🚀 Part 1: Running the Complete System

### Option A: Using Docker Compose (Recommended)
Run the following command from the project root:
```bash
docker compose up --build
```
This automatically provisions and starts:
- **PostgreSQL 16** on `localhost:5432` (with `btree_gist` extension and Flyway migrations)
- **Redis 7** on `localhost:6379` (with AOF persistence and atomic Lua scripts)
- **Spring Boot 3 App** on `http://localhost:8080`
- **Prometheus** on `http://localhost:9090`
- **Grafana** on `http://localhost:3000` (Default credentials: `admin` / `admin`)

---

## 🧪 Part 2: Step-by-Step API Verification Workflow

Follow these sequential steps in Postman or Terminal via cURL:

### Step 1: Authentication & Token Generation
#### 1.1 Admin Authentication
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@shuttle.com",
    "password": "password123"
  }'
```
**Response (200 OK)**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "role": "ADMIN",
  "email": "admin@shuttle.com"
}
```

#### 1.2 Employee Authentication (Alice)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@company.com",
    "password": "password123"
  }'
```

---

### Step 2: Query Initial Segment Availability (A $\rightarrow$ C)
```bash
curl -X GET "http://localhost:8080/api/v1/trips/1/availability?from=Station%20A&to=City%20Center%20C" \
  -H "Authorization: Bearer <ALICE_JWT_TOKEN>"
```
**Response (200 OK)**:
```json
{
  "tripId": 1,
  "segment": { "from": "Station A", "to": "City Center C", "fromIdx": 0, "toIdx": 2 },
  "qualifyingSeats": [1, 2, 3],
  "availableCount": 3,
  "tripUtilizationRatio": 0.0
}
```

---

### Step 3: Segment Booking & "Seat Weaving" Proof

#### 3.1 Alice books Segment A $\rightarrow$ B on Trip 1
```bash
curl -X POST http://localhost:8080/api/v1/trips/1/bookings \
  -H "Authorization: Bearer <ALICE_JWT_TOKEN>" \
  -H "Idempotency-Key: idemp-alice-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "Station A",
    "toStop": "Tech Park B"
  }'
```
**Response (201 Created)**:
```json
{
  "bookingId": "bk_1",
  "tripId": 1,
  "seatNo": 1,
  "segment": { "from": "Station A", "to": "Tech Park B", "fromIdx": 0, "toIdx": 1 },
  "status": "CONFIRMED"
}
```
*(Alice is allocated **Seat 1** for leg 0).*

#### 3.2 Bob books Segment B $\rightarrow$ D on Trip 1
```bash
curl -X POST http://localhost:8080/api/v1/trips/1/bookings \
  -H "Authorization: Bearer <BOB_JWT_TOKEN>" \
  -H "Idempotency-Key: idemp-bob-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "Tech Park B",
    "toStop": "HQ Campus D"
  }'
```
**Response (201 Created)**:
```json
{
  "bookingId": "bk_2",
  "tripId": 1,
  "seatNo": 1,
  "segment": { "from": "Tech Park B", "to": "HQ Campus D", "fromIdx": 1, "toIdx": 3 },
  "status": "CONFIRMED"
}
```
**Technical Significance**: Bob is assigned **Seat 1** as well. Because $[0, 1)$ and $[1, 3)$ share no road segments, the bitmap engine (`BestFitStrategy`) packs both passengers onto Seat 1, leaving Seats 2 and 3 available for full-route journeys.

---

### Step 4: Overlap Conflict & RFC 7807 Error Envelope

When all physical seats are committed and a passenger requests an overlapping segment:
```bash
curl -X POST http://localhost:8080/api/v1/trips/1/bookings \
  -H "Authorization: Bearer <NEW_USER_JWT>" \
  -H "Idempotency-Key: idemp-fail-001" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "Station A",
    "toStop": "City Center C"
  }'
```
**Response (409 Conflict - RFC 7807 Standard)**:
```json
{
  "type": "https://api.shuttle/errors/segment-unavailable",
  "title": "Segment Unavailable",
  "status": 409,
  "detail": "No seat is free for the entire requested segment.",
  "traceId": "7f8b9e12-3456-4abc-9def-123456789abc",
  "instance": "/api/v1/trips/1/bookings",
  "waitlistOffer": {
    "action": "POST /api/v1/trips/1/waitlist"
  }
}
```

---

### Step 5: Joining the Segment Waitlist
```bash
curl -X POST http://localhost:8080/api/v1/trips/1/waitlist \
  -H "Authorization: Bearer <NEW_USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "Station A",
    "toStop": "Tech Park B"
  }'
```
**Response (201 Created)**:
```json
{
  "waitlistId": 1,
  "tripId": 1,
  "sequenceNo": 1,
  "status": "WAITING",
  "segment": { "from": "Station A", "to": "Tech Park B", "fromIdx": 0, "toIdx": 1 }
}
```

---

### Step 6: Cancellation & Automated Promotion Sweep
When Alice cancels booking `bk_1`:
```bash
curl -X POST http://localhost:8080/api/v1/bookings/1/cancel \
  -H "Authorization: Bearer <ALICE_JWT_TOKEN>"
```
**Response (200 OK)**:
```json
{
  "bookingId": 1,
  "status": "CANCELLED",
  "freedSeatNo": 1,
  "promotedCount": 1
}
```
**Technical Significance**: Cancelling `bk_1` instantly releases leg 0 on Seat 1 in Redis and PostgreSQL, executes the `FcfsEligiblePolicy` sweep, and auto-promotes the eligible waitlisted request into a `CONFIRMED` booking.

---

### Step 7: Driver Check-In & No-Show Lifecycle

#### 7.1 Boarding Check-In at Station A
```bash
curl -X POST http://localhost:8080/api/v1/bookings/2/check-in \
  -H "Authorization: Bearer <DRIVER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentStopIdx": 1
  }'
```
**Response (200 OK)**:
```json
{
  "bookingId": 2,
  "status": "CHECKED_IN",
  "stopIdx": 1,
  "message": "Passenger check-in successful."
}
```

#### 7.2 No-Show Downstream Capacity Reclamation
```bash
curl -X POST http://localhost:8080/api/v1/bookings/3/no-show \
  -H "Authorization: Bearer <DRIVER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "departedStopIdx": 1
  }'
```
**Response (200 OK)**:
```json
{
  "bookingId": 3,
  "status": "NO_SHOW",
  "message": "Booking updated to NO_SHOW. Downstream capacity released."
}
```

---

## 🛡️ Part 3: Database-Level Safety Verification (Layer 4 GiST Exclusion)

To independently verify that overlapping bookings are physically impossible at the PostgreSQL engine level:

1. Connect to PostgreSQL directly:
```bash
docker exec -it office_shuttle_postgres psql -U shuttle_user -d office_shuttle
```

2. Insert a valid booking on Seat 1 for range $[0, 2)$ ($A \rightarrow C$):
```sql
INSERT INTO bookings (trip_id, seat_id, user_id, from_stop_idx, to_stop_idx, status, idempotency_key, version)
VALUES (1, 1, 1, 0, 2, 'CONFIRMED', 'raw-sql-test-1', 0);
```

3. Attempt to insert a conflicting overlapping booking on the **same Seat 1** for range $[1, 3)$ ($B \rightarrow D$):
```sql
INSERT INTO bookings (trip_id, seat_id, user_id, from_stop_idx, to_stop_idx, status, idempotency_key, version)
VALUES (1, 1, 2, 1, 3, 'CONFIRMED', 'raw-sql-test-2', 0);
```

4. **PostgreSQL Constraint Enforcement**:
```sql
ERROR: conflicting key value violates exclusion constraint "no_overlapping_segments"
DETAIL: Key (seat_id, int4range(from_stop_idx, to_stop_idx, '[)'::text))=(1, [1,3)) conflicts with existing key (seat_id, int4range(from_stop_idx, to_stop_idx, '[)'::text))=(1, [0,2)).
```

---

## 📊 Part 4: Prometheus & Grafana Observability

- Open **`http://localhost:3000`** in a browser (Credentials: `admin` / `admin`).
- Open **Dashboards $\rightarrow$ Office Shuttle System Observability**.
- Monitored metrics:
  - `booking_requests_total` (CONFIRMED vs WAITLISTED vs REJECTED)
  - `booking_race_conflicts_total` (Layer 2/3/4 defense triggers)
  - `booking_latency_seconds` (p50, p95, p99)
  - `outbox_backlog_size` (Pending async event queue)
