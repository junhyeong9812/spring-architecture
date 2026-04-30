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
class ShippingWithSubscriptionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;

    @Test
    void premiumSubscriber_freeShipping_evenSmallOrder() throws Exception {
        createSubscription("VIP", "premium");
        String orderId = createOrder("VIP", "볼펜", 1, 1000);

        // 결제 승인 → 배송 자동 생성 대기
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingFee").value(0))
                    .andExpect(jsonPath("$.feeDiscountType").value("premium_free"));
        });
    }

    @Test
    void basicSubscriber_halfFee_under30000() throws Exception {
        createSubscription("김기본", "basic");
        String orderId = createOrder("김기본", "마우스", 1, 20000);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingFee").value(1500));
        });
    }

    @Test
    void basicSubscriber_freeShipping_over30000() throws Exception {
        createSubscription("김기본2", "basic");
        String orderId = createOrder("김기본2", "키보드", 2, 20000);  // 40,000원

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shippingFee").value(0));
        });
    }

    private void createSubscription(String customerName, String tier) throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"customerName": "%s", "tier": "%s"}
                    """.formatted(customerName, tier)))
                .andExpect(status().isCreated());
    }

    private String createOrder(String customerName, String productName,
                               int quantity, int unitPrice) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Name", customerName)
                        .content("""
                    {
                      "customerName": "%s",
                      "items": [{"productName": "%s", "quantity": %d, "unitPrice": %d}]
                    }
                    """.formatted(customerName, productName, quantity, unitPrice)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }
}