package com.shoptracker.orders.domain.model;

public enum OrderStatus {
    CREATED, PAYMENT_PENDING, PAID, SHIPPING, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPING;
            case SHIPPING -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
