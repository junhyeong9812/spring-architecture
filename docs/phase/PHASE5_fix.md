# Phase 5 누락/오류 수정 가이드

> Phase 1~5 문서와 실제 구현을 대조하여 발견된 문제와 수정 방법.
> 우선순위: A(컴파일/부팅 차단) → B(런타임 동작 어긋남) → C(정리).

---

## A. 컴파일/부팅을 막는 치명적 누락

### A-1. `CreateOrderRequest` 빈 record

**파일**: `src/main/java/com/shoptracker/orders/adapter/inbound/web/CreateOrderRequest.java`

**현재 (잘못됨)**
```java
package com.shoptracker.orders.adapter.inbound.web;

public record CreateOrderRequest() {
}
```

**문제**
- 필드가 전무. `OrderController.create()`에서 `request.items()`를 호출하지만 메서드 자체가 없어 **컴파일 실패**.
- PHASE1 Step 4의 컨트롤러 패턴에 명시된 `items` 필드 누락.

**수정**
```java
package com.shoptracker.orders.adapter.inbound.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerName is required")
        String customerName,

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<OrderItemRequest> items
) {
}
```

> 참고: 컨트롤러는 `customerName`을 헤더(`X-Customer-Name`)로 받기 때문에 body의 `customerName`은 사실상 중복이지만, PHASE1/2의 통합 테스트가 body에도 customerName을 넣어 보내므로 같이 받도록 둔다. 헤더 우선이면 body 필드는 빼도 됨.

`OrderItemRequest`도 검증 어노테이션을 함께 보강하면 좋음 (`OrderItemRequest.java`):
```java
public record OrderItemRequest(
        @NotBlank String productName,
        @Positive int quantity,
        @Positive long unitPrice
) {
}
```

---

### A-2. `OrderCommandService.cancelOrder()` 메서드 미구현

**파일**:
- `src/main/java/com/shoptracker/orders/application/service/OrderCommandService.java`
- `src/main/java/com/shoptracker/orders/application/port/inbound/CreateOrderUseCase.java`
- `src/main/java/com/shoptracker/orders/adapter/inbound/web/OrderController.java:64`

**문제**
`OrderController.cancel()`에서 `commandService.cancelOrder(id)`를 호출하지만, 서비스에는 `createOrder`만 존재. 인터페이스에도 정의 없음 → **컴파일 실패**.

**수정 1: UseCase 분리 권장**

`CreateOrderUseCase`는 그대로 두고, 새 인터페이스 추가:

`src/main/java/com/shoptracker/orders/application/port/inbound/CancelOrderUseCase.java` (신규)
```java
package com.shoptracker.orders.application.port.inbound;

import java.util.UUID;

public interface CancelOrderUseCase {
    void cancelOrder(UUID orderId);
}
```

**수정 2: `OrderCommandService`에 메서드 추가**

`OrderCommandService.java` (전체 교체)
```java
package com.shoptracker.orders.application.service;

import com.shoptracker.orders.adapter.inbound.web.OrderItemRequest;
import com.shoptracker.orders.application.port.inbound.CancelOrderUseCase;
import com.shoptracker.orders.application.port.inbound.CreateOrderUseCase;
import com.shoptracker.orders.domain.exception.OrderNotFoundException;
import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.orders.domain.model.OrderItem;
import com.shoptracker.orders.domain.model.OrderStatus;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import com.shoptracker.shared.events.OrderCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase, CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderCommandService(OrderRepository orderRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID createOrder(String customerName, List<OrderItemRequest> items) {
        List<OrderItem> orderItems = items.stream()
                .map(i -> new OrderItem(i.productName(), i.quantity(), new Money(i.unitPrice())))
                .toList();

        Order order = Order.create(customerName, orderItems);
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId().value(),
                customerName,
                order.getTotalAmount().amount(),
                items.size(),
                Instant.now()
        ));

        return order.getId().value();
    }

    @Override
    public void cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.transitionTo(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
```

> `Transactional` 은 반드시 `org.springframework.transaction.annotation.Transactional` 사용 (B-3 참조).

---

### A-3. `SubscriptionPersistenceAdapter`에 빈 등록 어노테이션 누락

**파일**: `src/main/java/com/shoptracker/subscription/adapter/outbound/persistence/SubscriptionPersistenceAdapter.java:9`

**현재 (잘못됨)**
```java
public class SubscriptionPersistenceAdapter implements SubscriptionRepository {
```

**문제**
어노테이션 없음 → Spring 컨테이너가 빈으로 등록하지 않음 → `SubscriptionRepository` 빈을 의존하는 `SubscriptionCommandService`/`SubscriptionQueryService` 주입 실패 → **앱 부팅 실패**.

다른 모듈은 모두 `@Repository`(`OrderPersistenceAdapter`, `ShipmentPersistenceAdapter`) 또는 `@Component`(`PaymentPersistenceAdapter`, `TrackingPersistenceAdapter`)가 붙어 있음.

**수정**
```java
package com.shoptracker.subscription.adapter.outbound.persistence;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import com.shoptracker.subscription.domain.port.outbound.SubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SubscriptionPersistenceAdapter implements SubscriptionRepository {
    // ... 기존 그대로
}
```

