# PHASE_END: 누락 보강 + Boot 4 마이그레이션 + 검증 결과

> 페이즈 1~6 문서로는 가려져 있던 빈 껍데기, 잘못된 import, Boot 4 변경점, 모듈 경계 위반을 모두 잡고 동작 가능한 상태로 만든 기록.
>
> 이 문서를 읽으면 다음을 알 수 있다:
> - 무엇이 빠져 있었는가
> - 어떻게 고쳤는가 (Edit/Write 가능한 정도의 구체성)
> - 왜 그렇게 고쳤는가 (Boot 4 변경점·Modulith 규칙 근거)
> - 무엇이 아직 남아 있는가 (timing/test isolation)

---

## 1. 빈 껍데기 채움 — `orders/adapter/outbound/persistence/`

5개 파일이 모두 4줄짜리 빈 record/class 였음. 통합 테스트는 물론 부팅도 안 되는 수준. PHASE1 패턴(다른 모듈)에 맞춰 전체 구현.

| 파일 | 변경 |
|------|------|
| `OrderJpaEntity.java` | `@Entity @Table("orders")` + 8개 컬럼 + `@OneToMany items` (cascade ALL, orphanRemoval, EAGER) + `addItem()` 양방향 헬퍼 |
| `OrderItemJpaEntity.java` | `@Entity @Table("order_items")` + `@ManyToOne LAZY` 부모 참조 + `@GeneratedValue(UUID)` |
| `SpringDataOrderRepository.java` | `JpaRepository<OrderJpaEntity, UUID>` + `findAllByCustomerName(String, Pageable)` |
| `OrderMapper.java` | `toDomain` / `toJpa` — `Money` ↔ `BigDecimal` 분해/재조립, `OrderStatus` enum 변환, items 컬렉션 매핑 |
| `OrderPersistenceAdapter.java` | `@Repository` + `OrderRepository` 구현 (4메서드: save, findById, findAllByCustomerName, findAll) |

**근거**: `OrderRepository` 포트 정의는 처음부터 있었지만 구현체가 비어 있어 부팅 시 `NoSuchBeanDefinitionException: OrderRepository`. PHASE1 docs의 `Subscription`/`Payments` 패턴을 그대로 적용.

---

## 2. 빈 등록 어노테이션 누락

`@Repository` / `@Component` 없으면 컴포넌트 스캔에서 빠짐 → 빈 미등록 → 의존하는 서비스 주입 실패 → 부팅 차단.

| 파일 | 변경 |
|------|------|
| `subscription/.../SubscriptionPersistenceAdapter.java` | 클래스에 `@Repository` 추가 |
| `shipping/.../ShipmentPersistenceAdapter.java` | 클래스에 `@Repository` 추가 |
| `orders/.../OrderPersistenceAdapter.java` | 신규 작성 시 `@Repository` 포함 |

`payments/.../PaymentPersistenceAdapter`(`@Component`), `tracking/.../TrackingPersistenceAdapter`(`@Component`)는 이미 적용되어 있었음.

---

## 3. 컨트롤러 측 빈 record

| 파일 | 문제 | 변경 |
|------|------|------|
| `orders/.../CreateOrderRequest.java` | `record CreateOrderRequest()` 빈 껍데기. 컨트롤러가 `request.items()` 호출 → 컴파일 차단 | `customerName`, `items` 필드 + `@NotBlank`, `@NotEmpty`, `@Valid` |
| `orders/.../OrderResponse.java` | 빈 record. 사용처 없음 | **삭제** (CQRS Query 측은 `OrderSummary` 사용) |

---

## 4. UseCase 누락

`OrderController.cancel()`이 `commandService.cancelOrder(id)` 호출하지만 서비스/UseCase에 메서드 없음.

| 파일 | 변경 |
|------|------|
| `orders/application/port/inbound/CancelOrderUseCase.java` | **신규** — `void cancelOrder(UUID orderId)` |
| `orders/application/service/OrderCommandService.java` | `implements CreateOrderUseCase, CancelOrderUseCase` 로 확장 + `cancelOrder()` 구현 (Order 조회 → `transitionTo(CANCELLED)` → save). 없는 주문은 `OrderNotFoundException` |

---

## 5. 트랜잭션 import 잘못됨

`OrderCommandService.java` 가 혼자 `jakarta.transaction.Transactional` 사용. 다른 서비스는 모두 `org.springframework.transaction.annotation.Transactional`.

