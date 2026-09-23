package com.officeshuttle.booking.config;

import com.officeshuttle.booking.domain.*;
import com.officeshuttle.booking.engine.RedisScriptExecutor;
import com.officeshuttle.booking.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final BusRepository busRepository;
    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisScriptExecutor redisScriptExecutor;

    public DataInitializer(
            UserRepository userRepository,
            RouteRepository routeRepository,
            StopRepository stopRepository,
            BusRepository busRepository,
            TripRepository tripRepository,
            SeatRepository seatRepository,
            PasswordEncoder passwordEncoder,
            RedisScriptExecutor redisScriptExecutor) {
        this.userRepository = userRepository;
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
        this.seatRepository = seatRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisScriptExecutor = redisScriptExecutor;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded.");
            return;
        }

        log.info("Seeding initial demo data...");

        String hash = passwordEncoder.encode("password123");
        User admin = userRepository.save(new User("ADMIN01", "admin@shuttle.com", hash, UserRole.ADMIN));
        User driver = userRepository.save(new User("DRIVER01", "driver@shuttle.com", hash, UserRole.DRIVER));
        User alice = userRepository.save(new User("EMP101", "alice@company.com", hash, UserRole.EMPLOYEE));
        User bob = userRepository.save(new User("EMP102", "bob@company.com", hash, UserRole.EMPLOYEE));
        User carol = userRepository.save(new User("EMP103", "carol@company.com", hash, UserRole.EMPLOYEE));
        User david = userRepository.save(new User("EMP104", "david@company.com", hash, UserRole.EMPLOYEE));

        Bus bus = busRepository.save(new Bus("SHUTTLE-BUS-01", 3));
        busRepository.save(new Bus("SHUTTLE-BUS-02", 40));

        Route route = new Route("North Tech Express (A->B->C->D)", 4);
        route = routeRepository.save(route);

        Stop s0 = new Stop(0, "Station A", "08:00"); s0.setRoute(route);
        Stop s1 = new Stop(1, "Tech Park B", "08:20"); s1.setRoute(route);
        Stop s2 = new Stop(2, "City Center C", "08:45"); s2.setRoute(route);
        Stop s3 = new Stop(3, "HQ Campus D", "09:15"); s3.setRoute(route);
        stopRepository.saveAll(List.of(s0, s1, s2, s3));

        Trip trip = new Trip(route, LocalDate.now().plusDays(1), bus, TripStatus.SCHEDULED);
        trip = tripRepository.save(trip);

        Seat seat1 = seatRepository.save(new Seat(trip, 1));
        Seat seat2 = seatRepository.save(new Seat(trip, 2));
        Seat seat3 = seatRepository.save(new Seat(trip, 3));

        // Initialize Redis bitmaps
        redisScriptExecutor.rebuildTripBitmaps(trip.getTripId(), 4, 3, Collections.emptyList());

        log.info("Data seeding completed successfully! Trip ID: {}", trip.getTripId());
    }
}
