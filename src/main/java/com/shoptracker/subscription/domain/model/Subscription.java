package com.shoptracker.subscription.domain.model;

import java.time.Instant;

public class Subscription {
    private final SubscriptionId id;
    private final String customerName;
    private final SubscriptionTier tier;
    private SubscriptionStatus status;
    private final Instant startedAt;
    private final Instant expiresAt;

    public Subscription(SubscriptionId id, String customerName, SubscriptionTier tier,
                        SubscriptionStatus status, Instant startedAt, Instant expiresAt) {
        this.id = id;
        this.customerName = customerName;
        this.tier = tier;
        this.status = status;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public static Subscription create(String customerName, SubscriptionTier tier) {
        return new Subscription(
                SubscriptionId.generate(),
                customerName,
                tier,
                SubscriptionStatus.ACTIVE,
                Instant.now(),
                Instant.now().plusSeconds(30L * 24 * 60 * 60)
        );
    }

    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE
                && this.expiresAt.isAfter(Instant.now());
    }

    public void cancel() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot  cancel subscription in status: " + this.status
            );
        }
        this.status = SubscriptionStatus.CANCELLED;
    }

    public SubscriptionId getId() { return id; }
    public String getCustomerName() { return customerName; }
    public SubscriptionTier getTier() { return tier; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
