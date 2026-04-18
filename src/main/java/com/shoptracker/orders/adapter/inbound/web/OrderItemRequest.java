package com.shoptracker.orders.adapter.inbound.web;

public record OrderItemRequest(
        String productName,
        int quantity,
        long unitPrice
) {
}
