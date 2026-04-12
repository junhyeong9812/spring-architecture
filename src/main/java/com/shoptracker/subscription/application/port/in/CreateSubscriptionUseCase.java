package com.shoptracker.subscription.application.port.in;

import java.util.UUID;

public interface CreateSubscriptionUseCase {
    UUID create(String customerName, String tier);
}
