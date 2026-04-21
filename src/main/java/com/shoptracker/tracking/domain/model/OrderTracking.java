package com.shoptracker.tracking.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OrderTracking {
    private final TrackingId id;
    private final UUID orderId;
    private final String customerName;
    private final String subscriptionTier;  // 주문 시점 스냅샷
    private final List<TrackingEvent> events;
    private TrackingPhase currentPhase;
    private final Instant startedAt;
    private Instant completedAt;

    public static OrderTracking create(UUID orderId, String customerName,
                                       String subscriptionTier) {
        var tracking = new OrderTracking(
                TrackingId.generate(), orderId, customerName, subscriptionTier,
                new ArrayList<>(), TrackingPhase.ORDER_PLACED, Instant.now(), null);
        tracking.addEvent("order.created", "orders",
                Map.of("message", "주문이 생성되었습니다"));
        return tracking;
    }

    public void addEvent(String eventType, String module, Map<String, Object> detail) {
        this.events.add(new TrackingEvent(eventType, Instant.now(), module, detail));
    }

    public void updatePhase(TrackingPhase phase) {
        this.currentPhase = phase;
        if (phase == TrackingPhase.DELIVERED || phase == TrackingPhase.FAILED) {
            this.completedAt = Instant.now();
        }
    }
    // Full constructor — JPA Mapper가 DB에서 읽어올 때도 사용한다.
    public OrderTracking(TrackingId id, UUID orderId, String customerName,
                         String subscriptionTier, List<TrackingEvent> events,
                         TrackingPhase currentPhase, Instant startedAt,
                         Instant completedAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerName = customerName;
        this.subscriptionTier = subscriptionTier;
        this.events = events;
        this.currentPhase = currentPhase;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public TrackingId getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getSubscriptionTier() { return subscriptionTier; }
    public List<TrackingEvent> getEvents() { return List.copyOf(events); }
    public TrackingPhase getCurrentPhase() { return currentPhase; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
