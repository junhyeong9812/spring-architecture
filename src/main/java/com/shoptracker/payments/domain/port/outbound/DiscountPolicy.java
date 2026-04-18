package com.shoptracker.payments.domain.port.outbound;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;

public interface DiscountPolicy {
    DiscountResult calculateDiscount(Money amount);
}
