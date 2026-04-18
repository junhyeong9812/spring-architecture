package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.model.policy.*;
import com.shoptracker.payments.domain.port.outbound.DiscountPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class DiscountPolicyTest {

    @Test
    void premiumSubscription_10PercentDiscount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
                new BigDecimal("0.10"), "premium_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(new Money(10000));
        assertThat(result.discountType()).isEqualTo("premium_subscription");
    }

    @Test
    void basicSubscription_5PercentDiscount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
                new BigDecimal("0.05"), "basic_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(new Money(5000));
        assertThat(result.discountType()).isEqualTo("basic_subscription");
    }

    @Test
    void noSubscription_noDiscount() {
        DiscountPolicy policy = new NoDiscountPolicy();
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(Money.ZERO);
        assertThat(result.discountType()).isEqualTo("none");
    }

    @Test
    void premiumDiscount_onSmallAmount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
                new BigDecimal("0.10"), "premium_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(1500));
        assertThat(result.discountAmount()).isEqualTo(new Money(150)); // 소수점 버림
    }
}
