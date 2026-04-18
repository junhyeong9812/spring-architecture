package com.shoptracker.payments.domain.port.outbound;

import com.shoptracker.payments.domain.model.GatewayResult;
import com.shoptracker.payments.domain.model.Payment;

public interface PaymentGateway {
    GatewayResult process(Payment payment);
}
