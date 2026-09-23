package com.officeshuttle.booking.controller;

import com.officeshuttle.booking.domain.Route;
import com.officeshuttle.booking.domain.Stop;
import com.officeshuttle.booking.service.RouteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ResponseEntity<List<Route>> getAllRoutes() {
        return ResponseEntity.ok(routeService.getAllActiveRoutes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Route> getRouteById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(routeService.getRouteById(id));
    }

    @PostMapping
    public ResponseEntity<Route> createRoute(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        List<Map<String, String>> stopsList = (List<Map<String, String>>) request.get("stops");

        List<Stop> stops = new ArrayList<>();
        if (stopsList != null) {
            for (int i = 0; i < stopsList.size(); i++) {
                Map<String, String> s = stopsList.get(i);
                stops.add(new Stop(i, s.get("name"), s.get("arrivalTime")));
            }
        }

        Route created = routeService.createRoute(name, stops);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
