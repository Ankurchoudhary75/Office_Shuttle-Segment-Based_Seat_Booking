package com.officeshuttle.booking.exception;

public class CapacityReassignmentException extends DomainException {
    public CapacityReassignmentException(String message) {
        super(message, "https://api.shuttle/errors/capacity-reassignment-failed", 409);
    }
}
