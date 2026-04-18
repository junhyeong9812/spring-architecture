package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.outbound.ShippingFeePolicy;
import java.math.BigDecimal;

public class PremiumShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        return new ShippingFeeResult(Money.ZERO, BASE_FEE, "premium_free",
                "Premium 구독 무료배송");
    }
}
