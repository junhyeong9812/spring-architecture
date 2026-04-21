package com.shoptracker.tracking.adapter.outbound.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_tracking")
public class TrackingJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "subscription_tier")
    private String subscriptionTier;

    @Column(name = "current_phase", nullable = false, length = 30)
    private String currentPhase;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "tracking",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("timestamp ASC")
    private List<TrackingEventJpaEntity> events = new ArrayList<>();

    protected TrackingJpaEntity() {}

    public void addEvent(TrackingEventJpaEntity event) {
        events.add(event);
        event.setTracking(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(String subscriptionTier) { this.subscriptionTier = subscriptionTier; }
    public String getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(String currentPhase) { this.currentPhase = currentPhase; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<TrackingEventJpaEntity> getEvents() { return events; }
    public void setEvents(List<TrackingEventJpaEntity> events) { this.events = events; }
}