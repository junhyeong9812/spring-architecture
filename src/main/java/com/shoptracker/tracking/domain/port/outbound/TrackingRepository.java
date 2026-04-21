package com.shoptracker.tracking.domain.port.outbound;

import com.shoptracker.tracking.domain.model.OrderTracking;

import java.util.Optional;
import java.util.UUID;

public interface TrackingRepository {
    void save(OrderTracking tracking);
    Optional<OrderTracking> findByOrderId(UUID orderId);
}
