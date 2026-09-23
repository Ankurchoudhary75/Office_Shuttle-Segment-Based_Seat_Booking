package com.officeshuttle.booking.exception;

public class InvalidSegmentException extends DomainException {
    public InvalidSegmentException(String message) {
        super(message, "https://api.shuttle/errors/invalid-segment", 400);
    }
}
