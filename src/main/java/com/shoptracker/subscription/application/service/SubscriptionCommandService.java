package com.shoptracker.subscription.application.service;

import com.shoptracker.shared.events.SubscriptionActivatedEvent;
import com.shoptracker.shared.exception.BusinessRuleException;
import com.shoptracker.subscription.application.port.inbound.CreateSubscriptionUseCase;
import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionTier;
import com.shoptracker.subscription.domain.port.outbound.SubscriptionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionCommandService implements CreateSubscriptionUseCase {
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionCommandService(SubscriptionRepository subscriptionRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID create(String customerName, String tier) {
        subscriptionRepository.findActiveByCustomer(customerName)
                .filter(Subscription::isActive)
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "Customer '" + customerName + "' already has an active subscription");
                });

        SubscriptionTier subscriptionTier = SubscriptionTier.fromString(tier);
        Subscription subscription = Subscription.create(customerName, subscriptionTier);
        subscriptionRepository.save(subscription);

        eventPublisher.publishEvent(new SubscriptionActivatedEvent(
                subscription.getId().value(),
                customerName,
                subscriptionTier.getValue(),
                subscription.getExpiresAt(),
                Instant.now()
        ));

        return subscription.getId().value();
    }

}
