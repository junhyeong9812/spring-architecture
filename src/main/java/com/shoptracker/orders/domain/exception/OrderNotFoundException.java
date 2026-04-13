package com.shoptracker.orders.domain.exception;

import com.shoptracker.shared.exception.EntityNotFoundException;

import java.util.UUID;

public class OrderNotFoundException extends EntityNotFoundException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
