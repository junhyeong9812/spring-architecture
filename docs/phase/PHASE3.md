# Phase 3: Shipping 모듈 + 구독 배송비 정책

> **목표**: 두 번째 정책 주입 체감 — 구독이 결제 할인과 배송비 할인, 두 곳에 영향을 주되 느슨하게
>
> **선행**: Phase 2 완료 (Payments + 할인 정책 동작)
>
> **예상 소요**: 2~3일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | Shipping이 Subscription을 모른다 | `grep -r "import.*subscription" shipping/` → 0건 |
| 2 | 같은 DI 패턴이 두 번째 모듈에도 적용 | `ShippingPolicyConfig`의 구조가 `PaymentsPolicyConfig`와 동일 |
| 3 | Premium 1,000원 주문도 배송비 무료 | API 호출로 확인 |
| 4 | Basic 40,000원 → 무료, 20,000원 → 1,500원 | 금액 기준과 구독이 복합 작용 |
| 5 | 결제 승인 → 배송 자동 생성 | PaymentApprovedEvent → ShipmentCreated |

---

## Step 1: Shipping 도메인 레이어

### 1.1 엔티티

```java
// src/main/java/com/shoptracker/shipping/domain/model/Shipment.java
package com.shoptracker.shipping.domain.model;

import com.shoptracker.orders.domain.model.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class Shipment {
    private final ShipmentId id;
    private final UUID orderId;
    private ShipmentStatus status;
    private final Address address;
    private final Money shippingFee;
    private final Money originalFee;
    private final String feeDiscountType;
    private String trackingNumber;
    private LocalDate estimatedDelivery;
    private final Instant createdAt;

    public static Shipment create(UUID orderId, Address address,
                                   ShippingFeeResult feeResult) {
        return new Shipment(
            ShipmentId.generate(), orderId,
            ShipmentStatus.PREPARING, address,
            feeResult.fee(), feeResult.originalFee(), feeResult.discountType(),
            generateTrackingNumber(), LocalDate.now().plusDays(3),
            Instant.now()
        );
    }

    public void transitTo(ShipmentStatus newStatus) {
        // PREPARING → IN_TRANSIT → DELIVERED (단방향)
        if (this.status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Already delivered");
        }
        if (this.status == ShipmentStatus.PREPARING && newStatus != ShipmentStatus.IN_TRANSIT) {
            throw new IllegalStateException("Must transit to IN_TRANSIT first");
        }
        this.status = newStatus;
    }

    private static String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // Full constructor, Getters 생략
    public Shipment(ShipmentId id, UUID orderId, ShipmentStatus status,
                    Address address, Money shippingFee, Money originalFee,
                    String feeDiscountType, String trackingNumber,
                    LocalDate estimatedDelivery, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.address = address;
        this.shippingFee = shippingFee;
        this.originalFee = originalFee;
        this.feeDiscountType = feeDiscountType;
        this.trackingNumber = trackingNumber;
        this.estimatedDelivery = estimatedDelivery;
        this.createdAt = createdAt;
    }

    public ShipmentId getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public ShipmentStatus getStatus() { return status; }
    public Address getAddress() { return address; }
    public Money getShippingFee() { return shippingFee; }
    public Money getOriginalFee() { return originalFee; }
    public String getFeeDiscountType() { return feeDiscountType; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
    public Instant getCreatedAt() { return createdAt; }
}
```

```java
// ShipmentId.java, Address.java, ShipmentStatus.java
package com.shoptracker.shipping.domain.model;

public record ShipmentId(java.util.UUID value) {
    public static ShipmentId generate() { return new ShipmentId(java.util.UUID.randomUUID()); }
}

public record Address(String street, String city, String zipCode) {}

public enum ShipmentStatus { PREPARING, IN_TRANSIT, DELIVERED }
```

### 1.2 배송비 정책 인터페이스 + 구현체

```java
// src/main/java/com/shoptracker/shipping/domain/port/out/ShippingFeePolicy.java
package com.shoptracker.shipping.domain.port.out;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;

public interface ShippingFeePolicy {
    ShippingFeeResult calculateFee(Money orderAmount);
}
```

```java
// src/main/java/com/shoptracker/shipping/domain/model/ShippingFeeResult.java
package com.shoptracker.shipping.domain.model;

import com.shoptracker.orders.domain.model.Money;

public record ShippingFeeResult(
    Money fee,
    Money originalFee,
    String discountType,    // "none", "basic_half", "premium_free"
    String reason
) {}
```

