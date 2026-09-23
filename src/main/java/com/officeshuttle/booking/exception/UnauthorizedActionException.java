package com.officeshuttle.booking.exception;

public class UnauthorizedActionException extends DomainException {
    public UnauthorizedActionException(String message) {
        super(message, "https://api.shuttle/errors/unauthorized-action", 403);
    }
}
