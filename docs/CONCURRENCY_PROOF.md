# Concurrency Correctness Proof & Defense Analysis

## 1. The Race Condition Window

Consider two concurrent requests for overlapping journeys on a single remaining seat on bus:
- **Thread A**: Requests `B -> D` (Legs 1, 2)
- **Thread B**: Requests `A -> C` (Legs 0, 1)

In a naive "check then write" application:
1. Thread A queries: "Is any seat free for B->D?" $\rightarrow$ Seat 1 is free.
2. Thread B queries: "Is any seat free for A->C?" $\rightarrow$ Seat 1 is free.
3. Thread A writes booking on Seat 1 for B->D.
4. Thread B writes booking on Seat 1 for A->C.
5. **Result**: Both passengers board Seat 1 between stops B and C $\rightarrow$ Critical double-booking violation.

---

## 2. Four Independent Defense Layers

### Layer 1: In-Memory Bitmap Engine
Operates on 64-bit integer words where bit $i$ represents leg $i$:
$$\text{rangeMask}(X_s, X_d) = ((1 \ll (X_d - X_s)) - 1) \ll X_s$$
Qualifying seats are found via column-wise AND reduction:
$$\text{candidates} = \bigcap_{i=X_s}^{X_d-1} \text{freeSeatsOnLeg}[i]$$

### Layer 2: Redis Lua Script Atomicity
Redis executes scripts as a single atomic operation:
```lua
redis.call('BITOP', 'AND', tmpKey, unpack(KEYS))
local seatBit = redis.call('BITPOS', tmpKey, 1)
if seatBit == -1 then return -1 end
for i = 1, #KEYS do redis.call('SETBIT', KEYS[i], seatBit, 0) end
return seatBit + 1
```
Thread B cannot interleave between Thread A's availability check and bit reservation.

### Layer 3: PostgreSQL ACID Re-Validation
Inside the database transaction:
```sql
SELECT * FROM seats WHERE trip_id = :tripId ORDER BY seat_no FOR UPDATE SKIP LOCKED;
SELECT 1 FROM bookings WHERE seat_id = :seatId AND status = 'CONFIRMED'
AND from_stop_idx < :requestedTo AND to_stop_idx > :requestedFrom LIMIT 1;
```

### Layer 4: PostgreSQL GiST Exclusion Constraint
The physical guarantee enforced by the database storage engine:
```sql
EXCLUDE USING gist (seat_id WITH =, int4range(from_stop_idx, to_stop_idx, '[)') WITH &&) WHERE (status = 'CONFIRMED');
```
Even if application logic or cache fails entirely, the database engine physically rejects an overlapping insert.
