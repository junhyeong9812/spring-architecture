package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedEvent(
        UUID shipmentId, UUID orderId,
        String newStatus, Instant timestamp
) {}
