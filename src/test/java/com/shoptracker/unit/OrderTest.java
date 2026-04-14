package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

public class OrderTest {
    @Test
    void createOrder_calculatesTotal() {
        Order order = Order.create("홍길동", List.of(
                new OrderItem("노트북", 1, new Money(1000000)),
                new OrderItem("마우스", 2, new Money(25000))
        ));

        assertThat(order.getTotalAmount()).isEqualTo(new Money(1050000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void createOrder_emptyItems_throws() {
        assertThatThrownBy(() -> Order.create("홍길동", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 1 item");
    }

    @Test
    void transitionStatus_validPath() {
        Order order = Order.create("테스트", List.of(
                new OrderItem("상품", 1, new Money(10000))));

        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void transitionStatus_invalidPath_throws() {
        Order order = Order.create("테스트", List.of(
                new OrderItem("상품", 1, new Money(10000))));

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
                .isInstanceOf(IllegalStateException.class);
    }
}
