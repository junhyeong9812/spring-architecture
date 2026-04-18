package com.shoptracker.payments.domain.model;

import com.shoptracker.orders.domain.model.Money;

public record DiscountResult(
        Money discountAmount,
        String discountType
) {
}
