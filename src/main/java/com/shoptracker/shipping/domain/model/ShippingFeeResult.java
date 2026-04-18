package com.shoptracker.shipping.domain.model;

import com.shoptracker.orders.domain.model.Money;

public record ShippingFeeResult(
        Money fee,
        Money originalFee,
        String discountType,    // "none", "basic_half", "premium_free"
        String reason
) {}
