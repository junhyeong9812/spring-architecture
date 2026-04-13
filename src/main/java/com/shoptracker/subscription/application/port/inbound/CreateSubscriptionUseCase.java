package com.shoptracker.subscription.application.port.inbound;

import java.util.UUID;

public interface CreateSubscriptionUseCase {
    UUID create(String customerName, String tier);
}
