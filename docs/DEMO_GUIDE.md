# 🎬 Complete End-to-End Demo & Shortlisting Guide

This guide gives you the exact step-by-step procedures to run, test, capture demo screenshots, and present the **Office Shuttle Segment-Based Seat Booking** system to evaluators.

---

## 🚀 Part 1: Running the Complete System

### Option A: Using Docker Compose (All-in-One)
Run the following command from the project root:
```bash
docker compose up --build
```
This starts:
- **PostgreSQL 16** on `localhost:5432`
- **Redis 7** on `localhost:6379`
- **Spring Boot 3 App** on `http://localhost:8080`
- **Prometheus** on `http://localhost:9090`
- **Grafana** on `http://localhost:3000` (Login: `admin` / `admin`)

---

## 🧪 Part 2: Step-by-Step API Execution (With Exact cURL & JSON)

Follow these 7 sequential steps in Postman or Terminal to produce the screenshots for your submission:

### Step 1: Login to Get JWT Access Tokens
#### 1.1 Admin Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@shuttle.com",
    "password": "password123"
  }'
```
**Expected 200 OK Response**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "role": "ADMIN",
  "email": "admin@shuttle.com"
}
```

#### 1.2 Employee Login (Alice)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@company.com",
    "password": "password123"
  }'
```

---

### Step 2: Check Initial Availability for Segment A $\rightarrow$ C
```bash
curl -X GET "http://localhost:8080/api/v1/trips/1/availability?from=Station%20A&to=City%20Center%20C" \
  -H "Authorization: Bearer <ALICE_JWT_TOKEN>"
```
**Expected 200 OK Response**:
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

### Step 3: Happy Path & "Seat Weaving" Proof (The Crown Jewel)

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
**Expected 201 Created Response**:
```json
{
  "bookingId": "bk_1",
  "tripId": 1,
  "seatNo": 1,
  "segment": { "from": "Station A", "to": "Tech Park B", "fromIdx": 0, "toIdx": 1 },
  "status": "CONFIRMED"
}
```
*(Notice: Alice is placed on **Seat 1** for leg 0).*

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
**Expected 201 Created Response**:
```json
{
  "bookingId": "bk_2",
  "tripId": 1,
  "seatNo": 1,
  "segment": { "from": "Tech Park B", "to": "HQ Campus D", "fromIdx": 1, "toIdx": 3 },
  "status": "CONFIRMED"
}
```
**🎯 Key Result to Highlight in Screenshot**: Bob is assigned **Seat 1** too! Because $[0, 1)$ and $[1, 3)$ share zero road legs, the system intelligently packs both passengers onto Seat 1 without wasting Seat 2 or 3.

---

### Step 4: Concurrency & Boundary Rejection (RFC 7807 Error Envelope)

Suppose Seat 1 is now full ($A \rightarrow B$ and $B \rightarrow D$). Let Carol book $A \rightarrow D$ on Seat 2, and David book $A \rightarrow D$ on Seat 3.
Now all 3 seats on Trip 1 are committed for $[0, 3)$.

When a new passenger tries to book $A \rightarrow C$:
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
**Expected 409 Conflict (RFC 7807) Response**:
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

### Step 5: Joining the Waitlist
The passenger follows the `waitlistOffer` link:
```bash
curl -X POST http://localhost:8080/api/v1/trips/1/waitlist \
  -H "Authorization: Bearer <NEW_USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "fromStop": "Station A",
    "toStop": "Tech Park B"
  }'
```
**Expected 201 Created Response**:
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

### Step 6: Cancellation & Smart Promotion Sweep
Alice cancels her $A \rightarrow B$ booking (`bk_1`):
```bash
curl -X POST http://localhost:8080/api/v1/bookings/1/cancel \
  -H "Authorization: Bearer <ALICE_JWT_TOKEN>"
```
**Expected 200 OK Response**:
```json
{
  "bookingId": 1,
  "status": "CANCELLED",
  "freedSeatNo": 1,
  "promotedCount": 1
}
```
**🎯 Key Result**: The system freed leg 0 on Seat 1, immediately ran the `FcfsEligiblePolicy` sweep, and promoted the waitlisted passenger automatically.

---

### Step 7: Driver Check-In & No-Show Lifecycle

#### 7.1 Driver Check-In at Station A
```bash
curl -X POST http://localhost:8080/api/v1/bookings/2/check-in \
  -H "Authorization: Bearer <DRIVER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentStopIdx": 1
  }'
```
**Expected 200 OK Response**:
```json
{
  "bookingId": 2,
  "status": "CHECKED_IN",
  "stopIdx": 1,
  "message": "Passenger check-in successful."
}
```

#### 7.2 No-Show Downstream Capacity Reclamation
If a passenger did not show up at Stop 1:
```bash
curl -X POST http://localhost:8080/api/v1/bookings/3/no-show \
  -H "Authorization: Bearer <DRIVER_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "departedStopIdx": 1
  }'
```
**Expected 200 OK Response**:
```json
{
  "bookingId": 3,
  "status": "NO_SHOW",
  "message": "Booking updated to NO_SHOW. Downstream capacity released."
}
```

---

## 🛡️ Part 3: The "Mic Drop" Database Proof (Layer 4 GiST Exclusion)

To prove to any interviewer that double-booking is **physically impossible** even if someone writes raw SQL directly into PostgreSQL:

1. Connect to PostgreSQL via terminal:
```bash
docker exec -it office_shuttle_postgres psql -U shuttle_user -d office_shuttle
```

2. Insert a valid booking on Seat 1 for range $[0, 2)$ ($A \rightarrow C$):
```sql
INSERT INTO bookings (trip_id, seat_id, user_id, from_stop_idx, to_stop_idx, status, idempotency_key, version)
VALUES (1, 1, 1, 0, 2, 'CONFIRMED', 'raw-sql-test-1', 0);
```
*(Query succeeds: 1 row inserted).*

3. Now attempt to insert an overlapping booking on the **same Seat 1** for range $[1, 3)$ ($B \rightarrow D$):
```sql
INSERT INTO bookings (trip_id, seat_id, user_id, from_stop_idx, to_stop_idx, status, idempotency_key, version)
VALUES (1, 1, 2, 1, 3, 'CONFIRMED', 'raw-sql-test-2', 0);
```

4. **💥 The PostgreSQL Engine Rejection**:
```
ERROR: conflicting key value violates exclusion constraint "no_overlapping_segments"
DETAIL: Key (seat_id, int4range(from_stop_idx, to_stop_idx, '[)'::text))=(1, [1,3)) conflicts with existing key (seat_id, int4range(from_stop_idx, to_stop_idx, '[)'::text))=(1, [0,2)).
```

**Interview Explanation**:
*"This proves that no matter what bugs or race conditions could theoretically occur at the application layer or cache layer, the PostgreSQL storage engine itself will abort any overlapping transaction."*

---

## 📊 Part 4: Grafana Observability Dashboard

Open your browser to: **`http://localhost:3000`** (User: `admin` / Pass: `admin`)
Navigate to Dashboards $\rightarrow$ **Office Shuttle System Observability**:

You will see real-time panels for:
1. **Confirmed Bookings Total** (`booking_requests_total{result="CONFIRMED"}`)
2. **Waitlisted Requests Total** (`booking_requests_total{result="WAITLISTED"}`)
3. **Race Conflicts Defended** (`booking_race_conflicts_total`)
4. **Waitlist Auto-Promotions Total** (`promotion_success_total`)
5. **Booking Request Latency** (p50, p95, p99 percentiles)
6. **Transactional Outbox Backlog Gauge** (`outbox_backlog_size`)
