package com.shoptracker.tracking.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataTrackingRepository extends JpaRepository<TrackingJpaEntity, UUID> {

    Optional<TrackingJpaEntity> findByOrderId(UUID orderId);
}