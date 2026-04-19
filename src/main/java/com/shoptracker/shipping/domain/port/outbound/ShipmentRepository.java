package com.shoptracker.shipping.domain.port.outbound;

import com.shoptracker.shipping.domain.model.Shipment;
import com.shoptracker.shipping.domain.model.ShipmentId;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {
    void save(Shipment shipment);
    Optional<Shipment> findById(ShipmentId id);
    Optional<Shipment> findByOrderId(UUID orderId);
}
