package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentCreatedEvent(
        UUID shipmentId, UUID orderId,
        BigDecimal shippingFee, String feeDiscountType,
        String trackingNumber, Instant timestamp
) {}
