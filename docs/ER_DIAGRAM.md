# Entity Relationship & Database Schema

## 1. Relational Topology

```mermaid
erDiagram
    ROUTE ||--o{ STOP : "contains (1..N)"
    ROUTE ||--o{ TRIP : "schedules (1..N)"
    BUS ||--o{ TRIP : "assigned to (1..N)"
    TRIP ||--o{ SEAT : "has (1..N)"
    TRIP ||--o{ BOOKING : "records (1..N)"
    SEAT ||--o{ BOOKING : "allocated to (1..N)"
    USER ||--o{ BOOKING : "books (1..N)"
    TRIP ||--o{ WAITLIST_ENTRY : "enqueues (1..N)"
    USER ||--o{ WAITLIST_ENTRY : "requests (1..N)"
    BOOKING ||--o{ OUTBOX_EVENT : "triggers (1..N)"
    BOOKING ||--o{ AUDIT_LOG : "audits (1..N)"
```

## 2. Key Database Schema Details

### PostgreSQL GiST Exclusion Constraint
```sql
ALTER TABLE bookings ADD CONSTRAINT no_overlapping_segments
EXCLUDE USING gist (
    seat_id WITH =,
    int4range(from_stop_idx, to_stop_idx, '[)') WITH &&
)
WHERE (status = 'CONFIRMED');
```

- `int4range(from, to, '[)')` represents a half-open interval.
- `WITH &&` tests for range overlap.
- `WITH =` ensures collision checks are partitioned by physical `seat_id`.
- `WHERE (status = 'CONFIRMED')` allows cancelled/no-show records to remain in history without blocking re-booking.
