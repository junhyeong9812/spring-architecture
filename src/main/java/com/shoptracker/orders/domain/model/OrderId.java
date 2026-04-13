package com.shoptracker.orders.domain.model;

import java.util.UUID;

public record OrderId(UUID value) {
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }
}
