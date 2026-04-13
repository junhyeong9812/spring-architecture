package com.shoptracker.orders.domain.model;

public record OrderItem(
        String productName,
        int quantity,
        Money unitPrice
) {
    public Money subtotal() {
        return new Money(unitPrice.amount().multiply(java.math.BigDecimal.valueOf(quantity)));
    }
}
