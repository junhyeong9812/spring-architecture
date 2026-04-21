package com.shoptracker.tracking.adapter.inbound.web;

import com.shoptracker.tracking.domain.model.OrderTracking;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TrackingTimelineResponse(
        UUID orderId,
        String customerName,
        String currentPhase,
        List<TimelineEntry> events,
        Instant startedAt,
        Instant completedAt
) {
    public record TimelineEntry(
            String eventType,
            Instant timestamp,
            String module,
            Map<String, Object> detail
    ) {}

    public static TrackingTimelineResponse from(OrderTracking t) {
        return new TrackingTimelineResponse(
                t.getOrderId(), t.getCustomerName(),
                t.getCurrentPhase().name().toLowerCase(),
                t.getEvents().stream()
                        .map(e -> new TimelineEntry(
                                e.eventType(), e.timestamp(), e.module(), e.detail()))
                        .toList(),
                t.getStartedAt(), t.getCompletedAt()
        );
    }
}
