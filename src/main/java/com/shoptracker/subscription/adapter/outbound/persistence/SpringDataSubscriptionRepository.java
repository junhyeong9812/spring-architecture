package com.shoptracker.subscription.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataSubscriptionRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    @Query("SELECT s FROM SubscriptionJpaEntity s " +
            "WHERE s.customerName = :customerName AND s.status = 'ACTIVE'")
    Optional<SubscriptionJpaEntity> findActiveByCustomerName(String customerName);
}
