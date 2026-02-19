# Phase 2: Spring Events + Payments 모듈 + 구독 할인 정책

> **목표**: 모듈 간 이벤트 통신 + DI 정책 주입을 체감
>
> **선행**: Phase 1 완료 (Subscription + Orders 모듈 동작)
>
> **예상 소요**: 3~4일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | Orders가 Payments를 모른다 | `grep -r "import.*payments" orders/` → 0건 |
| 2 | Payments가 Subscription을 모른다 | `grep -r "import.*subscription" payments/` → 0건 |
| 3 | DI가 구독 등급별 할인 정책을 자동 조립 | Premium → 10%, Basic → 5%, None → 0% |
| 4 | Spring Event로 모듈 간 소통 | `@ApplicationModuleListener`로 비동기 수신 |
| 5 | FakeGateway의 10% 거절 | 여러 번 주문하면 가끔 결제 실패 |

---

## Step 1: 이벤트 추가 정의

Phase 1에서 `OrderCreatedEvent`를 정의했으니, 결제 관련 이벤트를 추가합니다.

```java
// src/main/java/com/shoptracker/shared/events/PaymentApprovedEvent.java
package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
    UUID paymentId,
    UUID orderId,
    BigDecimal originalAmount,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    String appliedDiscountType,   // "none", "basic_subscription", "premium_subscription"
    String method,
    Instant timestamp
) {}
```

```java
// src/main/java/com/shoptracker/shared/events/PaymentRejectedEvent.java
package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRejectedEvent(
    UUID paymentId,
    UUID orderId,
    String reason,
    Instant timestamp
) {}
```

---

## Step 2: Payments 도메인 레이어 (순수 Java)

### 2.1 엔티티

```java
// src/main/java/com/shoptracker/payments/domain/model/Payment.java
package com.shoptracker.payments.domain.model;

import com.shoptracker.orders.domain.model.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * ★ 순수 Java. Spring/JPA import 없음.
 */
public class Payment {
    private final PaymentId id;
    private final UUID orderId;
    private final Money originalAmount;
    private final Money discountAmount;
    private final Money finalAmount;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final String appliedDiscountType;
    private Instant processedAt;

    // Factory method
    public static Payment create(UUID orderId, Money originalAmount,
                                  DiscountResult discount, PaymentMethod method) {
        Money finalAmount = originalAmount.subtract(discount.discountAmount());
        return new Payment(
            PaymentId.generate(), orderId,
            originalAmount, discount.discountAmount(), finalAmount,
            method, PaymentStatus.PENDING, discount.discountType(),
            null
        );
    }

    public void approve() {
        this.status = PaymentStatus.APPROVED;
        this.processedAt = Instant.now();
    }

    public void reject() {
        this.status = PaymentStatus.REJECTED;
        this.processedAt = Instant.now();
    }

    // Constructor, Getters 생략 (모두 작성 필요)
    public Payment(PaymentId id, UUID orderId, Money originalAmount,
                   Money discountAmount, Money finalAmount, PaymentMethod method,
                   PaymentStatus status, String appliedDiscountType, Instant processedAt) {
        this.id = id;
        this.orderId = orderId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.method = method;
        this.status = status;
        this.appliedDiscountType = appliedDiscountType;
        this.processedAt = processedAt;
    }

    public PaymentId getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public Money getOriginalAmount() { return originalAmount; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getAppliedDiscountType() { return appliedDiscountType; }
    public Instant getProcessedAt() { return processedAt; }
}
```

```java
// src/main/java/com/shoptracker/payments/domain/model/PaymentId.java
package com.shoptracker.payments.domain.model;

import java.util.UUID;

public record PaymentId(UUID value) {
    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }
}
```

```java
// PaymentMethod.java, PaymentStatus.java
package com.shoptracker.payments.domain.model;

public enum PaymentMethod {
    CREDIT_CARD, BANK_TRANSFER, VIRTUAL_ACCOUNT
}

public enum PaymentStatus {
    PENDING, APPROVED, REJECTED, REFUNDED
}
```

### 2.2 할인 정책 인터페이스 (Output Port)

