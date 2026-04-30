package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class OrderStatusTransitionTest {

    @Test
    void created_canTransitionTo_paymentPending() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
    }

    @Test
    void created_canTransitionTo_cancelled() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void created_cannotTransitionTo_delivered() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void paid_canTransitionTo_shipping() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPING)).isTrue();
    }

    @Test
    void paid_cannotTransitionTo_cancelled() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void delivered_cannotTransitionAnywhere() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(status)).isFalse();
        }
    }
}