```java
// src/main/java/com/shoptracker/shipping/domain/model/policy/StandardShippingFeePolicy.java
package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.out.ShippingFeePolicy;
import java.math.BigDecimal;

/** 미구독자: 3,000원, 50,000원 이상 무료 */
public class StandardShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));
    private static final Money FREE_THRESHOLD = new Money(new BigDecimal("50000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        if (orderAmount.isGreaterThanOrEqual(FREE_THRESHOLD)) {
            return new ShippingFeeResult(Money.ZERO, BASE_FEE, "none", "50,000원 이상 무료배송");
        }
        return new ShippingFeeResult(BASE_FEE, BASE_FEE, "none", "기본 배송비");
    }
}
```

```java
// BasicShippingFeePolicy.java — Basic 구독: 50% 할인, 30,000원 이상 무료
package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.out.ShippingFeePolicy;
import java.math.BigDecimal;

public class BasicShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));
    private static final Money HALF_FEE = new Money(new BigDecimal("1500"));
    private static final Money FREE_THRESHOLD = new Money(new BigDecimal("30000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        if (orderAmount.isGreaterThanOrEqual(FREE_THRESHOLD)) {
            return new ShippingFeeResult(Money.ZERO, BASE_FEE, "basic_half",
                "Basic 구독 30,000원 이상 무료배송");
        }
        return new ShippingFeeResult(HALF_FEE, BASE_FEE, "basic_half",
            "Basic 구독 배송비 50% 할인");
    }
}
```

```java
// PremiumShippingFeePolicy.java — Premium 구독: 항상 무료
package com.shoptracker.shipping.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.ShippingFeeResult;
import com.shoptracker.shipping.domain.port.out.ShippingFeePolicy;
import java.math.BigDecimal;

public class PremiumShippingFeePolicy implements ShippingFeePolicy {
    private static final Money BASE_FEE = new Money(new BigDecimal("3000"));

    @Override
    public ShippingFeeResult calculateFee(Money orderAmount) {
        return new ShippingFeeResult(Money.ZERO, BASE_FEE, "premium_free",
            "Premium 구독 무료배송");
    }
}
```

---

## Step 2: DI 설정 — 배송비 정책 자동 주입

```java
// src/main/java/com/shoptracker/shipping/internal/ShippingPolicyConfig.java
package com.shoptracker.shipping.internal;

import com.shoptracker.shared.SubscriptionContext;
import com.shoptracker.shipping.domain.model.policy.*;
import com.shoptracker.shipping.domain.port.out.ShippingFeePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

/**
 * ★★★ Phase 3 핵심 학습 포인트 ★★★
 *
 * Phase 2의 PaymentsPolicyConfig과 완전히 같은 패턴.
 * Shipping 모듈도 Subscription을 import하지 않고,
 * SubscriptionContext만 보고 적절한 배송비 정책을 조립한다.
 *
 * → 구독권 하나가 두 곳(결제+배송)에 영향을 주지만,
 *   모듈 간 직접 의존은 0건.
 */
@Configuration
class ShippingPolicyConfig {

    @Bean
    @RequestScope
    ShippingFeePolicy shippingFeePolicy(SubscriptionContext subCtx) {
        return switch (subCtx.tier()) {
            case "premium" -> new PremiumShippingFeePolicy();
            case "basic"   -> new BasicShippingFeePolicy();
            default        -> new StandardShippingFeePolicy();
        };
    }
}
```

---

## Step 3: Application 레이어 + Event Handler

```java
// src/main/java/com/shoptracker/shipping/application/service/ShipmentCommandService.java
package com.shoptracker.shipping.application.service;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shared.events.ShipmentCreatedEvent;
import com.shoptracker.shipping.domain.model.*;
import com.shoptracker.shipping.domain.port.out.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ShipmentCommandService {
    private final ShipmentRepository shipmentRepository;
    private final ShippingFeePolicy shippingFeePolicy;  // ★ DI가 구독별로 주입
    private final ApplicationEventPublisher eventPublisher;

    public ShipmentCommandService(ShipmentRepository shipmentRepository,
                                   ShippingFeePolicy shippingFeePolicy,
                                   ApplicationEventPublisher eventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.shippingFeePolicy = shippingFeePolicy;
        this.eventPublisher = eventPublisher;
    }

    public UUID createShipment(UUID orderId, BigDecimal orderAmount) {
        // ★ shippingFeePolicy가 어떤 구현체인지 이 서비스는 모른다!
        ShippingFeeResult feeResult = shippingFeePolicy.calculateFee(
            new Money(orderAmount));

        Address defaultAddress = new Address("서울시 강남구", "서울", "06000");
        Shipment shipment = Shipment.create(orderId, defaultAddress, feeResult);

        shipmentRepository.save(shipment);

        eventPublisher.publishEvent(new ShipmentCreatedEvent(
            shipment.getId().value(), orderId,
            feeResult.fee().amount(), feeResult.discountType(),
            shipment.getTrackingNumber(), Instant.now()
        ));

        return shipment.getId().value();
    }
}
```

