package com.shoptracker.payments.application.service;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import com.shoptracker.payments.application.port.inbound.ProcessPaymentUseCase;
import com.shoptracker.payments.domain.model.*;
import com.shoptracker.payments.domain.port.outbound.*;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import io.micrometer.observation.annotation.Observed;
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
    private final DiscountPolicy discountPolicy;
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

    /**
     *   @Observed: 이 메서드 호출이 자동으로
     *   - Trace span 생성
     *   - Timer metric 기록 (처리 시간)
     *   - Counter metric (호출 횟수)
     */
    @Override
    @Observed(name = "payment.process",
            contextualName = "process-payment",
            lowCardinalityKeyValues = {"module", "payments"})
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
