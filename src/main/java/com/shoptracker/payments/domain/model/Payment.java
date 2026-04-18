package com.shoptracker.payments.domain.model;

import com.shoptracker.orders.domain.model.Money;
import java.time.Instant;
import java.util.UUID;

public class Payment {
    private final PaymentId id;
    private final UUID orderId;
    private final Money originalAmount;
    private final Money discountAmount;
    private final Money finalAmount;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final String appliedDiscountType;
    private Instant processedAt;

    // Factory method
    public static Payment create(UUID orderId, Money originalAmount,
                                 DiscountResult discount, PaymentMethod method) {
        Money finalAmount = originalAmount.subtract(discount.discountAmount());
        return new Payment(
                PaymentId.generate(), orderId,
                originalAmount, discount.discountAmount(), finalAmount,
                method, PaymentStatus.PENDING, discount.discountType(),
                null
        );
    }

    public void approve() {
        this.status = PaymentStatus.APPROVED;
        this.processedAt = Instant.now();
    }

    public void reject() {
        this.status = PaymentStatus.REJECTED;
        this.processedAt = Instant.now();
    }

    public Payment(PaymentId id, UUID orderId, Money originalAmount,
                   Money discountAmount, Money finalAmount, PaymentMethod method,
                   PaymentStatus status, String appliedDiscountType, Instant processedAt) {
        this.id = id;
        this.orderId = orderId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.method = method;
        this.status = status;
        this.appliedDiscountType = appliedDiscountType;
        this.processedAt = processedAt;
    }

    public PaymentId getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public Money getOriginalAmount() { return originalAmount; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getAppliedDiscountType() { return appliedDiscountType; }
    public Instant getProcessedAt() { return processedAt; }
}
