package com.officeshuttle.booking.repository;

import com.officeshuttle.booking.domain.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {
    Optional<Bus> findByRegNo(String regNo);
}
