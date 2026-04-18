package com.shoptracker.payments.adapter.inbound.web;

import com.shoptracker.payments.domain.model.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String method,
        String status,
        String appliedDiscountType,
        Instant processedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId().value(), p.getOrderId(),
                p.getOriginalAmount().amount(),
                p.getDiscountAmount().amount(),
                p.getFinalAmount().amount(),
                p.getMethod().name().toLowerCase(),
                p.getStatus().name().toLowerCase(),
                p.getAppliedDiscountType(),
                p.getProcessedAt()
        );
    }
}
