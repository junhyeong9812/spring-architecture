package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejectedEvent(
        UUID paymentId,
        UUID orderId,
        String reason,
        Instant timestamp
) {}