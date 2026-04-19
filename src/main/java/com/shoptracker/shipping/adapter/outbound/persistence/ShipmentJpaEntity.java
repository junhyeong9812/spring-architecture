package com.shoptracker.shipping.adapter.outbound.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class ShipmentJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "street")
    private String street;

    @Column(name = "city")
    private String city;

    @Column(name = "zip_code")
    private String zipCode;

    @Column(name = "shipping_fee", nullable = false)
    private BigDecimal shippingFee;

    @Column(name = "original_fee", nullable = false)
    private BigDecimal originalFee;

    @Column(name = "fee_discount_type", nullable = false)
    private String feeDiscountType;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShipmentJpaEntity() {} // JPA용

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getOriginalFee() { return originalFee; }
    public void setOriginalFee(BigDecimal originalFee) { this.originalFee = originalFee; }
    public String getFeeDiscountType() { return feeDiscountType; }
    public void setFeeDiscountType(String feeDiscountType) { this.feeDiscountType = feeDiscountType; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(LocalDate estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