```java
// src/main/java/com/shoptracker/payments/domain/port/out/DiscountPolicy.java
package com.shoptracker.payments.domain.port.out;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;

/**
 * ★ Output Port: 할인 정책 인터페이스.
 *   FastAPI의 DiscountPolicy(Protocol)에 대응.
 *   구현체는 domain/model/policy/ 에, 조립은 DI(@Configuration)에서.
 */
public interface DiscountPolicy {
    DiscountResult calculateDiscount(Money amount);
}
```

```java
// src/main/java/com/shoptracker/payments/domain/model/DiscountResult.java
package com.shoptracker.payments.domain.model;

import com.shoptracker.orders.domain.model.Money;

public record DiscountResult(
    Money discountAmount,
    String discountType    // "none", "basic_subscription", "premium_subscription"
) {}
```

### 2.3 할인 정책 구현체

```java
// src/main/java/com/shoptracker/payments/domain/model/policy/SubscriptionDiscountPolicy.java
package com.shoptracker.payments.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.port.out.DiscountPolicy;
import java.math.BigDecimal;

/**
 * ★ 구독 할인 정책.
 *   rate와 discountType을 DI에서 주입받음.
 *   이 클래스는 '구독'이라는 단어를 알지만, Subscription 모듈은 import하지 않는다.
 *   FastAPI의 SubscriptionDiscountPolicy에 대응.
 */
public class SubscriptionDiscountPolicy implements DiscountPolicy {
    private final BigDecimal rate;
    private final String discountType;

    public SubscriptionDiscountPolicy(BigDecimal rate, String discountType) {
        this.rate = rate;
        this.discountType = discountType;
    }

    @Override
    public DiscountResult calculateDiscount(Money amount) {
        return new DiscountResult(amount.applyRate(rate), discountType);
    }
}
```

```java
// src/main/java/com/shoptracker/payments/domain/model/policy/NoDiscountPolicy.java
package com.shoptracker.payments.domain.model.policy;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.port.out.DiscountPolicy;

public class NoDiscountPolicy implements DiscountPolicy {
    @Override
    public DiscountResult calculateDiscount(Money amount) {
        return new DiscountResult(Money.ZERO, "none");
    }
}
```

### 2.4 Payment Gateway 인터페이스 + Fake 구현

```java
// src/main/java/com/shoptracker/payments/domain/port/out/PaymentGateway.java
package com.shoptracker.payments.domain.port.out;

import com.shoptracker.payments.domain.model.GatewayResult;
import com.shoptracker.payments.domain.model.Payment;

public interface PaymentGateway {
    GatewayResult process(Payment payment);
}
```

```java
// src/main/java/com/shoptracker/payments/domain/model/GatewayResult.java
package com.shoptracker.payments.domain.model;

public record GatewayResult(boolean success, String transactionId, String message) {}
```

```java
// src/main/java/com/shoptracker/payments/adapter/out/FakePaymentGateway.java
package com.shoptracker.payments.adapter.out;

import com.shoptracker.payments.domain.model.GatewayResult;
import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.port.out.PaymentGateway;
import org.springframework.stereotype.Component;
import java.util.Random;
import java.util.UUID;

/**
 * ★ 90% 승인, 10% 거절하는 Fake 게이트웨이.
 *   나중에 실제 PG로 교체 시, 이 구현체만 바꾸면 됨 (인터페이스 변경 없음).
 */
@Component
public class FakePaymentGateway implements PaymentGateway {
    private final Random random = new Random();

    @Override
    public GatewayResult process(Payment payment) {
        try {
            Thread.sleep(300); // 네트워크 지연 시뮬레이션 (Virtual Thread에서 효율적)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (random.nextDouble() < 0.9) {
            return new GatewayResult(true, UUID.randomUUID().toString(), "Payment approved");
        }
        return new GatewayResult(false, null, "Insufficient funds");
    }
}
```

### 2.5 Payment Repository

```java
// src/main/java/com/shoptracker/payments/domain/port/out/PaymentRepository.java
package com.shoptracker.payments.domain.port.out;

import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.model.PaymentId;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findById(PaymentId id);
    Optional<Payment> findByOrderId(UUID orderId);
}
```

---

## Step 3: DI 설정 — 구독 등급별 할인 정책 자동 주입