---

## B. 런타임에 문서 의도와 어긋남

### B-1. `GlobalExceptionHandler`가 사실상 죽어있음

**파일**: `src/main/java/com/shoptracker/shared/exception/GlobalExceptionHandler.java`

**현재 (잘못됨)**
```java
package com.shoptracker.shared.exception;

import jakarta.persistence.EntityNotFoundException;   // ← 잘못된 import
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
// @RestControllerAdvice 누락!

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)   // ← JPA의 것을 잡고 있음
    public ProblemDetail handleNotFound(EntityNotFoundException ex) { ... }
    ...
}
```

**문제 두 가지**
1. **`@RestControllerAdvice` 어노테이션 누락** → Spring이 핸들러로 인식하지 않음. 모든 예외가 기본(500) 처리.
2. **`import jakarta.persistence.EntityNotFoundException`** → JPA의 `EntityNotFoundException`을 import하고 있음. 그런데 컨트롤러들(`SubscriptionController`, `PaymentController`, `ShipmentController`, `TrackingController`, `OrderQueryService`의 `OrderNotFoundException`)은 모두 프로젝트 자체의 `com.shoptracker.shared.exception.EntityNotFoundException`을 던짐. **타입 불일치로 핸들러에 매칭되지 않음** → 모든 NotFound가 500으로 떨어짐.

**수정 (전체 교체)**
```java
package com.shoptracker.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Resource Not Found");
        detail.setType(URI.create("https://shoptracker.dev/errors/not-found"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        detail.setTitle("Business Rule Violation");
        detail.setType(URI.create("https://shoptracker.dev/errors/business-rule"));
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setTitle("Validation Error");
        detail.setProperty("fieldErrors", errors);
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
```

핵심 변경 포인트:
- `import jakarta.persistence.EntityNotFoundException` 삭제. **import 자체를 빼면** 같은 패키지(`com.shoptracker.shared.exception`)의 `EntityNotFoundException`이 자동으로 사용됨.
- 클래스에 `@RestControllerAdvice` 추가.

---

### B-2. `application.yml` 들여쓰기 오류 — `management:`/`springdoc:`가 `spring:` 하위에 잘못 들어가 있음

**파일**: `src/main/resources/application.yml:26-51`

**현재 (잘못됨)**
```yaml
spring:
  application:
    name: shoptracker

  datasource: ...
  jpa: ...
  flyway: ...
  threads:
    virtual:
      enabled: true

  management:           # ← spring 하위 (틀림)
    endpoints:
      web:
        exposure:
          include: health, info, metrics, prometheus
    tracing:
      sampling:
        probability: 1.0
    otlp:
      tracing:
        endpoint: http://localhost:4318/v1/traces
      metrics:
        endpoint: http://localhost:4318/v1/metrics
    observations:
      key-values:
        application: shoptracker

  springdoc:            # ← spring 하위 (틀림)
    api-docs:
      path: /api-docs
    swagger-ui:
      path: /swagger-ui.html
      tags-sorter: alpha
      operations-sorter: method

server:
  port: 8080

logging: ...
```

**문제**
Spring Boot의 `management.*`와 `springdoc.*`는 **최상위 키**다. 현재처럼 `spring:` 하위에 두면 `spring.management.*`/`spring.springdoc.*`로 인식되어 **모든 설정이 무시**된다:
- 액추에이터 노출 설정 무시 → `/actuator/prometheus` 안 열림
- 트레이싱 샘플링/OTLP 엔드포인트 무시
- Swagger UI 경로 설정 무시 (springdoc 기본 경로로만 노출됨)

**수정 (전체 교체)**
```yaml
spring:
  application:
    name: shoptracker

  datasource:
    url: jdbc:postgresql://localhost:5432/shoptracker
    username: shoptracker
    password: shoptracker123

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration

  threads:
    virtual:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
    metrics:
      endpoint: http://localhost:4318/v1/metrics
  observations:
    key-values:
      application: shoptracker

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method

server:
  port: 8080

logging:
  level:
    com.shoptracker: DEBUG
    org.springframework.modulith: DEBUG
```

핵심: `management:` 와 `springdoc:` 의 들여쓰기를 한 단계 빼서 최상위 키로 끌어올린다.

---

### B-3. `OrderCommandService` 트랜잭션 import 잘못됨

**파일**: `src/main/java/com/shoptracker/orders/application/service/OrderCommandService.java:10`

**현재**
```java
import jakarta.transaction.Transactional;   // JTA용 (Jakarta EE)
```

**문제**
- 다른 모든 서비스(`PaymentCommandService`, `OrderQueryService`, `SubscriptionCommandService`, `SubscriptionQueryService`, `ShipmentCommandService`)는 `org.springframework.transaction.annotation.Transactional` 사용.
- `jakarta.transaction.Transactional`도 Spring이 인식해 동작은 하지만, `readOnly`, `propagation`, `isolation` 같은 Spring 전용 속성을 쓸 수 없고 일관성이 깨진다.

