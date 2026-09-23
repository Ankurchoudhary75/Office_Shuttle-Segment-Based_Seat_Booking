package com.officeshuttle.booking.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "entity", nullable = false, length = 64)
    private String entity;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AuditLog() {}

    public AuditLog(String entity, String action, Long actorId, String payloadJson) {
        this.entity = entity;
        this.action = action;
        this.actorId = actorId;
        this.payloadJson = payloadJson;
    }

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
