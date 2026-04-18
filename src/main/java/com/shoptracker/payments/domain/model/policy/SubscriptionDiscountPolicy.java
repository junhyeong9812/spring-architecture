package com.shoptracker.payments.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.port.outbound.DiscountPolicy;

import java.math.BigDecimal;

public class SubscriptionDiscountPolicy implements DiscountPolicy {
    private final BigDecimal rate;
    private final String discountType;

    public SubscriptionDiscountPolicy(BigDecimal rate, String discountType) {
        this.rate = rate;
        this.discountType = discountType;
    }

    @Override
    public DiscountResult calculateDiscount(Money amount) {
        return new DiscountResult(amount.applyRate(rate), discountType);
    }
}
