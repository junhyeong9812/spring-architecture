package com.shoptracker.subscription.adapter.inbound.web;

import com.shoptracker.subscription.domain.model.Subscription;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String customerName,
        String tier,
        String status,
        boolean isActive,
        Instant startedAt,
        Instant expiresAt
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
                s.getId().value(),
                s.getCustomerName(),
                s.getTier().getValue(),
                s.getStatus().name().toLowerCase(),
                s.isActive(),
                s.getStartedAt(),
                s.getExpiresAt()
        );
    }
}
