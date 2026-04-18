package com.shoptracker.payments.adapter.outbound;

import com.shoptracker.payments.domain.model.GatewayResult;
import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.port.outbound.PaymentGateway;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {
    private final Random random = new Random();

    @Override
    public GatewayResult process(Payment payment) {
        try {
            Thread.sleep(300); // 네트워크 지연 시뮬레이션 (Virtual Thread에서 효율적)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (random.nextDouble() < 0.9) {
            return new GatewayResult(true, UUID.randomUUID().toString(), "Payment approved");
        }
        return new GatewayResult(false, null, "Insufficient funds");
    }
}
