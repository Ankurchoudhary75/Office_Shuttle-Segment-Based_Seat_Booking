package com.officeshuttle.booking.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "total_stops", nullable = false)
    private Integer totalStops;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stopOrder ASC")
    private List<Stop> stops = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Route() {}

    public Route(String name, Integer totalStops) {
        this.name = name;
        this.totalStops = totalStops;
        this.version = 1;
        this.active = true;
    }

    public void addStop(Stop stop) {
        stops.add(stop);
        stop.setRoute(this);
    }

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getTotalStops() { return totalStops; }
    public void setTotalStops(Integer totalStops) { this.totalStops = totalStops; }

    public List<Stop> getStops() { return stops; }
    public void setStops(List<Stop> stops) { this.stops = stops; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
