package com.shoptracker.orders.application.port.inbound;

import com.shoptracker.orders.adapter.inbound.web.OrderItemRequest;

import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {
    UUID createOrder(String customerName, List<OrderItemRequest> items);
}
