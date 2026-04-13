package com.shoptracker.orders.domain.model;

import java.time.Instant;
import java.util.List;

public class Order {
    private final OrderId id;
    private final String customerName;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Money totalAmount;
    private Money shippingFee;
    private Money discountAmount;
    private Money finalAmount;
    private final Instant createdAt;

    public Order(OrderId id, String customerName, List<OrderItem> items,
                 OrderStatus status, Money totalAmount, Money shippingFee,
                 Money discountAmount, Money finalAmount, Instant createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.items = List.copyOf(items); // 불변 복사
        this.status = status;
        this.totalAmount = totalAmount;
        this.shippingFee = shippingFee;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.createdAt = createdAt;
    }

    public static Order create(String customerName, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least 1 item");
        }

        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.ZERO, Money::add);

        if (total.isNegativeOrZero()) {
            throw new IllegalArgumentException("Order total must be positive");
        }

        return new Order(
                OrderId.generate(), customerName, items,
                OrderStatus.CREATED, total,
                Money.ZERO, Money.ZERO, total,
                Instant.now()
        );
    }

    public void transitionTo(OrderStatus newStatus) {
        if (! this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void applyPricing(Money shippingFee, Money discountAmount) {
        this.shippingFee = shippingFee;
        this.discountAmount = discountAmount;
        this.finalAmount = totalAmount.add(shippingFee).subtract(discountAmount);
    }

    public OrderId getId() { return id; }
    public String getCustomerName() { return customerName; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getShippingFee() { return shippingFee; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
