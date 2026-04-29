package com.shoptracker.orders.application.service;

import com.shoptracker.orders.adapter.inbound.web.OrderItemRequest;
import com.shoptracker.orders.application.port.inbound.CancelOrderUseCase;
import com.shoptracker.orders.application.port.inbound.CreateOrderUseCase;
import com.shoptracker.orders.domain.exception.OrderNotFoundException;
import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.orders.domain.model.OrderItem;
import com.shoptracker.orders.domain.model.OrderStatus;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import com.shoptracker.shared.events.OrderCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase, CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderCommandService(OrderRepository orderRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID createOrder(String customerName, List<OrderItemRequest> items) {
        List<OrderItem> orderItems = items.stream()
                .map(i -> new OrderItem(i.productName(), i.quantity(), new Money(i.unitPrice())))
                .toList();

        Order order = Order.create(customerName, orderItems);
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId().value(),
                customerName,
                order.getTotalAmount().amount(),
                items.size(),
                Instant.now()
        ));

        return order.getId().value();
    }

    @Override
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
