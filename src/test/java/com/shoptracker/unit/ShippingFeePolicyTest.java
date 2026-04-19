package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.model.policy.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShippingFeePolicyTest {

    // --- Standard (미구독) ---
    @Test
    void standard_baseFee_3000() {
        var policy = new StandardShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(30000));
        assertThat(result.fee()).isEqualTo(new Money(3000));
        assertThat(result.discountType()).isEqualTo("none");
    }

    @Test
    void standard_freeOver50000() {
        var policy = new StandardShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(50000));
        assertThat(result.fee()).isEqualTo(Money.ZERO);
    }

    // --- Basic ---
    @Test
    void basic_halfFee_1500() {
        var policy = new BasicShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(20000));
        assertThat(result.fee()).isEqualTo(new Money(1500));
        assertThat(result.discountType()).isEqualTo("basic_half");
    }

    @Test
    void basic_freeOver30000() {
        var policy = new BasicShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(30000));
        assertThat(result.fee()).isEqualTo(Money.ZERO);
    }

    @Test
    void basic_freeOver40000() {
        var policy = new BasicShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(40000));
        assertThat(result.fee()).isEqualTo(Money.ZERO);
    }

    // --- Premium ---
    @Test
    void premium_alwaysFree_evenSmallAmount() {
        var policy = new PremiumShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(1000));
        assertThat(result.fee()).isEqualTo(Money.ZERO);
        assertThat(result.discountType()).isEqualTo("premium_free");
    }

    @Test
    void premium_alwaysFree_largeAmount() {
        var policy = new PremiumShippingFeePolicy();
        ShippingFeeResult result = policy.calculateFee(new Money(100000));
        assertThat(result.fee()).isEqualTo(Money.ZERO);
    }
}
