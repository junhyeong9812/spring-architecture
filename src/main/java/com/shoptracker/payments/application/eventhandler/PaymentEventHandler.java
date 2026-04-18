package com.shoptracker.payments.application.eventhandler;

import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import com.shoptracker.payments.application.port.inbound.ProcessPaymentUseCase;
import com.shoptracker.shared.events.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);
    private final ProcessPaymentUseCase processPaymentUseCase;

    public PaymentEventHandler(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @ApplicationModuleListener
    public void on(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order {}, processing payment...",
                event.orderId());

        processPaymentUseCase.processPayment(
                new ProcessPaymentCommand(event.orderId(), event.totalAmount())
        );
    }
}