```java
// src/main/java/com/shoptracker/payments/internal/PaymentsPolicyConfig.java
package com.shoptracker.payments.internal;

import com.shoptracker.payments.domain.model.policy.NoDiscountPolicy;
import com.shoptracker.payments.domain.model.policy.SubscriptionDiscountPolicy;
import com.shoptracker.payments.domain.port.out.DiscountPolicy;
import com.shoptracker.payments.domain.port.out.PaymentGateway;
import com.shoptracker.shared.SubscriptionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;
import java.math.BigDecimal;

/**
 * ★★★ Phase 2 핵심 학습 포인트 ★★★
 *
 * 이 파일이 "DI + 정책 주입" 패턴의 중심입니다.
 *
 * Payments 모듈은 Subscription을 import하지 않습니다.
 * 대신 Spring DI가 SubscriptionContext(Request Scope Bean)를 보고
 * 적절한 DiscountPolicy를 조립해줍니다.
 *
 * FastAPI의 PaymentsProvider.discount_policy()의 match 문에 대응:
 *   match sub_ctx.tier:
 *       case "premium": return SubscriptionDiscountPolicy(rate=0.10, ...)
 *       case "basic":   return SubscriptionDiscountPolicy(rate=0.05, ...)
 *       case _:         return NoDiscountPolicy()
 */
@Configuration
class PaymentsPolicyConfig {

    @Bean
    @RequestScope
    DiscountPolicy discountPolicy(SubscriptionContext subCtx) {
        return switch (subCtx.tier()) {
            case "premium" -> new SubscriptionDiscountPolicy(
                new BigDecimal("0.10"), "premium_subscription");
            case "basic" -> new SubscriptionDiscountPolicy(
                new BigDecimal("0.05"), "basic_subscription");
            default -> new NoDiscountPolicy();
        };
    }
}
```

> **★ 이 설정의 의미**:
> 1. HTTP 요청이 오면 `SubscriptionContextConfig`이 `X-Customer-Name`으로 구독 조회
> 2. `SubscriptionContext` Bean이 만들어짐 (tier = "premium" 등)
> 3. `PaymentsPolicyConfig`이 tier를 보고 적절한 `DiscountPolicy` 조립
> 4. `PaymentCommandService`는 주입받은 `DiscountPolicy`를 그냥 사용
> 5. **Payments는 왜 10%인지, 왜 5%인지 전혀 모른다**

---

## Step 4: Payments Application 레이어

### 4.1 Command

```java
// src/main/java/com/shoptracker/payments/application/command/ProcessPaymentCommand.java
package com.shoptracker.payments.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentCommand(
    UUID orderId,
    BigDecimal totalAmount,
    String method          // "credit_card", "bank_transfer", "virtual_account"
) {
    public ProcessPaymentCommand(UUID orderId, BigDecimal totalAmount) {
        this(orderId, totalAmount, "credit_card"); // 기본값
    }
}
```

### 4.2 Use Case (Input Port)

```java
// src/main/java/com/shoptracker/payments/application/port/in/ProcessPaymentUseCase.java
package com.shoptracker.payments.application.port.in;

import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import java.util.UUID;

public interface ProcessPaymentUseCase {
    UUID processPayment(ProcessPaymentCommand command);
}
```

### 4.3 Service (Use Case 구현)

```java
// src/main/java/com/shoptracker/payments/application/service/PaymentCommandService.java
package com.shoptracker.payments.application.service;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import com.shoptracker.payments.application.port.in.ProcessPaymentUseCase;
import com.shoptracker.payments.domain.model.*;
import com.shoptracker.payments.domain.port.out.*;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PaymentCommandService implements ProcessPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final DiscountPolicy discountPolicy;   // ★ DI가 구독 등급별로 주입!
    private final ApplicationEventPublisher eventPublisher;

    public PaymentCommandService(PaymentRepository paymentRepository,
                                  PaymentGateway paymentGateway,
                                  DiscountPolicy discountPolicy,
                                  ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.discountPolicy = discountPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID processPayment(ProcessPaymentCommand command) {
        Money originalAmount = new Money(command.totalAmount());

        // ★ discountPolicy가 어떤 구현체인지 이 서비스는 모른다!
        DiscountResult discount = discountPolicy.calculateDiscount(originalAmount);

        PaymentMethod method = PaymentMethod.valueOf(command.method().toUpperCase());
        Payment payment = Payment.create(command.orderId(), originalAmount, discount, method);

        // Fake Gateway 결제 처리
        GatewayResult result = paymentGateway.process(payment);

        if (result.success()) {
            payment.approve();
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new PaymentApprovedEvent(
                payment.getId().value(), command.orderId(),
                originalAmount.amount(),
                discount.discountAmount().amount(),
                payment.getFinalAmount().amount(),
                discount.discountType(),
                method.name().toLowerCase(),
                Instant.now()
            ));
        } else {
            payment.reject();
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new PaymentRejectedEvent(
                payment.getId().value(), command.orderId(),
                result.message(), Instant.now()
            ));
        }

        return payment.getId().value();
    }
}
```

