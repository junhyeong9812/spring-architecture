package com.shoptracker.payments.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.port.outbound.DiscountPolicy;

public class NoDiscountPolicy implements DiscountPolicy {
    @Override
    public DiscountResult calculateDiscount(Money amount) {
        return new DiscountResult(Money.ZERO, "none");
    }
}
