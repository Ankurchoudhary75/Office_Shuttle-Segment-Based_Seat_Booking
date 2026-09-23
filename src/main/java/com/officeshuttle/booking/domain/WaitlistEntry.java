package com.officeshuttle.booking.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "waitlist_entries", uniqueConstraints = {
    @UniqueConstraint(name = "uq_trip_seq", columnNames = {"trip_id", "sequence_no"})
})
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long waitlistId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "from_stop_idx", nullable = false)
    private Integer fromStopIdx;

    @Column(name = "to_stop_idx", nullable = false)
    private Integer toStopIdx;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "promoted_at")
    private OffsetDateTime promotedAt;

    public WaitlistEntry() {}

    public WaitlistEntry(Trip trip, User user, Integer fromStopIdx, Integer toStopIdx, Long sequenceNo) {
        this.trip = trip;
        this.user = user;
        this.fromStopIdx = fromStopIdx;
        this.toStopIdx = toStopIdx;
        this.sequenceNo = sequenceNo;
        this.status = WaitlistStatus.WAITING;
    }

    public Long getWaitlistId() { return waitlistId; }
    public void setWaitlistId(Long waitlistId) { this.waitlistId = waitlistId; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getFromStopIdx() { return fromStopIdx; }
    public void setFromStopIdx(Integer fromStopIdx) { this.fromStopIdx = fromStopIdx; }

    public Integer getToStopIdx() { return toStopIdx; }
    public void setToStopIdx(Integer toStopIdx) { this.toStopIdx = toStopIdx; }

    public Long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }

    public WaitlistStatus getStatus() { return status; }
    public void setStatus(WaitlistStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getPromotedAt() { return promotedAt; }
    public void setPromotedAt(OffsetDateTime promotedAt) { this.promotedAt = promotedAt; }
}