### 4.4 Event Handler — OrderCreatedEvent 수신

```java
// src/main/java/com/shoptracker/payments/application/eventhandler/PaymentEventHandler.java
package com.shoptracker.payments.application.eventhandler;

import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import com.shoptracker.payments.application.port.in.ProcessPaymentUseCase;
import com.shoptracker.shared.events.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * ★ Spring Modulith의 @ApplicationModuleListener:
 *   - 트랜잭션 커밋 후 비동기 실행
 *   - 이벤트 지속성 (실패 시 자동 재시도)
 *   - FastAPI의 event_bus.subscribe(OrderCreatedEvent, handler)에 대응
 *
 * ★ 핵심: 이 클래스는 Orders 모듈을 import하지 않는다!
 *   shared/events 에 정의된 이벤트만 알면 된다.
 */
@Component
public class PaymentEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);
    private final ProcessPaymentUseCase processPaymentUseCase;

    public PaymentEventHandler(ProcessPaymentUseCase processPaymentUseCase) {
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @ApplicationModuleListener
    public void on(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order {}, processing payment...",
            event.orderId());

        processPaymentUseCase.processPayment(
            new ProcessPaymentCommand(event.orderId(), event.totalAmount())
        );
    }
}
```

---

## Step 5: Orders 측 — PaymentApproved/Rejected 수신

```java
// src/main/java/com/shoptracker/orders/application/eventhandler/OrderEventHandler.java
package com.shoptracker.orders.application.eventhandler;

import com.shoptracker.orders.domain.model.OrderStatus;
import com.shoptracker.orders.domain.port.out.OrderRepository;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    private final OrderRepository orderRepository;

    public OrderEventHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Payment approved for order {}, transitioning to PAID", event.orderId());
        orderRepository.findById(new OrderId(event.orderId()))
            .ifPresent(order -> {
                order.transitionTo(OrderStatus.PAID);
                orderRepository.save(order);
            });
    }

    @ApplicationModuleListener
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.info("Payment rejected for order {}: {}", event.orderId(), event.reason());
        orderRepository.findById(new OrderId(event.orderId()))
            .ifPresent(order -> {
                order.transitionTo(OrderStatus.CANCELLED);
                orderRepository.save(order);
            });
    }
}
```

---

## Step 6: Adapter — Persistence (Payments)

```sql
-- src/main/resources/db/migration/V3__create_payments.sql
CREATE TABLE payments (
    id                    UUID PRIMARY KEY,
    order_id              UUID NOT NULL REFERENCES orders(id),
    original_amount       DECIMAL(15,2) NOT NULL,
    discount_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    final_amount          DECIMAL(15,2) NOT NULL,
    method                VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    applied_discount_type VARCHAR(50) NOT NULL DEFAULT 'none',
    processed_at          TIMESTAMPTZ
);

CREATE INDEX idx_payments_order ON payments (order_id);
```

> JPA Entity, Mapper, PersistenceAdapter 패턴은 Subscription과 동일하므로 생략.
> `PaymentJpaEntity`, `PaymentMapper`, `PaymentPersistenceAdapter`를 동일 패턴으로 생성.

---

## Step 7: Controller (Payments 조회)

```java
// src/main/java/com/shoptracker/payments/adapter/in/web/PaymentController.java
package com.shoptracker.payments.adapter.in.web;

import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.port.out.PaymentRepository;
import com.shoptracker.payments.domain.model.PaymentId;
import com.shoptracker.shared.exception.EntityNotFoundException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentRepository.findById(new PaymentId(id))
            .map(PaymentResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getByOrderId(@PathVariable UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
            .map(PaymentResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "Payment not found for order: " + orderId));
    }
}
```

