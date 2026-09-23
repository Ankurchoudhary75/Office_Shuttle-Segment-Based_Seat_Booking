package com.officeshuttle.booking.exception;

public abstract class DomainException extends RuntimeException {
    private final String errorType;
    private final int status;

    public DomainException(String message, String errorType, int status) {
        super(message);
        this.errorType = errorType;
        this.status = status;
    }

    public String getErrorType() {
        return errorType;
    }

    public int getStatus() {
        return status;
    }
}
