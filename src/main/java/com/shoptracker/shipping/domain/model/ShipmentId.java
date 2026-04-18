package com.shoptracker.shipping.domain.model;

public record ShipmentId(java.util.UUID value) {
    public static ShipmentId generate() { return new ShipmentId(java.util.UUID.randomUUID()); }
}
