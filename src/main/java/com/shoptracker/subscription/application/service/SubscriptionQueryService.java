package com.shoptracker.subscription.application.service;

import com.shoptracker.subscription.application.port.inbound.SubscriptionQueryPort;
import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import com.shoptracker.subscription.domain.port.outbound.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionQueryService implements SubscriptionQueryPort {
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionQueryService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public Optional<Subscription> findActiveByCustomer(String customerName) {
        return subscriptionRepository.findActiveByCustomer(customerName);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return subscriptionRepository.findById(new SubscriptionId(id));
    }
}
