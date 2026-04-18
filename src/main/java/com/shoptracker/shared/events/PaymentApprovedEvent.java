package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID paymentId,
        UUID orderId,
        BigDecimal originalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String appliedDiscountType,   // "none", "basic_subscription", "premium_subscription"
        String method,
        Instant timestamp
) {
}
