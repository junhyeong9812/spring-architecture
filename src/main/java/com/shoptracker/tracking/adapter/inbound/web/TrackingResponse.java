package com.shoptracker.tracking.adapter.inbound.web;

import com.shoptracker.tracking.domain.model.OrderTracking;

import java.time.Instant;
import java.util.UUID;

public record TrackingResponse(
        UUID trackingId,
        UUID orderId,
        String customerName,
        String subscriptionTier,
        String currentPhase,
        int eventCount,
        Instant startedAt,
        Instant completedAt
) {
    public static TrackingResponse from(OrderTracking t) {
        return new TrackingResponse(
                t.getId().value(),
                t.getOrderId(),
                t.getCustomerName(),
                t.getSubscriptionTier(),
                t.getCurrentPhase().name().toLowerCase(),
                t.getEvents().size(),
                t.getStartedAt(),
                t.getCompletedAt()
        );
    }
}
