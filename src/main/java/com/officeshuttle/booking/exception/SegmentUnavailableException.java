package com.officeshuttle.booking.exception;

public class SegmentUnavailableException extends DomainException {
    private final String waitlistOfferAction;

    public SegmentUnavailableException(String message, String waitlistOfferAction) {
        super(message, "https://api.shuttle/errors/segment-unavailable", 409);
        this.waitlistOfferAction = waitlistOfferAction;
    }

    public SegmentUnavailableException(String message) {
        this(message, null);
    }

    public String getWaitlistOfferAction() {
        return waitlistOfferAction;
    }
}
