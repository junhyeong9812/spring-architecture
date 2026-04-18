package com.shoptracker.orders.application.eventhandler;

import com.shoptracker.orders.domain.model.OrderStatus;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    private final OrderRepository orderRepository;

    public OrderEventHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Payment approved for order {}, transitioning to PAID", event.orderId());
        orderRepository.findById(new OrderId(event.orderId()))
                .ifPresent(order -> {
                    order.transitionTo(OrderStatus.PAID);
                    orderRepository.save(order);
                });
    }

    @ApplicationModuleListener
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.info("Payment rejected for order {}: {}", event.orderId(), event.reason());
        orderRepository.findById(new OrderId(event.orderId()))
                .ifPresent(order -> {
                    order.transitionTo(OrderStatus.CANCELLED);
                    orderRepository.save(order);
                });
    }
}
