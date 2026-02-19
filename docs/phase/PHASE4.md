# Phase 4: Tracking 모듈 + 전체 Saga

> **목표**: 모든 이벤트가 연결되고, 전체 여정 추적 + 실패 보상 흐름까지 동작
>
> **선행**: Phase 3 완료 (Orders → Payments → Shipping 이벤트 체인 동작)
>
> **예상 소요**: 2~3일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | 주문 1개 → Tracking 타임라인에 모든 이벤트 기록 | GET /tracking/order/{id}/timeline |
| 2 | 결제 실패 → 주문 자동 CANCELLED | FakeGateway 10% 거절 → 상태 확인 |
| 3 | Tracking이 모든 모듈의 이벤트를 구독 | 하지만 다른 모듈을 직접 import하지 않음 |
| 4 | 구독 활성화/만료도 추적에 기록 | SubscriptionActivatedEvent 수신 |
| 5 | Spring Modulith 문서 자동 생성 | PlantUML 다이어그램 |

---

## Step 1: Tracking 도메인 레이어

```java
// tracking/domain/model/OrderTracking.java
public class OrderTracking {
    private final TrackingId id;
    private final UUID orderId;
    private final String customerName;
    private final String subscriptionTier;  // 주문 시점 스냅샷
    private final List<TrackingEvent> events;
    private TrackingPhase currentPhase;
    private final Instant startedAt;
    private Instant completedAt;

    public static OrderTracking create(UUID orderId, String customerName,
                                        String subscriptionTier) {
        var tracking = new OrderTracking(
            TrackingId.generate(), orderId, customerName, subscriptionTier,
            new ArrayList<>(), TrackingPhase.ORDER_PLACED, Instant.now(), null);
        tracking.addEvent("order.created", "orders",
            Map.of("message", "주문이 생성되었습니다"));
        return tracking;
    }

    public void addEvent(String eventType, String module, Map<String, Object> detail) {
        this.events.add(new TrackingEvent(eventType, Instant.now(), module, detail));
    }

    public void updatePhase(TrackingPhase phase) {
        this.currentPhase = phase;
        if (phase == TrackingPhase.DELIVERED || phase == TrackingPhase.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    // Constructor, Getters 생략 (위 패턴과 동일)
}

// tracking/domain/model/TrackingEvent.java
public record TrackingEvent(
    String eventType,
    Instant timestamp,
    String module,
    Map<String, Object> detail
) {}

// tracking/domain/model/TrackingPhase.java
public enum TrackingPhase {
    ORDER_PLACED, PAYMENT_PROCESSING, PAYMENT_COMPLETED,
    SHIPPING, DELIVERED, FAILED
}

// tracking/domain/model/TrackingId.java
public record TrackingId(UUID value) {
    public static TrackingId generate() { return new TrackingId(UUID.randomUUID()); }
}

// tracking/domain/port/out/TrackingRepository.java
public interface TrackingRepository {
    void save(OrderTracking tracking);
    Optional<OrderTracking> findByOrderId(UUID orderId);
}
```

---

## Step 2: Tracking Event Handler — 모든 이벤트 구독

