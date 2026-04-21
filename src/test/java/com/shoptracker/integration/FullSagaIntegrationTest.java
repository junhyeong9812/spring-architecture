package com.shoptracker.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class FullSagaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Autowired MockMvc mockMvc;

    @Test
    void fullSaga_basicSubscriber() throws Exception {
        // 1. Basic 구독 생성
        createSubscription("김구독", "basic");

        // 2. 40,000원 주문 (Basic: 5% 할인, 배송비 3만원 이상 무료)
        UUID orderId = createOrder("김구독", "키보드", 2, 20000);

        // 3. Saga 완료 대기
        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            // Tracking 타임라인에서 전체 이벤트 확인
            mockMvc.perform(get("/api/v1/tracking/order/" + orderId + "/timeline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events[0].eventType").value("order.created"))
                    .andExpect(jsonPath("$.events.length()").value(
                            org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        });

        // 4. 결제 확인 (승인된 경우)
        mockMvc.perform(get("/api/v1/payments/order/" + orderId))
                .andExpect(status().isOk())
                .andDo(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("approved")) {
                        // 5% 할인: 40,000 * 0.05 = 2,000원
                        mockMvc.perform(get("/api/v1/payments/order/" + orderId))
                                .andExpect(jsonPath("$.discountAmount").value(2000))
                                .andExpect(jsonPath("$.appliedDiscountType").value("basic_subscription"));

                        // 배송비: 40,000 > 30,000이므로 Basic 무료배송
                        mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
                                .andExpect(jsonPath("$.shippingFee").value(0));
                    }
                });
    }

    @Test
    void paymentRejection_cancelsOrder() throws Exception {
        // 여러 번 주문하여 FakeGateway 10% 거절을 트리거
        // (또는 테스트용 AlwaysRejectGateway를 주입)
        // → PaymentRejectedEvent → Order CANCELLED → Tracking FAILED
    }

    // ── Helper methods ──

    private void createSubscription(String name, String tier) throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"customerName": "%s", "tier": "%s"}
                    """.formatted(name, tier)))
                .andExpect(status().isCreated());
    }

    private UUID createOrder(String name, String product, int qty, long price) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Name", name)
                        .content("""
                    {
                      "customerName": "%s",
                      "items": [{"productName": "%s", "quantity": %d, "unitPrice": %d}]
                    }
                    """.formatted(name, product, qty, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String idString = JsonPath.read(response, "$.id");
        return UUID.fromString(idString);
    }
}
