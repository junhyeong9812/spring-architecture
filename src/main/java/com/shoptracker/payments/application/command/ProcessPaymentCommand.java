package com.shoptracker.payments.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentCommand(
        UUID orderId,
        BigDecimal totalAmount,
        String method          // "credit_card", "bank_transfer", "virtual_account"
) {
    public ProcessPaymentCommand(UUID orderId, BigDecimal totalAmount) {
        this(orderId, totalAmount, "credit_card"); // 기본값
    }
}
