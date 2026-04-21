package com.shoptracker.tracking.adapter.outbound.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
public class TrackingEventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracking_id", nullable = false)
    private TrackingJpaEntity tracking;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detail;

    protected TrackingEventJpaEntity() {}

    public TrackingEventJpaEntity(UUID id, String eventType, Instant timestamp,
                                  String module, Map<String, Object> detail) {
        this.id = id;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.module = module;
        this.detail = detail;
    }

    public UUID getId() { return id; }
    public TrackingJpaEntity getTracking() { return tracking; }
    public void setTracking(TrackingJpaEntity tracking) { this.tracking = tracking; }
    public String getEventType() { return eventType; }
    public Instant getTimestamp() { return timestamp; }
    public String getModule() { return module; }
    public Map<String, Object> getDetail() { return detail; }
}