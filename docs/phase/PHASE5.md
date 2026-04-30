# Phase 5: CQRS 고도화 + Observability

> **목표**: Command/Query 분리 심화 + OpenTelemetry 관찰성 + API 문서화
>
> **선행**: Phase 4 완료 (전체 Saga + Tracking 동작)
>
> **예상 소요**: 2일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | Command/Query 서비스가 명확히 분리 | CommandService: write + event, QueryService: read-only |
| 2 | ReadModel(DTO Projection)로 조회 최적화 | 목록 API가 Aggregate 전체가 아닌 요약만 반환 |
| 3 | 페이지네이션이 Spring Data Pageable로 간단 | `?page=0&size=10&sort=createdAt,desc` |
| 4 | OpenTelemetry로 전 구간 추적 | 단일 의존성 추가로 자동 계측 |
| 5 | @Observed로 커스텀 메트릭 | 결제 처리 시간, 할인 적용률 등 |
| 6 | Swagger/OpenAPI 문서 자동 생성 | `/swagger-ui.html` 접속 |

---

## Step 1: CQRS 서비스 분리 패턴

### 1.1 Command Service vs Query Service

```
┌─────────────────────────────────────────────────────────┐
│                    Controller                            │
│    POST /orders  →  createOrder(command)                 │
│    GET  /orders  →  listOrders(query)                    │
│    GET  /orders/{id}  →  getOrder(query)                 │
└─────────┬────────────────────────────┬──────────────────┘
          │                            │
          ▼                            ▼
┌──────────────────┐        ┌──────────────────┐
│ OrderCommandService│       │ OrderQueryService │
│                    │       │                   │
│ - OrderRepository  │       │ - OrderRepository │
│ - EventPublisher   │       │   (read-only)     │
│                    │       │ - NO EventPublisher│
│ @Transactional     │       │ @Transactional    │
│                    │       │   (readOnly=true)  │
└──────────────────┘        └──────────────────┘
```

### 1.2 Query 전용 ReadModel (DTO Projection)

```java
// orders/application/query/OrderSummary.java
package com.shoptracker.orders.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ★ ReadModel: 조회 전용 DTO.
 *   Aggregate 전체를 반환하지 않고, 필요한 필드만 포함.
 *   Spring Data JPA의 Projection 또는 직접 매핑으로 구현.
 */
public record OrderSummary(
    UUID id,
    String customerName,
    String status,
    BigDecimal totalAmount,
    BigDecimal shippingFee,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    int itemCount,
    Instant createdAt
) {}
```

```java
// orders/application/query/ListOrdersQuery.java
package com.shoptracker.orders.application.query;

public record ListOrdersQuery(
    String customerName,    // 선택: 고객별 필터
    String status,          // 선택: 상태 필터
    int page,
    int size,
    String sortBy,          // "createdAt", "totalAmount"
    String sortDir          // "asc", "desc"
) {
    public ListOrdersQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
        if (sortBy == null) sortBy = "createdAt";
        if (sortDir == null) sortDir = "desc";
    }
}
```

### 1.3 Query Service

```java
// orders/application/service/OrderQueryService.java
@Service
@Transactional(readOnly = true)  // ★ 읽기 전용 트랜잭션
public class OrderQueryService {
    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderSummary getOrder(UUID orderId) {
        return orderRepository.findById(new OrderId(orderId))
            .map(this::toSummary)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public Page<OrderSummary> listOrders(ListOrdersQuery query) {
        Pageable pageable = PageRequest.of(
            query.page(), query.size(),
            Sort.by(Sort.Direction.fromString(query.sortDir()), query.sortBy())
        );

        Page<Order> orders;
        if (query.customerName() != null) {
            orders = orderRepository.findAllByCustomerName(query.customerName(), pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orders.map(this::toSummary);
    }

    private OrderSummary toSummary(Order order) {
        return new OrderSummary(
            order.getId().value(),
            order.getCustomerName(),
            order.getStatus().name().toLowerCase(),
            order.getTotalAmount().amount(),
            order.getShippingFee().amount(),
            order.getDiscountAmount().amount(),
            order.getFinalAmount().amount(),
            order.getItems().size(),
            order.getCreatedAt()
        );
    }
}
```

### 1.4 Controller — 페이지네이션 지원

