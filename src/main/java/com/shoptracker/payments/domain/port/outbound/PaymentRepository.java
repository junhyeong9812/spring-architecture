package com.shoptracker.payments.domain.port.outbound;

import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.model.PaymentId;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findById(PaymentId id);
    Optional<Payment> findByOrderId(UUID orderId);
}
