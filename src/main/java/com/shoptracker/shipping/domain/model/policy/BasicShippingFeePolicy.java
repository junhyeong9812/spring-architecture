package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.outbound.ShippingFeePolicy;
import java.math.BigDecimal;

public class BasicShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));
    private static final Money HALF_FEE = new Money(new BigDecimal("1500"));
    private static final Money FREE_THRESHOLD = new Money(new BigDecimal("30000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        if (orderAmount.isGreaterThanOrEqual(FREE_THRESHOLD)) {
            return new ShippingFeeResult(Money.ZERO, BASE_FEE, "basic_half",
                    "Basic 구독 30,000원 이상 무료배송");
        }
        return new ShippingFeeResult(HALF_FEE, BASE_FEE, "basic_half",
                "Basic 구독 배송비 50% 할인");
    }
}