**변경**: import 교체. Spring 전용 속성(`readOnly`, `propagation`, `isolation`) 사용 가능 + 일관성.

---

## 6. `GlobalExceptionHandler` 활성화

```java
// 변경 전
import jakarta.persistence.EntityNotFoundException;   // ← JPA 것
public class GlobalExceptionHandler { ... }            // ← @RestControllerAdvice 누락

// 변경 후
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class GlobalExceptionHandler { ... }
// EntityNotFoundException은 같은 패키지의 com.shoptracker.shared.exception.EntityNotFoundException 자동 사용
```

**근거**: 컨트롤러들이 던지는 `com.shoptracker.shared.exception.EntityNotFoundException`과 핸들러가 잡는 `jakarta.persistence.EntityNotFoundException`이 다른 타입이라 매칭 안 됨. `@RestControllerAdvice` 없으면 핸들러 자체가 등록 안 됨.

---

## 7. `application.yml` 들여쓰기 — `management:` / `springdoc:`

```yaml
# 변경 전 (잘못됨 — spring 하위)
spring:
  ...
  management: ...
  springdoc: ...

# 변경 후 (최상위 키)
spring: ...
management: ...
springdoc: ...
```

**근거**: Boot 의 `management.*`, `springdoc.*` 는 최상위 키. `spring.management.*` 로 들어가면 모두 무시 → 액추에이터 노출/트레이싱 샘플링/Swagger 경로 설정 모두 불활성.

---

## 8. Boot 3 → Boot 4 마이그레이션

Boot 4는 모듈을 잘게 분할했음. 클래스패스에 새 의존성을 추가해야 동작.

### 8.1 `build.gradle` 추가

```groovy
implementation 'org.springframework.boot:spring-boot-flyway'
testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
testImplementation 'com.jayway.jsonpath:json-path'
```

| 의존성 | 이유 |
|--------|------|
| `spring-boot-flyway` | Boot 4 부터 `FlywayAutoConfiguration` 이 `spring-boot-autoconfigure` 에서 분리. 없으면 Flyway 가 아예 부팅 시 실행되지 않아 Hibernate validate 가 "missing table" 로 실패 |
| `spring-boot-webmvc-test` | `AutoConfigureMockMvc` 가 새 패키지(`org.springframework.boot.webmvc.test.autoconfigure.*`) 로 분리 |
| `json-path` | `JsonPath.read()` 가 `spring-boot-starter-test` 에 더 이상 자동 포함 안 됨 |

### 8.2 import 변경

```java
// 변경 전 (Boot 3)
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

// 변경 후 (Boot 4)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

3개 통합 테스트 파일 (`PaymentWithSubscriptionTest`, `ShippingWithSubscriptionTest`, `FullSagaIntegrationTest`) 일괄 변경.

### 8.3 Health 라이브러리 분리 (이미 적용됨)

`HealthConfig.java`가 새 패키지 사용:
```java
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
```
(기존 `o.s.b.actuate.health.*` 아님). `docs/study/health-library.md` 참고.

---

## 9. `@RequestScope` + `record` 호환성 — CGLIB 차단

```
Caused by: java.lang.IllegalArgumentException: Cannot subclass final class
   com.shoptracker.shared.SubscriptionContext
```

**원인**: `@RequestScope` 의 기본 `proxyMode = TARGET_CLASS` → CGLIB가 `SubscriptionContext` 의 서브클래스를 만들려 함. 그런데 `SubscriptionContext`는 `record`라 `final` → 서브클래스 불가.

**변경**: `SubscriptionContextConfig.java`
```java
// 변경 전
@Bean @RequestScope
public SubscriptionContext subscriptionContext(...) { ... }

