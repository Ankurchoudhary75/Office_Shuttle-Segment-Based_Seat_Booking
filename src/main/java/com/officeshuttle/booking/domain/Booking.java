package com.officeshuttle.booking.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "from_stop_idx", nullable = false)
    private Integer fromStopIdx;

    @Column(name = "to_stop_idx", nullable = false)
    private Integer toStopIdx;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 128)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public Booking() {}

    public Booking(Trip trip, Seat seat, User user, Integer fromStopIdx, Integer toStopIdx, String idempotencyKey) {
        this.trip = trip;
        this.seat = seat;
        this.user = user;
        this.fromStopIdx = fromStopIdx;
        this.toStopIdx = toStopIdx;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.CONFIRMED;
    }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public Seat getSeat() { return seat; }
    public void setSeat(Seat seat) { this.seat = seat; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getFromStopIdx() { return fromStopIdx; }
    public void setFromStopIdx(Integer fromStopIdx) { this.fromStopIdx = fromStopIdx; }

    public Integer getToStopIdx() { return toStopIdx; }
    public void setToStopIdx(Integer toStopIdx) { this.toStopIdx = toStopIdx; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
