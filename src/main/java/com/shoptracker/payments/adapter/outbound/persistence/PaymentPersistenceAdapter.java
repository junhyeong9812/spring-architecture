package com.shoptracker.payments.adapter.outbound.persistence;

import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.model.PaymentId;
import com.shoptracker.payments.domain.port.outbound.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentPersistenceAdapter implements PaymentRepository {
    private final SpringDataPaymentRepository jpaRepository;

    public PaymentPersistenceAdapter(SpringDataPaymentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Payment payment) {
        jpaRepository.save(PaymentMapper.toJpa(payment));
    }

    @Override
    public Optional<Payment> findById(PaymentId id) {
        return jpaRepository.findById(id.value())
                .map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId)
                .map(PaymentMapper::toDomain);
    }
}