package com.shoptracker.tracking.domain.model;

import java.util.UUID;

public record TrackingId(UUID value) {
    public static TrackingId generate() { return new TrackingId(UUID.randomUUID()); }
}