```java
// tracking/application/eventhandler/TrackingEventHandler.java
package com.shoptracker.tracking.application.eventhandler;

import com.shoptracker.shared.events.*;
import com.shoptracker.tracking.domain.model.*;
import com.shoptracker.tracking.domain.port.out.TrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ★★★ Phase 4 핵심 ★★★
 *
 * Tracking 모듈은 모든 도메인 이벤트를 구독하여 기록한다.
 * 하지만 다른 모듈(orders, payments, shipping, subscription)을
 * 직접 import하지 않는다.
 * shared/events 에 정의된 이벤트 record만 알면 된다.
 *
 * FastAPI의 tracking/application/event_handlers.py에 대응.
 */
@Component
public class TrackingEventHandler {
    private static final Logger log = LoggerFactory.getLogger(TrackingEventHandler.class);
    private final TrackingRepository trackingRepository;

    public TrackingEventHandler(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    // ── 주문 생성 ──
    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[Tracking] Order created: {}", event.orderId());

        OrderTracking tracking = OrderTracking.create(
            event.orderId(), event.customerName(), "unknown"); // 구독 tier는 별도 조회 가능

        trackingRepository.save(tracking);
    }

    // ── 결제 승인 ──
    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("[Tracking] Payment approved for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("payment.approved", "payments", Map.of(
                "paymentId", event.paymentId().toString(),
                "originalAmount", event.originalAmount().toString(),
                "discountAmount", event.discountAmount().toString(),
                "finalAmount", event.finalAmount().toString(),
                "discountType", event.appliedDiscountType(),
                "method", event.method()
            ));
            tracking.updatePhase(TrackingPhase.PAYMENT_COMPLETED);
            trackingRepository.save(tracking);
        });
    }

    // ── 결제 거절 ──
    @ApplicationModuleListener
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.info("[Tracking] Payment rejected for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("payment.rejected", "payments", Map.of(
                "reason", event.reason()
            ));
            tracking.updatePhase(TrackingPhase.FAILED);
            trackingRepository.save(tracking);
        });
    }

    // ── 배송 생성 ──
    @ApplicationModuleListener
    public void onShipmentCreated(ShipmentCreatedEvent event) {
        log.info("[Tracking] Shipment created for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("shipment.created", "shipping", Map.of(
                "shipmentId", event.shipmentId().toString(),
                "shippingFee", event.shippingFee().toString(),
                "feeDiscountType", event.feeDiscountType(),
                "trackingNumber", event.trackingNumber() != null
                    ? event.trackingNumber() : ""
            ));
            tracking.updatePhase(TrackingPhase.SHIPPING);
            trackingRepository.save(tracking);
        });
    }

    // ── 배송 상태 변경 ──
    @ApplicationModuleListener
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) {
        log.info("[Tracking] Shipment status changed for order: {} → {}",
            event.orderId(), event.newStatus());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("shipment.status_changed", "shipping", Map.of(
                "newStatus", event.newStatus()
            ));
            if ("DELIVERED".equals(event.newStatus())) {
                tracking.updatePhase(TrackingPhase.DELIVERED);
            }
            trackingRepository.save(tracking);
        });
    }

    // ── 구독 활성화 ──
    @ApplicationModuleListener
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info("[Tracking] Subscription activated: {} ({})",
            event.customerName(), event.tier());
        // 구독은 특정 주문에 종속되지 않으므로, 별도 로그만 남기거나
        // 고객 기준으로 관련 tracking을 업데이트할 수 있음
    }
}
```

---

## Step 3: Tracking API

```java
// tracking/adapter/in/web/TrackingController.java
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {
    private final TrackingRepository trackingRepository;

    public TrackingController(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @GetMapping("/order/{orderId}")
    public TrackingResponse getByOrderId(@PathVariable UUID orderId) {
        return trackingRepository.findByOrderId(orderId)
            .map(TrackingResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "Tracking not found for order: " + orderId));
    }

    @GetMapping("/order/{orderId}/timeline")
    public TrackingTimelineResponse getTimeline(@PathVariable UUID orderId) {
        return trackingRepository.findByOrderId(orderId)
            .map(TrackingTimelineResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "Tracking not found for order: " + orderId));
    }
}
```

```java
// tracking/adapter/in/web/TrackingTimelineResponse.java
public record TrackingTimelineResponse(
    UUID orderId,
    String customerName,
    String currentPhase,
    List<TimelineEntry> events,
    Instant startedAt,
    Instant completedAt
) {
    public record TimelineEntry(
        String eventType,
        Instant timestamp,
        String module,
        Map<String, Object> detail
    ) {}

    public static TrackingTimelineResponse from(OrderTracking t) {
        return new TrackingTimelineResponse(
            t.getOrderId(), t.getCustomerName(),
            t.getCurrentPhase().name().toLowerCase(),
            t.getEvents().stream()
                .map(e -> new TimelineEntry(
                    e.eventType(), e.timestamp(), e.module(), e.detail()))
                .toList(),
            t.getStartedAt(), t.getCompletedAt()
        );
    }
}
```

