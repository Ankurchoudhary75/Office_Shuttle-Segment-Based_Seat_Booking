package com.officeshuttle.booking.exception;

public class TripNotOperationalException extends DomainException {
    public TripNotOperationalException(String message) {
        super(message, "https://api.shuttle/errors/trip-not-operational", 410);
    }
}
