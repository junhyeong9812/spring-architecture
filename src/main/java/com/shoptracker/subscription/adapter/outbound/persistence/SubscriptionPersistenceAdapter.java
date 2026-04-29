package com.shoptracker.subscription.adapter.outbound.persistence;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import com.shoptracker.subscription.domain.port.outbound.SubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SubscriptionPersistenceAdapter implements SubscriptionRepository {
    private final SpringDataSubscriptionRepository jpaRepository;

    public SubscriptionPersistenceAdapter(SpringDataSubscriptionRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Subscription subscription) {
        jpaRepository.save(SubscriptionMapper.toJpa(subscription));
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return jpaRepository.findById(id.value())
                .map(SubscriptionMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findActiveByCustomer(String customerName) {
        return jpaRepository.findActiveByCustomerName(customerName)
                .map(SubscriptionMapper::toDomain);
    }
}
