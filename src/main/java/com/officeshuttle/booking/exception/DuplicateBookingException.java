package com.officeshuttle.booking.exception;

public class DuplicateBookingException extends DomainException {
    public DuplicateBookingException(String message) {
        super(message, "https://api.shuttle/errors/duplicate-booking", 409);
    }
}
