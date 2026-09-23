-- Seed Users (Passwords hashed for: 'password123')
-- BCrypt: $2a$10$e8wF42v9pG0.3F2KzM1N.uXvS1Xo5b3n1q8z9w0e1r2t3y4u5i6o
INSERT INTO users (employee_code, email, password_hash, role) VALUES
('ADMIN01', 'admin@shuttle.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'ADMIN'),
('OPERATOR01', 'operator@shuttle.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'OPERATOR'),
('DRIVER01', 'driver@shuttle.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'DRIVER'),
('EMP101', 'alice@company.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'EMPLOYEE'),
('EMP102', 'bob@company.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'EMPLOYEE'),
('EMP103', 'carol@company.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'EMPLOYEE'),
('EMP104', 'david@company.com', '$2a$10$wE9K2sF1E8hM/yOq8F1.kO7aYxU1xK2a3.c4.d5.e6.f7.g8.h9.i', 'EMPLOYEE');

-- Seed Bus
INSERT INTO buses (reg_no, seat_count) VALUES
('SHUTTLE-BUS-01', 3), -- Small bus for demo/testing segment reuse
('SHUTTLE-BUS-02', 40);

-- Seed Route (A -> B -> C -> D)
INSERT INTO routes (name, version, active, total_stops) VALUES
('North Tech Express (A->B->C->D)', 1, true, 4);

-- Seed Stops
-- A=0 (Station A), B=1 (Tech Park B), C=2 (City Center C), D=3 (HQ Campus D)
INSERT INTO stops (route_id, stop_order, name, arrival_time) VALUES
(1, 0, 'Station A', '08:00'),
(1, 1, 'Tech Park B', '08:20'),
(1, 2, 'City Center C', '08:45'),
(1, 3, 'HQ Campus D', '09:15');

-- Seed Trip
INSERT INTO trips (route_id, service_date, bus_id, status) VALUES
(1, CURRENT_DATE + INTERVAL '1 day', 1, 'SCHEDULED');

-- Seed Seats for Trip 1 (3 seats: 1, 2, 3)
INSERT INTO seats (trip_id, seat_no) VALUES
(1, 1),
(1, 2),
(1, 3);