```java
// src/main/java/com/shoptracker/payments/adapter/in/web/PaymentResponse.java
package com.shoptracker.payments.adapter.in.web;

import com.shoptracker.payments.domain.model.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    BigDecimal originalAmount,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    String method,
    String status,
    String appliedDiscountType,
    Instant processedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
            p.getId().value(), p.getOrderId(),
            p.getOriginalAmount().amount(),
            p.getDiscountAmount().amount(),
            p.getFinalAmount().amount(),
            p.getMethod().name().toLowerCase(),
            p.getStatus().name().toLowerCase(),
            p.getAppliedDiscountType(),
            p.getProcessedAt()
        );
    }
}
```

---

## Step 8: 테스트

### 8.1 단위 테스트 — 할인 정책

```java
// src/test/java/com/shoptracker/unit/DiscountPolicyTest.java
package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.payments.domain.model.DiscountResult;
import com.shoptracker.payments.domain.model.policy.*;
import com.shoptracker.payments.domain.port.out.DiscountPolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class DiscountPolicyTest {

    @Test
    void premiumSubscription_10PercentDiscount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
            new BigDecimal("0.10"), "premium_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(new Money(10000));
        assertThat(result.discountType()).isEqualTo("premium_subscription");
    }

    @Test
    void basicSubscription_5PercentDiscount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
            new BigDecimal("0.05"), "basic_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(new Money(5000));
        assertThat(result.discountType()).isEqualTo("basic_subscription");
    }

    @Test
    void noSubscription_noDiscount() {
        DiscountPolicy policy = new NoDiscountPolicy();
        DiscountResult result = policy.calculateDiscount(new Money(100000));
        assertThat(result.discountAmount()).isEqualTo(Money.ZERO);
        assertThat(result.discountType()).isEqualTo("none");
    }

    @Test
    void premiumDiscount_onSmallAmount() {
        DiscountPolicy policy = new SubscriptionDiscountPolicy(
            new BigDecimal("0.10"), "premium_subscription");
        DiscountResult result = policy.calculateDiscount(new Money(1500));
        assertThat(result.discountAmount()).isEqualTo(new Money(150)); // 소수점 버림
    }
}
```

### 8.2 통합 테스트 — 구독 + 결제 연동

```java
// src/test/java/com/shoptracker/integration/PaymentWithSubscriptionTest.java
package com.shoptracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        String orderId = /* JsonPath로 추출 */;

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

        String orderId = /* JsonPath로 추출 */;

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            mockMvc.perform(get("/api/v1/payments/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedDiscountType").value("none"))
                .andExpect(jsonPath("$.discountAmount").value(0));
        });
    }
}
```

### 8.3 Spring Modulith Scenario 테스트

```java
// src/test/java/com/shoptracker/module/PaymentsModuleTest.java
package com.shoptracker.module;

import com.shoptracker.shared.events.OrderCreatedEvent;
import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shared.events.PaymentRejectedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
                assert event.finalAmount() != null;
            });
    }
}
```

---

## 체감 체크포인트 최종 확인

```bash
# 1. 모듈 간 직접 의존 없음
grep -r "import com.shoptracker.payments" src/main/java/com/shoptracker/orders/
# → 0건
grep -r "import com.shoptracker.subscription" src/main/java/com/shoptracker/payments/
# → 0건

# 2. 단위 테스트
./gradlew test --tests "com.shoptracker.unit.DiscountPolicyTest"
# → 전부 PASS, DB 없이

# 3. API 테스트
# Premium 구독 생성
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"customerName": "홍길동", "tier": "premium"}'

# 주문 생성 → 자동으로 결제 처리됨
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Name: 홍길동" \
  -d '{"customerName": "홍길동", "items": [{"productName": "노트북", "quantity": 1, "unitPrice": 1000000}]}'

# 잠시 후 결제 확인 → discountAmount: 100000 (10% 할인!)
curl http://localhost:8080/api/v1/payments/order/{orderId}
```

---

## 다음 Phase 예고

**Phase 3**: Shipping 모듈을 추가하여 **두 번째 정책 주입** (배송비)을 체감합니다.
구독권 하나가 결제 할인과 배송비 할인, 두 곳에 영향을 주지만 모듈 간 직접 의존은 없습니다.