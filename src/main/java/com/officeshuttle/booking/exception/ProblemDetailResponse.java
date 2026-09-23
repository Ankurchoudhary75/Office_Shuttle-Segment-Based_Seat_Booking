package com.officeshuttle.booking.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * RFC 7807 Problem-Details Error Envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String traceId;
    private String instance;
    private Map<String, String> waitlistOffer;

    public ProblemDetailResponse() {}

    public ProblemDetailResponse(String type, String title, int status, String detail, String traceId, String instance) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.traceId = traceId;
        this.instance = instance;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public Map<String, String> getWaitlistOffer() { return waitlistOffer; }
    public void setWaitlistOffer(Map<String, String> waitlistOffer) { this.waitlistOffer = waitlistOffer; }
}
