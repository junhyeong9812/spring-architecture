package com.shoptracker.subscription.adapter.outbound.persistence;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import com.shoptracker.subscription.domain.model.SubscriptionStatus;
import com.shoptracker.subscription.domain.model.SubscriptionTier;

public class SubscriptionMapper {

    public static Subscription toDomain(SubscriptionJpaEntity entity) {
        return new Subscription(
                new SubscriptionId(entity.getId()),
                entity.getCustomerName(),
                SubscriptionTier.fromString(entity.getTier()),
                SubscriptionStatus.valueOf(entity.getStatus()),
                entity.getStartedAt(),
                entity.getExpiresAt()
        );
    }

    public static SubscriptionJpaEntity toJpa(Subscription domain) {
        SubscriptionJpaEntity entity = new SubscriptionJpaEntity();
        entity.setId(domain.getId().value());
        entity.setCustomerName(domain.getCustomerName());
        entity.setTier(domain.getTier().getValue());
        entity.setStatus(domain.getStatus().name());
        entity.setStartedAt(domain.getStartedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        return entity;
    }
}
