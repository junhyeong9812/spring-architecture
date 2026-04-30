package com.shoptracker.payments;

import com.shoptracker.shared.events.OrderCreatedEvent;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@Testcontainers
class PaymentsModuleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void orderCreated_triggersPayment(Scenario scenario) {
        scenario.publish(new OrderCreatedEvent(
                        UUID.randomUUID(), "테스트고객",
                        new BigDecimal("50000"), 1, Instant.now()))
                .andWaitForEventOfType(PaymentApprovedEvent.class)
                .matching(e -> e.orderId() != null)
                .toArriveAndVerify(event -> {
                    assertThat(event.finalAmount()).isNotNull();
                });
    }
}
