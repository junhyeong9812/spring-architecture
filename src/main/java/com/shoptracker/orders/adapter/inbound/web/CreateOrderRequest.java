package com.shoptracker.orders.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerName is required")
        String customerName,

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<OrderItemRequest> items
) {
}
