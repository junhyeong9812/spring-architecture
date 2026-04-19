package com.shoptracker.shipping.adapter.inbound.web;

import com.shoptracker.shipping.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID orderId,
        String status,
        String street,
        String city,
        String zipCode,
        BigDecimal shippingFee,
        BigDecimal originalFee,
        String feeDiscountType,
        String trackingNumber,
        LocalDate estimatedDelivery,
        Instant createdAt
) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
                s.getId().value(),
                s.getOrderId(),
                s.getStatus().name().toLowerCase(),
                s.getAddress().street(),
                s.getAddress().city(),
                s.getAddress().zipCode(),
                s.getShippingFee().amount(),
                s.getOriginalFee().amount(),
                s.getFeeDiscountType(),
                s.getTrackingNumber(),
                s.getEstimatedDelivery(),
                s.getCreatedAt()
        );
    }
}
