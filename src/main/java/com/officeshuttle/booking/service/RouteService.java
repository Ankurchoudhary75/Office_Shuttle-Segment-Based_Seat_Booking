package com.officeshuttle.booking.service;

import com.officeshuttle.booking.domain.Route;
import com.officeshuttle.booking.domain.Stop;
import com.officeshuttle.booking.exception.InvalidSegmentException;
import com.officeshuttle.booking.repository.RouteRepository;
import com.officeshuttle.booking.repository.StopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;

    public RouteService(RouteRepository routeRepository, StopRepository stopRepository) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
    }

    public List<Route> getAllActiveRoutes() {
        return routeRepository.findByActiveTrue();
    }

    public Route getRouteById(Long routeId) {
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new InvalidSegmentException("Route not found: " + routeId));
    }

    @Transactional
    public Route createRoute(String name, List<Stop> stops) {
        if (stops == null || stops.size() < 2) {
            throw new InvalidSegmentException("A route must contain at least 2 stops");
        }

        Route route = new Route(name, stops.size());
        route = routeRepository.save(route);

        for (int i = 0; i < stops.size(); i++) {
            Stop stop = stops.get(i);
            stop.setRoute(route);
            stop.setStopOrder(i);
            stopRepository.save(stop);
        }

        route.setStops(stops);
        return route;
    }
}