```java
// src/main/java/com/shoptracker/shipping/application/eventhandler/ShippingEventHandler.java
package com.shoptracker.shipping.application.eventhandler;

import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shipping.application.service.ShipmentCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * ★ 결제 승인 → 배송 자동 생성
 *   PaymentApprovedEvent를 수신하면 자동으로 Shipment를 생성한다.
 */
@Component
public class ShippingEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ShippingEventHandler.class);
    private final ShipmentCommandService shipmentCommandService;

    public ShippingEventHandler(ShipmentCommandService shipmentCommandService) {
        this.shipmentCommandService = shipmentCommandService;
    }

    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Payment approved for order {}, creating shipment...", event.orderId());
        shipmentCommandService.createShipment(event.orderId(), event.finalAmount());
    }
}
```

### 이벤트 추가 — ShipmentCreated/StatusChanged

```java
// src/main/java/com/shoptracker/shared/events/ShipmentCreatedEvent.java
package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentCreatedEvent(
    UUID shipmentId, UUID orderId,
    BigDecimal shippingFee, String feeDiscountType,
    String trackingNumber, Instant timestamp
) {}
```

```java
// src/main/java/com/shoptracker/shared/events/ShipmentStatusChangedEvent.java
package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedEvent(
    UUID shipmentId, UUID orderId,
    String newStatus, Instant timestamp
) {}
```

### Orders 측 — ShipmentCreated 수신

```java
// OrderEventHandler.java에 추가
@ApplicationModuleListener
public void onShipmentCreated(ShipmentCreatedEvent event) {
    log.info("Shipment created for order {}, transitioning to SHIPPING", event.orderId());
    orderRepository.findById(new OrderId(event.orderId()))
        .ifPresent(order -> {
            order.transitionTo(OrderStatus.SHIPPING);
            orderRepository.save(order);
        });
}
```

---

## Step 4: Flyway 마이그레이션

```sql
-- src/main/resources/db/migration/V4__create_shipments.sql
CREATE TABLE shipments (
    id                 UUID PRIMARY KEY,
    order_id           UUID NOT NULL REFERENCES orders(id),
    status             VARCHAR(20) NOT NULL,
    street             VARCHAR(255),
    city               VARCHAR(100),
    zip_code           VARCHAR(20),
    shipping_fee       DECIMAL(15,2) NOT NULL DEFAULT 0,
    original_fee       DECIMAL(15,2) NOT NULL DEFAULT 0,
    fee_discount_type  VARCHAR(50) NOT NULL DEFAULT 'none',
    tracking_number    VARCHAR(50),
    estimated_delivery DATE,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_shipments_order ON shipments (order_id);
```

---

## Step 5: 테스트

### 5.1 단위 테스트 — 배송비 정책

```java
// src/test/java/com/shoptracker/unit/ShippingFeePolicyTest.java
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
```

### 5.2 통합 테스트

```java
// src/test/java/com/shoptracker/integration/ShippingWithSubscriptionTest.java

@Test
void premiumSubscriber_freeShipping_evenSmallOrder() throws Exception {
    // 1. Premium 구독 생성
    createSubscription("VIP", "premium");

    // 2. 소액 주문
    UUID orderId = createOrder("VIP", "볼펜", 1, 1000);

    // 3. 결제 승인 대기 → 배송 자동 생성
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
    UUID orderId = createOrder("김기본", "마우스", 1, 20000);

    await().atMost(ofSeconds(5)).untilAsserted(() -> {
        mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shippingFee").value(1500));
    });
}

@Test
void basicSubscriber_freeShipping_over30000() throws Exception {
    createSubscription("김기본2", "basic");
    UUID orderId = createOrder("김기본2", "키보드", 2, 20000);  // 40,000원

    await().atMost(ofSeconds(5)).untilAsserted(() -> {
        mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shippingFee").value(0));
    });
}
```

---

## 체감 체크포인트

```bash
# 1. Shipping → Subscription 직접 의존 없음
grep -r "import com.shoptracker.subscription" src/main/java/com/shoptracker/shipping/
# → 0건

# 2. 전체 이벤트 흐름 확인 (로그)
# OrderCreated → PaymentApproved → ShipmentCreated → Order SHIPPING
# 로그에서 3개 이벤트가 순서대로 찍히는지 확인

# 3. 구독 등급별 배송비 (curl)
# Premium: 어떤 금액이든 배송비 0원
# Basic 20,000원: 배송비 1,500원
# Basic 30,000원: 배송비 0원
# 미구독 30,000원: 배송비 3,000원
# 미구독 50,000원: 배송비 0원
```

---

## 다음 Phase 예고

**Phase 4**: Tracking 모듈을 추가하여 주문→결제→배송 **전체 Saga를 타임라인으로 기록**합니다.
결제 실패 시 주문 자동 취소 (보상 트랜잭션)도 확인합니다.