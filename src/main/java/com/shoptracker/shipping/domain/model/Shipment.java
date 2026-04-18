package com.shoptracker.shipping.domain.model;

import com.shoptracker.orders.domain.model.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class Shipment {
    private final ShipmentId id;
    private final UUID orderId;
    private ShipmentStatus status;
    private final Address address;
    private final Money shippingFee;
    private final Money originalFee;
    private final String feeDiscountType;
    private String trackingNumber;
    private LocalDate estimatedDelivery;
    private final Instant createdAt;

    public static Shipment create(UUID orderId, Address address,
                                  ShippingFeeResult feeResult) {
        return new Shipment(
                ShipmentId.generate(), orderId,
                ShipmentStatus.PREPARING, address,
                feeResult.fee(), feeResult.originalFee(), feeResult.discountType(),
                generateTrackingNumber(), LocalDate.now().plusDays(3),
                Instant.now()
        );
    }

    public void transitTo(ShipmentStatus newStatus) {
        // PREPARING → IN_TRANSIT → DELIVERED (단방향)
        if (this.status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Already delivered");
        }
        if (this.status == ShipmentStatus.PREPARING && newStatus != ShipmentStatus.IN_TRANSIT) {
            throw new IllegalStateException("Must transit to IN_TRANSIT first");
        }
        this.status = newStatus;
    }

    private static String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // Full constructor, Getters 생략
    public Shipment(ShipmentId id, UUID orderId, ShipmentStatus status,
                    Address address, Money shippingFee, Money originalFee,
                    String feeDiscountType, String trackingNumber,
                    LocalDate estimatedDelivery, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.address = address;
        this.shippingFee = shippingFee;
        this.originalFee = originalFee;
        this.feeDiscountType = feeDiscountType;
        this.trackingNumber = trackingNumber;
        this.estimatedDelivery = estimatedDelivery;
        this.createdAt = createdAt;
    }

    public ShipmentId getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public ShipmentStatus getStatus() { return status; }
    public Address getAddress() { return address; }
    public Money getShippingFee() { return shippingFee; }
    public Money getOriginalFee() { return originalFee; }
    public String getFeeDiscountType() { return feeDiscountType; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
    public Instant getCreatedAt() { return createdAt; }
}
