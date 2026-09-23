package com.officeshuttle.booking.exception;

public class StaleWriteException extends DomainException {
    public StaleWriteException(String message) {
        super(message, "https://api.shuttle/errors/stale-write-conflict", 409);
    }
}
