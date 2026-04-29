package com.shoptracker.orders.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
        UUID id,
        String customerName,
        String status,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        int itemCount,
        Instant createdAt
) {}