```java
// orders/adapter/in/web/OrderController.java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    // Command
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-Customer-Name") String customerName) {
        UUID id = commandService.createOrder(customerName, request.items());
        return Map.of("id", id);
    }

    // Query
    @GetMapping
    public Page<OrderSummary> list(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return queryService.listOrders(new ListOrdersQuery(
            customerName, status, page, size, sort, dir));
    }

    @GetMapping("/{id}")
    public OrderSummary getById(@PathVariable UUID id) {
        return queryService.getOrder(id);
    }

    // Command
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) {
        commandService.cancelOrder(id);
    }
}
```

---

## Step 2: OpenTelemetry Observability

### 2.1 의존성 추가

```groovy
// build.gradle 에 추가
dependencies {
    // ★ Spring Boot 4: 단일 의존성으로 전체 관찰성 (traces + metrics + logs)
    implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
}
```

### 2.2 application.yml 설정

```yaml
# src/main/resources/application.yml 에 추가
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  tracing:
    sampling:
      probability: 1.0  # 개발: 100% 샘플링

  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces   # OTLP collector (선택)
    metrics:
      endpoint: http://localhost:4318/v1/metrics

  observations:
    key-values:
      application: shoptracker  # 모든 메트릭에 태그 추가
```

### 2.3 @Observed 어노테이션으로 커스텀 계측

```java
// payments/application/service/PaymentCommandService.java
@Service
@Transactional
public class PaymentCommandService implements ProcessPaymentUseCase {

    /**
     * ★ @Observed: 이 메서드 호출이 자동으로
     *   - Trace span 생성
     *   - Timer metric 기록 (처리 시간)
     *   - Counter metric (호출 횟수)
     */
    @Override
    @Observed(name = "payment.process",
              contextualName = "process-payment",
              lowCardinalityKeyValues = {"module", "payments"})
    public UUID processPayment(ProcessPaymentCommand command) {
        Money originalAmount = new Money(command.totalAmount());

        // ★ discountPolicy는 DI가 구독 등급별로 주입한 것(Phase 2 참고).
        DiscountResult discount = discountPolicy.calculateDiscount(originalAmount);

        PaymentMethod method = PaymentMethod.valueOf(command.method().toUpperCase());
        Payment payment = Payment.create(command.orderId(), originalAmount, discount, method);

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

```java
// 수동 Observation 예시 (더 세밀한 제어)
@Service
public class DetailedPaymentService {
    private final ObservationRegistry registry;

    public void processWithObservation(ProcessPaymentCommand command) {
        Observation observation = Observation.createNotStarted("payment.detailed", registry)
            .lowCardinalityKeyValue("method", command.method())
            .highCardinalityKeyValue("orderId", command.orderId().toString());

        observation.observe(() -> {
            // 결제 처리 로직
        });
    }
}
```

### 2.4 Health Check 강화

```java
// shared/config/HealthConfig.java
@Configuration
public class HealthConfig {

    @Bean
    public HealthIndicator eventBusHealth() {
        return () -> Health.up()
            .withDetail("type", "spring-modulith-events")
            .build();
    }
}
```

---

## Step 3: 에러 핸들링 표준화

```java
// shared/exception/GlobalExceptionHandler.java 확장
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

---

## Step 4: Swagger / OpenAPI 문서

### 4.1 의존성 추가

```groovy
// build.gradle
dependencies {
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0'
}
```

### 4.2 설정

```yaml
# application.yml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: method
```

### 4.3 Controller에 API 문서 어노테이션

```java
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "주문 관리 API")
public class OrderController {

    @Operation(summary = "주문 생성",
               description = "새 주문을 생성합니다. X-Customer-Name 헤더로 구독 상태가 자동 반영됩니다.")
    @ApiResponse(responseCode = "201", description = "주문 생성 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-Customer-Name") String customerName) {
        UUID id = commandService.createOrder(customerName, request.items());
        return Map.of("id", id);
    }
}
```

---

## 체감 체크포인트

```bash
# 1. 페이지네이션
curl "http://localhost:8080/api/v1/orders?page=0&size=5&sort=createdAt&dir=desc"

# 2. Swagger UI 접속
open http://localhost:8080/swagger-ui.html

# 3. Actuator 메트릭
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/payment.process

# 4. CQRS 분리 확인: QueryService에 EventPublisher 의존 없음
grep -r "EventPublisher" src/main/java/com/shoptracker/orders/application/service/OrderQueryService.java
# → 0건
```

---

## 다음 Phase 예고

**Phase 6**: Docker 프로덕션 배포. Multi-stage build, 환경별 설정, Virtual Threads, GraalVM 네이티브(선택).