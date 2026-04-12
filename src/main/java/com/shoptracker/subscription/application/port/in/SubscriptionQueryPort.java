package com.shoptracker.subscription.application.port.in;

import com.shoptracker.subscription.domain.model.Subscription;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionQueryPort {
    Optional<Subscription> findActiveByCustomer(String customerName);
    Optional<Subscription> findById(UUID id);
}
