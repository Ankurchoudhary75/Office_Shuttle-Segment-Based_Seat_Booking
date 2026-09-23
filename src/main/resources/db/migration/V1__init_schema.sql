-- Enable btree_gist extension for range and integer exclusion constraints
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Users Table
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL, -- EMPLOYEE, DRIVER, OPERATOR, ADMIN
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Routes Table
CREATE TABLE routes (
    route_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    total_stops INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Stops Table (Ordered topology)
CREATE TABLE stops (
    stop_id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(route_id) ON DELETE CASCADE,
    stop_order INT NOT NULL, -- 0-based ordinal index
    name VARCHAR(255) NOT NULL,
    arrival_time VARCHAR(16) NOT NULL,
    CONSTRAINT uq_route_stop_order UNIQUE (route_id, stop_order)
);

-- Buses Table
CREATE TABLE buses (
    bus_id BIGSERIAL PRIMARY KEY,
    reg_no VARCHAR(64) UNIQUE NOT NULL,
    seat_count INT NOT NULL
);

-- Trips Table
CREATE TABLE trips (
    trip_id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL REFERENCES routes(route_id),
    service_date DATE NOT NULL,
    bus_id BIGINT NOT NULL REFERENCES buses(bus_id),
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, IN_TRANSIT, COMPLETED, CANCELLED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_route_date_bus UNIQUE (route_id, service_date, bus_id)
);

-- Seats Table (Logical seat numbers per trip)
CREATE TABLE seats (
    seat_id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    seat_no INT NOT NULL,
    CONSTRAINT uq_trip_seat_no UNIQUE (trip_id, seat_no)
);

-- Bookings Table (Layer 4 Exclusion Constraint + Optimistic Versioning)
CREATE TABLE bookings (
    booking_id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(trip_id),
    seat_id BIGINT NOT NULL REFERENCES seats(seat_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    from_stop_idx INT NOT NULL,
    to_stop_idx INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED', -- CONFIRMED, CANCELLED, CHECKED_IN, NO_SHOW
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_booking_interval CHECK (from_stop_idx < to_stop_idx),
    CONSTRAINT no_overlapping_segments EXCLUDE USING gist (
        seat_id WITH =,
        int4range(from_stop_idx, to_stop_idx, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED')
);

CREATE INDEX idx_bookings_seat_overlap ON bookings (seat_id, from_stop_idx, to_stop_idx) WHERE status = 'CONFIRMED';
CREATE INDEX idx_bookings_trip_user ON bookings (trip_id, user_id);

-- Waitlist Table
CREATE TABLE waitlist_entries (
    waitlist_id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    from_stop_idx INT NOT NULL,
    to_stop_idx INT NOT NULL,
    sequence_no BIGINT NOT NULL, -- Monotonic fairness key
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING', -- WAITING, PROMOTED, EXPIRED, CANCELLED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    promoted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_waitlist_interval CHECK (from_stop_idx < to_stop_idx),
    CONSTRAINT uq_trip_seq UNIQUE (trip_id, sequence_no)
);

CREATE INDEX idx_waitlist_trip_seq ON waitlist_entries (trip_id, sequence_no) WHERE status = 'WAITING';

-- Transactional Outbox Table
CREATE TABLE outbox_events (
    event_id BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at);

-- Audit Log Table
CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    entity VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id BIGINT,
    payload_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
