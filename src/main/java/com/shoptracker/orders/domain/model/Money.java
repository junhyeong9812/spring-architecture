package com.shoptracker.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, String currency) {
    public static final Money ZERO = new Money(BigDecimal.ZERO, "KRW");

    public Money(BigDecimal amount) {
        this(amount, "KRW");
    }

    public Money(long amount) {
        this(BigDecimal.valueOf(amount), "KRW");
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money applyRate(BigDecimal rate) {
        return new Money(
                this.amount.multiply(rate).setScale(0, RoundingMode.FLOOR),
                this.currency
        );
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isNegativeOrZero() {
        return this.amount.compareTo(BigDecimal.ZERO) <= 0;
    }
}
