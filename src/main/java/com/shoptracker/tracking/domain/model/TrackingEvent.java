package com.shoptracker.tracking.domain.model;

import java.time.Instant;
import java.util.Map;

public record TrackingEvent(
        String eventType,
        Instant timestamp,
        String module,
        Map<String, Object> detail
) {}
