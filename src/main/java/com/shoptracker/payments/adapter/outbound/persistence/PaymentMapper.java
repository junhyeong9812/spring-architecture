package com.shoptracker.payments.adapter.outbound.persistence;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.model.PaymentId;
import com.shoptracker.payments.domain.model.PaymentMethod;
import com.shoptracker.payments.domain.model.PaymentStatus;

public class PaymentMapper {

    public static Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
                new PaymentId(entity.getId()),
                entity.getOrderId(),
                new Money(entity.getOriginalAmount()),
                new Money(entity.getDiscountAmount()),
                new Money(entity.getFinalAmount()),
                PaymentMethod.valueOf(entity.getMethod()),
                PaymentStatus.valueOf(entity.getStatus()),
                entity.getAppliedDiscountType(),
                entity.getProcessedAt()
        );
    }

    public static PaymentJpaEntity toJpa(Payment domain) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setId(domain.getId().value());
        entity.setOrderId(domain.getOrderId());
        entity.setOriginalAmount(domain.getOriginalAmount().amount());
        entity.setDiscountAmount(domain.getDiscountAmount().amount());
        entity.setFinalAmount(domain.getFinalAmount().amount());
        entity.setMethod(domain.getMethod().name());
        entity.setStatus(domain.getStatus().name());
        entity.setAppliedDiscountType(domain.getAppliedDiscountType());
        entity.setProcessedAt(domain.getProcessedAt());
        return entity;
    }
}