package com.shoptracker.payments.application.service;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import com.shoptracker.payments.application.port.inbound.ProcessPaymentUseCase;
import com.shoptracker.payments.domain.model.*;
import com.shoptracker.payments.domain.port.outbound.*;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PaymentCommandService implements ProcessPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DiscountPolicy discountPolicy;   // ★ DI가 구독 등급별로 주입!
    private final ApplicationEventPublisher eventPublisher;

    public PaymentCommandService(PaymentRepository paymentRepository,
                                 PaymentGateway paymentGateway,
                                 DiscountPolicy discountPolicy,
                                 ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.discountPolicy = discountPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID processPayment(ProcessPaymentCommand command) {
        Money originalAmount = new Money(command.totalAmount());

        DiscountResult discount = discountPolicy.calculateDiscount(originalAmount);

        PaymentMethod method = PaymentMethod.valueOf(command.method().toUpperCase());
        Payment payment = Payment.create(command.orderId(), originalAmount, discount, method);

        GatewayResult result = paymentGateway.process(payment);

        if (result.success()) {
            payment.approve();
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new PaymentApprovedEvent(
                    payment.getId().value(), command.orderId(),
                    originalAmount.amount(),
                    discount.discountAmount().amount(),
                    payment.getFinalAmount().amount(),
                    discount.discountType(),
                    method.name().toLowerCase(),
                    Instant.now()
            ));
        } else {
            payment.reject();
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new PaymentRejectedEvent(
                    payment.getId().value(), command.orderId(),
                    result.message(), Instant.now()
            ));
        }

        return payment.getId().value();
    }
}
