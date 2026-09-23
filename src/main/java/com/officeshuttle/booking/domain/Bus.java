package com.officeshuttle.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "buses")
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bus_id")
    private Long busId;

    @Column(name = "reg_no", unique = true, nullable = false, length = 64)
    private String regNo;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    public Bus() {}

    public Bus(String regNo, Integer seatCount) {
        this.regNo = regNo;
        this.seatCount = seatCount;
    }

    public Long getBusId() { return busId; }
    public void setBusId(Long busId) { this.busId = busId; }

    public String getRegNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }

    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }
}
