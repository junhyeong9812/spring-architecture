package com.shoptracker.orders.application.service;

import com.shoptracker.orders.application.query.ListOrdersQuery;
import com.shoptracker.orders.application.query.OrderSummary;
import com.shoptracker.orders.domain.exception.OrderNotFoundException;
import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {
    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderSummary getOrder(UUID orderId) {
        return orderRepository.findById(new OrderId(orderId))
                .map(this::toSummary)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public Page<OrderSummary> listOrders(ListOrdersQuery query) {
        Pageable pageable = PageRequest.of(
                query.page(), query.size(),
                Sort.by(Sort.Direction.fromString(query.sortDir()), query.sortBy())
        );

        Page<Order> orders;
        if (query.customerName() != null) {
            orders = orderRepository.findAllByCustomerName(query.customerName(), pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(this::toSummary);
    }

    private OrderSummary toSummary(Order order) {
        return new OrderSummary(
                order.getId().value(),
                order.getCustomerName(),
                order.getStatus().name().toLowerCase(),
                order.getTotalAmount().amount(),
                order.getShippingFee().amount(),
                order.getDiscountAmount().amount(),
                order.getFinalAmount().amount(),
                order.getItems().size(),
                order.getCreatedAt()
        );
    }
}
