package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionActivatedEvent(
        UUID subscriptionId,
        String customerName,
        String tier,
        Instant expiresAt,
        Instant timestamp
) {}