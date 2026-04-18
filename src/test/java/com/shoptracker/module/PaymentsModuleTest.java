package com.shoptracker.module;

import com.shoptracker.shared.events.OrderCreatedEvent;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@ApplicationModuleTest
class PaymentsModuleTest {

    @Test
    void orderCreated_triggersPayment(Scenario scenario) {
        scenario.publish(new OrderCreatedEvent(
                        UUID.randomUUID(), "테스트고객",
                        new BigDecimal("50000"), 1, Instant.now()))
                .andWaitForEventOfType(PaymentApprovedEvent.class)
                .matching(e -> e.orderId() != null)
                .toArriveAndVerify(event -> {
                    // 결제 승인 이벤트가 발행되었는지 확인
                    assertThat(event.finalAmount()).isNotNull();
                });
    }
}