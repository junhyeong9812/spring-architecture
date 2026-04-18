package com.shoptracker.orders.application.service;

import com.shoptracker.orders.adapter.inbound.web.OrderItemRequest;
import com.shoptracker.orders.application.port.inbound.CreateOrderUseCase;
import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderItem;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import com.shoptracker.shared.events.OrderCreatedEvent;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase {
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
                .map(i -> new OrderItem(i.productName(), i.quantity(),new Money(i.unitPrice())))
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
}