**수정**
A-2의 전체 교체 코드에 이미 반영. 단독으로 고친다면 다음 한 줄만:
```java
// 변경 전
import jakarta.transaction.Transactional;
// 변경 후
import org.springframework.transaction.annotation.Transactional;
```

---

## C. 정리/일관성

### C-1. `OrderResponse` 빈 record

**파일**: `src/main/java/com/shoptracker/orders/adapter/inbound/web/OrderResponse.java`

**현재**
```java
public record OrderResponse() {
}
```

**문제**
- 필드 전무. 현재 컨트롤러는 `OrderSummary`만 반환하므로 사용처도 없음.
- 다른 모듈은 `Response` DTO를 적극 사용 (`SubscriptionResponse`, `PaymentResponse`, `ShipmentResponse`, `TrackingResponse`).

**수정 옵션 A: 삭제** (단순)
사용처가 없으므로 파일 자체를 삭제하는 게 가장 깔끔.

**수정 옵션 B: 채우기** (다른 모듈과 일관)
상세 단건 조회용 응답이 필요하면:
```java
package com.shoptracker.orders.adapter.inbound.web;

import com.shoptracker.orders.domain.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerName,
        String status,
        BigDecimal totalAmount,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        List<OrderItemView> items,
        Instant createdAt
) {
    public record OrderItemView(String productName, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId().value(),
                order.getCustomerName(),
                order.getStatus().name().toLowerCase(),
                order.getTotalAmount().amount(),
                order.getShippingFee().amount(),
                order.getDiscountAmount().amount(),
                order.getFinalAmount().amount(),
                order.getItems().stream()
                        .map(i -> new OrderItemView(i.productName(), i.quantity(), i.unitPrice().amount()))
                        .toList(),
                order.getCreatedAt()
        );
    }
}
```
권장은 **옵션 A(삭제)**. PHASE5의 CQRS 의도상 조회는 `OrderSummary` 하나로 통일.

---

### C-2. git status의 deleted 파일 처리

**상태**
```
D  src/main/java/com/shoptracker/subscription/adapter/out/persistence/SubscriptionJpaEntity.java
```

**문제 아님**
- 옛 경로(`out/persistence`)에서 새 경로(`outbound/persistence`)로 이전한 흔적.
- 새 경로에는 정상 파일이 있음 (`outbound/persistence/SubscriptionJpaEntity.java`).
- 이미 staged 상태(`D ` with leading space) → 그대로 다음 커밋에 포함시키면 됨.

---

## 수정 순서 권고

순서를 지키면 중간에 빌드/부팅이 깨지지 않는다.

| # | 항목 | 영향 | 작업량 |
|---|------|------|--------|
| 1 | A-3 `SubscriptionPersistenceAdapter` `@Repository` 추가 | 부팅 차단 해소 | 10초 |
| 2 | A-1 `CreateOrderRequest` 필드 채우기 | 컴파일 차단 해소 | 1분 |
| 3 | A-2 `OrderCommandService.cancelOrder()` 추가 + `CancelOrderUseCase` 생성 | 컴파일 차단 해소 | 5분 |
| 4 | B-3 `OrderCommandService` Transactional import 교체 | 일관성 (3번에 포함됨) | — |
| 5 | B-1 `GlobalExceptionHandler` `@RestControllerAdvice` + import 교체 | 에러 응답 표준화 복구 | 1분 |
| 6 | B-2 `application.yml` `management:`/`springdoc:` 최상위로 | 액추에이터/Swagger 동작 | 1분 |
| 7 | C-1 `OrderResponse` 삭제 또는 채우기 | 정리 | 30초 |

---

## 검증 명령

수정 후 아래 명령으로 동작 확인:

```bash
# 1. 컴파일
./gradlew compileJava

# 2. 단위 테스트
./gradlew test --tests "com.shoptracker.unit.*"

# 3. 모듈 경계 검증
./gradlew test --tests "com.shoptracker.ModuleStructureTest"

# 4. 통합 테스트 (Testcontainers)
./gradlew test --tests "com.shoptracker.integration.*"

# 5. 부팅 후 엔드포인트 확인
docker compose up -d
./gradlew bootRun &
sleep 20

# 액추에이터 — management.* 가 최상위로 가야 동작
curl -s http://localhost:8080/actuator/health | jq .
curl -s http://localhost:8080/actuator/prometheus | head -20

# Swagger — springdoc.* 가 최상위로 가야 커스텀 경로 동작
curl -sI http://localhost:8080/swagger-ui.html

# 주문 취소 — A-2 가 적용되어야 동작
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Name: 홍길동' \
  -d '{"customerName":"홍길동","items":[{"productName":"테스트","quantity":1,"unitPrice":1000}]}' \
  | jq -r .id)
curl -s -X POST "http://localhost:8080/api/v1/orders/${ORDER_ID}/cancel" -i

# NotFound 응답 — B-1 이 적용되어야 ProblemDetail JSON
curl -s -i http://localhost:8080/api/v1/orders/00000000-0000-0000-0000-000000000000
# → 404 + application/problem+json 가 떠야 함
```
