package com.shoptracker.orders.application.port.inbound;

import java.util.UUID;

public interface CancelOrderUseCase {
    void cancelOrder(UUID orderId);
}
