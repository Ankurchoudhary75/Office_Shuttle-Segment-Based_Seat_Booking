package com.officeshuttle.booking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips", uniqueConstraints = {
    @UniqueConstraint(name = "uq_route_date_bus", columnNames = {"route_id", "service_date", "bus_id"})
})
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_id")
    private Long tripId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TripStatus status = TripStatus.SCHEDULED;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("seatNo ASC")
    private List<Seat> seats = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Trip() {}

    public Trip(Route route, LocalDate serviceDate, Bus bus, TripStatus status) {
        this.route = route;
        this.serviceDate = serviceDate;
        this.bus = bus;
        this.status = status;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setTrip(this);
    }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }

    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }

    public Bus getBus() { return bus; }
    public void setBus(Bus bus) { this.bus = bus; }

    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }

    public List<Seat> getSeats() { return seats; }
    public void setSeats(List<Seat> seats) { this.seats = seats; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
