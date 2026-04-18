package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.outbound.ShippingFeePolicy;
import java.math.BigDecimal;

/** 미구독자: 3,000원, 50,000원 이상 무료 */
public class StandardShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));
    private static final Money FREE_THRESHOLD = new Money(new BigDecimal("50000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        if (orderAmount.isGreaterThanOrEqual(FREE_THRESHOLD)) {
            return new ShippingFeeResult(Money.ZERO, BASE_FEE, "none", "50,000원 이상 무료배송");
        }
        return new ShippingFeeResult(BASE_FEE, BASE_FEE, "none", "기본 배송비");
    }
}