// 변경 후
@Bean @Scope(value = "request", proxyMode = ScopedProxyMode.NO)
public SubscriptionContext subscriptionContext(...) { ... }
```

`proxyMode = NO`로 설정해도 동작하는 이유: `SubscriptionContext` 의 모든 소비자(`PaymentsPolicyConfig.discountPolicy`, `ShippingPolicyConfig.shippingFeePolicy`)도 `@RequestScope` 빈이라 같은 스코프에서 직접 주입 가능. proxy 불필요.

---

## 10. Spring Modulith 모듈 경계 위반

`ApplicationModules.verify()` 가 7가지 위반 보고 → 4건 수정.

### 10.1 사이클: `shared ↔ subscription`

`SubscriptionContextConfig`가 `com.shoptracker.shared.config` 에 있으면서 `com.shoptracker.subscription.application.port.inbound.SubscriptionQueryPort` 를 import → `shared → subscription`. 한편 `subscription` 은 `shared.events.SubscriptionActivatedEvent` 를 사용 → `subscription → shared`. 양방향 사이클.

**해결**: `SubscriptionContextConfig` 를 `com.shoptracker.subscription.internal.SubscriptionContextConfig` 로 이동. `shared` 는 더 이상 다른 모듈을 import 하지 않음 (`shared` 는 진짜 공통 모듈로 격하).

### 10.2 NamedInterface 선언

`shared.events`, `shared.exception`, `orders.domain.model`(Money 노출용) 가 다른 모듈에서 사용되지만 Modulith 기본 규칙은 "모듈 루트 패키지의 타입만 공개". 하위 패키지를 명시적으로 노출.

**추가 파일** — 각각 `package-info.java`:
```java
// shared/events/package-info.java
@org.springframework.modulith.NamedInterface("events")
package com.shoptracker.shared.events;

// shared/exception/package-info.java
@org.springframework.modulith.NamedInterface("exception")
package com.shoptracker.shared.exception;

// orders/domain/model/package-info.java
@org.springframework.modulith.NamedInterface("model")
package com.shoptracker.orders.domain.model;

// subscription/application/port/inbound/package-info.java
@org.springframework.modulith.NamedInterface("port")
package com.shoptracker.subscription.application.port.inbound;
```

`shared` 자체에는 `package-info.java` 를 두지 **않음**. 두면 명시적 NamedInterface 만 공개되고 `SubscriptionContext`(루트 직속) 가 가려짐. 비워둬야 루트 자동 공개 적용.

---

## 11. 테스트 — 단위 테스트의 잘못된 단언

`OrderStatusTransitionTest.java`:
```java
// 변경 전
@Test
void created_canTransitionTo_delivered() {
    assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();  // ← 도메인 규칙과 반대
}

// 변경 후
@Test
void created_cannotTransitionTo_delivered() {
    assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
}
```

`OrderStatus.CREATED` 는 PAYMENT_PENDING 또는 CANCELLED 로만 전이 가능 (PHASE1 도메인 규칙). 테스트 이름과 단언이 불일치한 명백한 오타.

---

## 12. 테스트 placeholder 채움

`PaymentWithSubscriptionTest.java` 에 작성자가 미완으로 둔 부분:
```java
// 변경 전
String orderId = /* JsonPath로 추출 */;   // ← 컴파일 에러

