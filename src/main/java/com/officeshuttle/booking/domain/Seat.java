package com.officeshuttle.booking.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "seats", uniqueConstraints = {
    @UniqueConstraint(name = "uq_trip_seat_no", columnNames = {"trip_id", "seat_no"})
})
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    @JsonIgnore
    private Trip trip;

    @Column(name = "seat_no", nullable = false)
    private Integer seatNo;

    public Seat() {}

    public Seat(Trip trip, Integer seatNo) {
        this.trip = trip;
        this.seatNo = seatNo;
    }

    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public Integer getSeatNo() { return seatNo; }
    public void setSeatNo(Integer seatNo) { this.seatNo = seatNo; }
}
