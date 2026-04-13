package com.shoptracker.subscription.domain.port.outbound;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;

import java.util.Optional;

public interface SubscriptionRepository {
    void save(Subscription subscription);
    Optional<Subscription> findById(SubscriptionId id);
    Optional<Subscription> findActiveByCustomer(String customerName);
}
