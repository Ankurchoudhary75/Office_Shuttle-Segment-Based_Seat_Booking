package com.officeshuttle.booking.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "stops", uniqueConstraints = {
    @UniqueConstraint(name = "uq_route_stop_order", columnNames = {"route_id", "stop_order"})
})
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stop_id")
    private Long stopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    @JsonIgnore
    private Route route;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "arrival_time", nullable = false, length = 16)
    private String arrivalTime;

    public Stop() {}

    public Stop(Integer stopOrder, String name, String arrivalTime) {
        this.stopOrder = stopOrder;
        this.name = name;
        this.arrivalTime = arrivalTime;
    }

    public Long getStopId() { return stopId; }
    public void setStopId(Long stopId) { this.stopId = stopId; }

    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }

    public Integer getStopOrder() { return stopOrder; }
    public void setStopOrder(Integer stopOrder) { this.stopOrder = stopOrder; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }
}
