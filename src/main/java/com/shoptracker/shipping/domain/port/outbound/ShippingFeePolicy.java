package com.shoptracker.shipping.domain.port.outbound;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;

public interface ShippingFeePolicy {
    ShippingFeeResult calculateFee(Money orderAmount);
}
