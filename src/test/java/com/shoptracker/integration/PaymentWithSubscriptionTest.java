package com.shoptracker.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.awaitility.Awaitility.*;
import static java.time.Duration.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class PaymentWithSubscriptionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;

    @Test
    void premiumSubscriber_gets10PercentDiscount() throws Exception {
        // 1. Premium 구독 생성
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"customerName": "홍길동", "tier": "premium"}
                    """))
                .andExpect(status().isCreated());

        // 2. 주문 생성 (X-Customer-Name 헤더)
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Name", "홍길동")
                        .content("""
                    {
                      "customerName": "홍길동",
                      "items": [{"productName": "노트북", "quantity": 1, "unitPrice": 1000000}]
                    }
                    """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // orderId 추출 (JSON 파싱)
        String orderId = JsonPath.read(orderResponse, "$.id");

        // 3. 이벤트 처리 대기 후 결제 확인
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/payments/order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appliedDiscountType").value("premium_subscription"))
                    .andExpect(jsonPath("$.discountAmount").value(100000)); // 10% of 1,000,000
        });
    }

    @Test
    void noSubscription_noDiscount() throws Exception {
        String orderResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Name", "미구독자")
                        .content("""
                    {
                      "customerName": "미구독자",
                      "items": [{"productName": "마우스", "quantity": 1, "unitPrice": 50000}]
                    }
                    """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = JsonPath.read(orderResponse, "$.id");

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/payments/order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appliedDiscountType").value("none"))
                    .andExpect(jsonPath("$.discountAmount").value(0));
        });
    }
}
