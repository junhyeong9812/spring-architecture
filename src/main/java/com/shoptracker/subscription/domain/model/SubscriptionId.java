package com.shoptracker.subscription.domain.model;

import java.util.UUID;

public record SubscriptionId(UUID value) {
    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID());
    }
}