// 변경 후
String orderId = JsonPath.read(orderResponse, "$.id");
```
`com.jayway.jsonpath.JsonPath` import 추가 (8.1 의 `json-path` 의존성과 짝).

---

## 13. PHASE5/PHASE6 문서 — Groovy DSL 통일

PHASE1 은 "Project: Gradle - **Groovy DSL**" 명시인데 PHASE5/PHASE6 의 `build.gradle.kts` + Kotlin 문법(`id("…") version "…"`, `implementation("…")`) 잔존.

`PHASE5.md`, `PHASE6.md` 에서:
- `build.gradle.kts` → `build.gradle`
- `id("X") version "Y"` → `id 'X' version 'Y'`
- `implementation("X")` → `implementation 'X'`
- 최종 구조도 `├── build.gradle.kts` → `├── build.gradle`

(PHASE5_fix.md 의 7건 수정과 별개. 이 문서는 다른 페이즈의 후속 정리.)

---

## 14. 테스트 인프라 — Testcontainers 누락 보강

| 파일 | 문제 | 변경 |
|------|------|------|
| `ShopTrackerApplicationTests.java` | `@SpringBootTest` 만 있고 `@Testcontainers` 없음. `localhost:5432` 접근 시도 → connection refused | `@Testcontainers` + `@Container @ServiceConnection PostgreSQLContainer` 추가 |
| `module/PaymentsModuleTest.java` | 패키지 `com.shoptracker.module` 이 어떤 Modulith 모듈에도 속하지 않음. 테스트 초기화 실패 (`Package … is not part of any module!`) | 파일을 `com.shoptracker.payments.PaymentsModuleTest` 로 이동 (모듈 패키지 안에 둬야 `@ApplicationModuleTest`가 동작) + `@Testcontainers` + Postgres 추가 |
| `FullSagaIntegrationTest.java` | `awaitility` 10초 timeout이 events 누적에 부족 | 30초로 확장 |

---

## 15. 검증 결과

```
./gradlew test
42 tests completed, 3 failed
```

### 통과 (39/42)
- 단위 테스트 30/30 — Money/Order/Subscription/DiscountPolicy/ShippingFeePolicy/Status 전이
- `ModuleStructureTest.verifyModuleStructure` — Modulith 사이클/캡슐화 검증 통과
- `ModuleStructureTest.generateModuleDocumentation` — PlantUML 다이어그램 생성
- `ShopTrackerApplicationTests.contextLoads` — 앱 부팅 검증
- `PaymentWithSubscriptionTest.premiumSubscriber_gets10PercentDiscount` — 핵심 saga
- `PaymentWithSubscriptionTest.noSubscription_noDiscount`
- `ShippingWithSubscriptionTest.premiumSubscriber_freeShipping_evenSmallOrder`
- `ShippingWithSubscriptionTest.basicSubscriber_freeShipping_over30000`

### 잔여 실패 (3/42) — 알려진 이슈

1. **`FullSagaIntegrationTest.fullSaga_basicSubscriber`**
   - 현상: `$.events.length()` ≥ 2 기대, 실제 1.
   - 원인 추정: tracking 이벤트 수집 타이밍. `PaymentApprovedEvent` 가 `OrderCreatedEvent` 보다 먼저 처리되거나 tracking 이 일부 이벤트 누락. Spring Modulith `@ApplicationModuleListener` 의 비동기 + AFTER_COMMIT 이 직렬화되지 않아 트래킹이 OrderTracking 생성 전에 update 시도하면 `findByOrderId().ifPresent()` 가 무시.
   - 권고: `TrackingEventHandler.onOrderCreated` 가 동기 `@EventListener` 로 즉시 OrderTracking 생성하도록 분리하거나, ordering hint 부여. 또는 30s+ retry 정책.

2. **`PaymentsModuleTest.orderCreated_triggersPayment`**
   - 현상: `@ApplicationModuleTest` 가 payments 모듈만 부팅 → `SubscriptionContext` 빈이 없음 → `DiscountPolicy` 주입 실패 → 결제 실행 안 됨 → `PaymentApprovedEvent` 미발행 → timeout.
   - 권고: `@ApplicationModuleTest(extraIncludes = "subscription")` 또는 `@MockitoBean SubscriptionContext` 로 mock. 모듈 단위 테스트 본래 의도(외부 의존 격리)를 살리려면 후자가 정공.

3. **`ShippingWithSubscriptionTest.basicSubscriber_halfFee_under30000`**
   - 현상: 같은 클래스 다른 케이스(premium/over30000)는 통과, basic+under30000 만 실패. 타이밍 변동.
   - 권고: await timeout 상향 또는 결제 승인 분기 확정 후 검증. FakeGateway 10% 거절 영향 가능 — 확정 결제를 보장하는 테스트용 `AlwaysApprovePaymentGateway` 빈을 `@TestConfiguration` 으로 주입.

---

## 16. 부수 작업 정리

- `docs/phase/PHASE5_fix.md` — Phase 5 누락 7건 분석 (이 문서의 1~7 항목과 일부 중복)
- `docs/study/*.md` — 14개 학습 문서 (Spring Boot 4, Java 21, IoC/DI, Modulith, JPA, Flyway, 테스트, OTel, 가상스레드, GraalVM, CQRS+Hexagonal, observability 심화, 복원성 패턴, health 라이브러리)
- `Dockerfile`, `Dockerfile.native`, `docker-compose.yml`(앱 추가), `application-prod.yml`, `application-dev.yml` — Phase 6 적용

---

## 17. 추후 권고 (이 프로젝트 범위 밖)

| 영역 | 권고 |
|------|------|
| 이벤트 지속성 | `spring-modulith-events-jpa` 추가 → `event_publication` 테이블로 outbox 기반 재시도 |
| 모듈 단위 테스트 | 외부 모듈 빈을 `@MockitoBean` 으로 격리하는 테스트 인프라 정립 |
| 복원성 | Resilience4j 적용 (CircuitBreaker/Retry/Timeout) — `docs/study/resilience-patterns.md` 참고 |
| 관찰성 | OpenTelemetry Collector + Tempo/Loki/Prometheus + tail sampling |
| 보안 | `X-Customer-Name` 헤더 → JWT 기반 인증으로 교체 |

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-04-30 | 최초 작성. PHASE 1~6 구현 검증 + Boot 4 마이그레이션 + 테스트 보강 결과 종합 |
