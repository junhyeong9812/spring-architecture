package com.shoptracker.shipping.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataShipmentRepository extends JpaRepository<ShipmentJpaEntity, UUID> {
    Optional<ShipmentJpaEntity> findByOrderId(UUID orderId);
}