---

## Step 4: Flyway 마이그레이션

```sql
-- src/main/resources/db/migration/V5__create_tracking.sql
CREATE TABLE order_tracking (
    id                UUID PRIMARY KEY,
    order_id          UUID NOT NULL,
    customer_name     VARCHAR(255) NOT NULL,
    subscription_tier VARCHAR(20),
    current_phase     VARCHAR(30) NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ
);

CREATE TABLE tracking_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_id UUID NOT NULL REFERENCES order_tracking(id),
    event_type VARCHAR(50) NOT NULL,
    timestamp  TIMESTAMPTZ NOT NULL,
    module     VARCHAR(50) NOT NULL,
    detail     JSONB NOT NULL DEFAULT '{}',
    CONSTRAINT fk_tracking FOREIGN KEY (tracking_id) REFERENCES order_tracking(id)
);

CREATE INDEX idx_tracking_order ON order_tracking (order_id);
CREATE INDEX idx_tracking_events_tracking ON tracking_events (tracking_id);
```

---

## Step 5: 전체 Saga 테스트

```java
// integration/FullSagaIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class FullSagaIntegrationTest {

    @Container
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

    // Helper methods
    private void createSubscription(String name, String tier) throws Exception { /* ... */ }
    private UUID createOrder(String name, String product, int qty, long price) throws Exception { /* ... */ }
}
```

---

## Step 6: Spring Modulith 문서 자동 생성

```java
// ModuleStructureTest.java 에 추가
@Test
void createModuleDocumentation() {
    ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
    new Documenter(modules)
        .writeModulesAsPlantUml()                    // 전체 모듈 관계도
        .writeIndividualModulesAsPlantUml();          // 모듈별 상세 다이어그램
    // 결과: build/spring-modulith-docs/ 에 .puml 파일 생성
}
```

생성된 PlantUML을 통해 아래와 같은 다이어그램이 자동으로 만들어집니다:

```
[orders] --> [shared] : uses events
[payments] --> [shared] : uses events, SubscriptionContext
[shipping] --> [shared] : uses events, SubscriptionContext
[tracking] --> [shared] : uses events
[subscription] --> [shared] : provides SubscriptionContext
```

---

## 체감 체크포인트

```bash
# 1. 전체 흐름 테스트
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"customerName": "김구독", "tier": "basic"}'

curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Name: 김구독" \
  -d '{"customerName": "김구독", "items": [{"productName": "키보드", "quantity": 2, "unitPrice": 20000}]}'

# 2. 타임라인 확인 (2초 후)
curl http://localhost:8080/api/v1/tracking/order/{orderId}/timeline | jq .

# 예상 결과:
# {
#   "orderId": "...",
#   "customerName": "김구독",
#   "currentPhase": "shipping",
#   "events": [
#     {"eventType": "order.created", "module": "orders", ...},
#     {"eventType": "payment.approved", "module": "payments",
#      "detail": {"discountType": "basic_subscription", "discountAmount": "2000", ...}},
#     {"eventType": "shipment.created", "module": "shipping",
#      "detail": {"shippingFee": "0", "feeDiscountType": "basic_half", ...}}
#   ]
# }

# 3. Tracking 모듈이 다른 모듈을 import하지 않는지 확인
grep -r "import com.shoptracker.orders\|import com.shoptracker.payments\|import com.shoptracker.shipping" \
  src/main/java/com/shoptracker/tracking/
# → 0건

# 4. Spring Modulith 모듈 경계 검증
./gradlew test --tests "com.shoptracker.ModuleStructureTest"
# → PASS
```

---

## 다음 Phase 예고

**Phase 5**: CQRS를 고도화하고 (ReadModel, 페이지네이션), OpenTelemetry 관찰성을 추가합니다.