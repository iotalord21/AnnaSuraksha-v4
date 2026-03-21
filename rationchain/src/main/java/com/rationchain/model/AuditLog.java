package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String    apiKeyHash;
    private String    clientIp;
    private String    userAgent;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    private String endpoint;
    private String httpMethod;
    private int    httpStatus;

    @Column(length = 1000)
    private String detail;

    private Long   resourceId;
    private String resourceType;
    private Long   durationMs;
    private String logHash;
    private String prevLogHash;
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }

    public AuditLog() {}

    public enum EventType {
        API_READ, API_WRITE, FRAUD_FLAG, CHAIN_MUTATION,
        AUTH_SUCCESS, AUTH_FAILURE, RATE_LIMIT_HIT,
        AUDIT_RUN, SUPPLY_CHAIN_UPDATE, GHOST_DETECTED
    }

    // Getters
    public Long getId()                    { return id; }
    public String getApiKeyHash()          { return apiKeyHash; }
    public String getClientIp()            { return clientIp; }
    public String getUserAgent()           { return userAgent; }
    public EventType getEventType()        { return eventType; }
    public String getEndpoint()            { return endpoint; }
    public String getHttpMethod()          { return httpMethod; }
    public int getHttpStatus()             { return httpStatus; }
    public String getDetail()              { return detail; }
    public Long getResourceId()            { return resourceId; }
    public String getResourceType()        { return resourceType; }
    public Long getDurationMs()            { return durationMs; }
    public String getLogHash()             { return logHash; }
    public String getPrevLogHash()         { return prevLogHash; }
    public LocalDateTime getOccurredAt()   { return occurredAt; }

    // Setters
    public void setId(Long v)                      { this.id = v; }
    public void setApiKeyHash(String v)            { this.apiKeyHash = v; }
    public void setClientIp(String v)              { this.clientIp = v; }
    public void setUserAgent(String v)             { this.userAgent = v; }
    public void setEventType(EventType v)          { this.eventType = v; }
    public void setEndpoint(String v)              { this.endpoint = v; }
    public void setHttpMethod(String v)            { this.httpMethod = v; }
    public void setHttpStatus(int v)               { this.httpStatus = v; }
    public void setDetail(String v)                { this.detail = v; }
    public void setResourceId(Long v)              { this.resourceId = v; }
    public void setResourceType(String v)          { this.resourceType = v; }
    public void setDurationMs(Long v)              { this.durationMs = v; }
    public void setLogHash(String v)               { this.logHash = v; }
    public void setPrevLogHash(String v)           { this.prevLogHash = v; }
    public void setOccurredAt(LocalDateTime v)     { this.occurredAt = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuditLog a = new AuditLog();
        public Builder apiKeyHash(String v)          { a.apiKeyHash = v; return this; }
        public Builder clientIp(String v)            { a.clientIp = v; return this; }
        public Builder eventType(EventType v)        { a.eventType = v; return this; }
        public Builder endpoint(String v)            { a.endpoint = v; return this; }
        public Builder httpMethod(String v)          { a.httpMethod = v; return this; }
        public Builder httpStatus(int v)             { a.httpStatus = v; return this; }
        public Builder detail(String v)              { a.detail = v; return this; }
        public Builder resourceId(Long v)            { a.resourceId = v; return this; }
        public Builder resourceType(String v)        { a.resourceType = v; return this; }
        public Builder durationMs(Long v)            { a.durationMs = v; return this; }
        public Builder logHash(String v)             { a.logHash = v; return this; }
        public Builder prevLogHash(String v)         { a.prevLogHash = v; return this; }
        public AuditLog build()                      { return a; }
    }
}
