# Phase 1: 뼈대 + Subscription + Orders 모듈

> **목표**: Hexagonal 구조가 잡힌 Spring Boot 프로젝트에서 DI와 기본 CRUD가 돌아가는 것 확인
>
> **예상 소요**: 3~4일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | 도메인에 Spring 의존성이 없다 | `grep -r "import org.springframework" orders/domain/` → 0건 |
| 2 | 단위 테스트가 DB 없이 돌아간다 | `./gradlew test --tests "*.unit.*"` → 0.2초 이내 |
| 3 | Spring Modulith가 모듈 경계를 검증한다 | `ApplicationModules.of(...).verify()` 통과 |
| 4 | Flyway가 스키마를 자동 관리한다 | 앱 시작 시 테이블 자동 생성 |
| 5 | Value Object(record)가 불변이다 | `Money a = new Money(1000); a.add(...)` → 새 객체 반환 |

---

## Step 1: 프로젝트 셋업

### 1.1 Spring Initializr로 프로젝트 생성

> https://start.spring.io 또는 IntelliJ에서 생성

```
Project: Gradle - Kotlin DSL
Language: Java
Spring Boot: 4.0.x
Java: 21
Group: com.shoptracker
Artifact: shoptracker
```

### 1.2 build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.shoptracker"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-events-api")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Lombok (선택 — record 쓰면 많이 줄어듦)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.0")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 1.3 Docker Compose (PostgreSQL)

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: shoptracker
      POSTGRES_USER: shoptracker
      POSTGRES_PASSWORD: shoptracker123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### 1.4 application.yml

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: shoptracker

  datasource:
    url: jdbc:postgresql://localhost:5432/shoptracker
    username: shoptracker
    password: shoptracker123

  jpa:
    hibernate:
      ddl-auto: validate  # Flyway가 관리하므로 validate만
    properties:
      hibernate:
        format_sql: true
    open-in-view: false    # ★ 실무 권장: OSIV 끔

  flyway:
    enabled: true
    locations: classpath:db/migration

  threads:
    virtual:
      enabled: true        # ★ Virtual Threads 활성화

server:
  port: 8080

logging:
  level:
    com.shoptracker: DEBUG
    org.springframework.modulith: DEBUG
```

### 1.5 application-dev.yml

```yaml
# src/main/resources/application-dev.yml
spring:
  jpa:
    show-sql: true
  flyway:
    clean-disabled: false  # 개발 환경에서만 clean 허용
```

---

## Step 2: shared 패키지 — 공유 계약

### 2.1 SubscriptionContext (모듈 간 공유 DTO)

```java
// src/main/java/com/shoptracker/shared/SubscriptionContext.java
package com.shoptracker.shared;

/**
 * 구독 상태를 요약한 불변 DTO.
 * Payments, Shipping 등 다른 모듈은 이것만 알면 된다.
 * Subscription 엔티티 자체는 모른다.
 *
 * ★ FastAPI의 @dataclass(frozen=True)에 대응 — Java record는 자동으로 불변.
 */
public record SubscriptionContext(
    String customerName,
    String tier,          // "none", "basic", "premium"
    boolean isActive
) {
    public static SubscriptionContext none(String customerName) {
        return new SubscriptionContext(customerName, "none", false);
    }
}
```

### 2.2 이벤트 정의 (Phase 1에서는 2개만)

```java
// src/main/java/com/shoptracker/shared/events/OrderCreatedEvent.java
package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문 생성 이벤트.
 * ★ record로 정의 → 불변 + equals/hashCode/toString 자동 생성
 * ★ FastAPI의 @dataclass(frozen=True) OrderCreatedEvent에 대응
 */
public record OrderCreatedEvent(
    UUID orderId,
    String customerName,
    BigDecimal totalAmount,
    int itemsCount,
    Instant timestamp
) {}
```

```java
// src/main/java/com/shoptracker/shared/events/SubscriptionActivatedEvent.java
package com.shoptracker.shared.events;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionActivatedEvent(
    UUID subscriptionId,
    String customerName,
    String tier,
    Instant expiresAt,
    Instant timestamp
) {}
```

### 2.3 GlobalExceptionHandler (기본)

```java
// src/main/java/com/shoptracker/shared/exception/GlobalExceptionHandler.java
package com.shoptracker.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setTitle("Resource Not Found");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        detail.setTitle("Business Rule Violation");
        detail.setProperty("timestamp", Instant.now());
        return detail;
    }
}
```

```java
// src/main/java/com/shoptracker/shared/exception/EntityNotFoundException.java
package com.shoptracker.shared.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}

// src/main/java/com/shoptracker/shared/exception/BusinessRuleException.java
package com.shoptracker.shared.exception;

public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
```

---

## Step 3: Subscription 모듈 — 전체 구현

### 3.1 도메인 레이어 (순수 Java)

```java
// src/main/java/com/shoptracker/subscription/domain/model/Subscription.java
package com.shoptracker.subscription.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Subscription Aggregate Root.
 * ★ 핵심: Spring, JPA import 없음. 순수 Java만.
 * ★ FastAPI의 Subscription 엔티티에 대응.
 */
public class Subscription {
    private final SubscriptionId id;
    private final String customerName;
    private final SubscriptionTier tier;
    private SubscriptionStatus status;
    private final Instant startedAt;
    private final Instant expiresAt;

    public Subscription(SubscriptionId id, String customerName, SubscriptionTier tier,
                        SubscriptionStatus status, Instant startedAt, Instant expiresAt) {
        this.id = id;
        this.customerName = customerName;
        this.tier = tier;
        this.status = status;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 팩토리 메서드: 30일짜리 구독 생성.
     */
    public static Subscription create(String customerName, SubscriptionTier tier) {
        return new Subscription(
            SubscriptionId.generate(),
            customerName,
            tier,
            SubscriptionStatus.ACTIVE,
            Instant.now(),
            Instant.now().plusSeconds(30L * 24 * 60 * 60) // 30일
        );
    }

    /**
     * ★ 도메인 비즈니스 규칙: 만료 전이고 ACTIVE 상태인지.
     * FastAPI의 is_active()와 동일.
     */
    public boolean isActive() {
        return this.status == SubscriptionStatus.ACTIVE
            && this.expiresAt.isAfter(Instant.now());
    }

    public void cancel() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot cancel subscription in status: " + this.status);
        }
        this.status = SubscriptionStatus.CANCELLED;
    }

    // Getters
    public SubscriptionId getId() { return id; }
    public String getCustomerName() { return customerName; }
    public SubscriptionTier getTier() { return tier; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
```

```java
// src/main/java/com/shoptracker/subscription/domain/model/SubscriptionId.java
package com.shoptracker.subscription.domain.model;

import java.util.UUID;

/**
 * ★ Value Object: ID를 타입 안전하게 감싸기.
 *    OrderId와 SubscriptionId를 실수로 바꿔 넣는 것 방지.
 */
public record SubscriptionId(UUID value) {
    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID());
    }
}
```

```java
// src/main/java/com/shoptracker/subscription/domain/model/SubscriptionTier.java
package com.shoptracker.subscription.domain.model;

public enum SubscriptionTier {
    NONE("none"),
    BASIC("basic"),       // 결제 5% 할인, 배송비 50% 할인
    PREMIUM("premium");   // 결제 10% 할인, 배송비 무료

    private final String value;
    SubscriptionTier(String value) { this.value = value; }
    public String getValue() { return value; }

    public static SubscriptionTier fromString(String value) {
        for (SubscriptionTier tier : values()) {
            if (tier.value.equalsIgnoreCase(value)) return tier;
        }
        throw new IllegalArgumentException("Unknown tier: " + value);
    }
}
```

```java
// src/main/java/com/shoptracker/subscription/domain/model/SubscriptionStatus.java
package com.shoptracker.subscription.domain.model;

public enum SubscriptionStatus {
    ACTIVE, EXPIRED, CANCELLED
}
```

```java
// src/main/java/com/shoptracker/subscription/domain/port/out/SubscriptionRepository.java
package com.shoptracker.subscription.domain.port.out;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import java.util.Optional;

/**
 * ★ Output Port (Interface).
 *   FastAPI의 SubscriptionRepositoryProtocol(Protocol)에 대응.
 *   도메인이 정의하고, 인프라가 구현한다 → 의존성 역전!
 */
public interface SubscriptionRepository {
    void save(Subscription subscription);
    Optional<Subscription> findById(SubscriptionId id);
    Optional<Subscription> findActiveByCustomer(String customerName);
}
```

### 3.2 Application 레이어

```java
// src/main/java/com/shoptracker/subscription/application/port/in/CreateSubscriptionUseCase.java
package com.shoptracker.subscription.application.port.in;

import java.util.UUID;

public interface CreateSubscriptionUseCase {
    UUID create(String customerName, String tier);
}
```

```java
// src/main/java/com/shoptracker/subscription/application/port/in/SubscriptionQueryPort.java
package com.shoptracker.subscription.application.port.in;

import com.shoptracker.subscription.domain.model.Subscription;
import java.util.Optional;
import java.util.UUID;

/**
 * ★ 다른 모듈(shared/config)이 구독 상태를 조회하기 위한 Input Port.
 *   SubscriptionContextConfig에서 이걸 주입받아 SubscriptionContext를 만든다.
 */
public interface SubscriptionQueryPort {
    Optional<Subscription> findActiveByCustomer(String customerName);
    Optional<Subscription> findById(UUID id);
}
```

```java
// src/main/java/com/shoptracker/subscription/application/service/SubscriptionCommandService.java
package com.shoptracker.subscription.application.service;

import com.shoptracker.shared.events.SubscriptionActivatedEvent;
import com.shoptracker.shared.exception.BusinessRuleException;
import com.shoptracker.subscription.application.port.in.CreateSubscriptionUseCase;
import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionTier;
import com.shoptracker.subscription.domain.port.out.SubscriptionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionCommandService implements CreateSubscriptionUseCase {
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionCommandService(SubscriptionRepository subscriptionRepository,
                                       ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID create(String customerName, String tier) {
        // 비즈니스 규칙: 이미 활성 구독이 있으면 중복 생성 불가
        subscriptionRepository.findActiveByCustomer(customerName)
            .filter(Subscription::isActive)
            .ifPresent(existing -> {
                throw new BusinessRuleException(
                    "Customer '" + customerName + "' already has an active subscription");
            });

        SubscriptionTier subscriptionTier = SubscriptionTier.fromString(tier);
        Subscription subscription = Subscription.create(customerName, subscriptionTier);
        subscriptionRepository.save(subscription);

        // ★ Spring ApplicationEvent 발행
        eventPublisher.publishEvent(new SubscriptionActivatedEvent(
            subscription.getId().value(),
            customerName,
            subscriptionTier.getValue(),
            subscription.getExpiresAt(),
            Instant.now()
        ));

        return subscription.getId().value();
    }
}
```

```java
// src/main/java/com/shoptracker/subscription/application/service/SubscriptionQueryService.java
package com.shoptracker.subscription.application.service;

import com.shoptracker.subscription.application.port.in.SubscriptionQueryPort;
import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.port.out.SubscriptionRepository;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubscriptionQueryService implements SubscriptionQueryPort {
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionQueryService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public Optional<Subscription> findActiveByCustomer(String customerName) {
        return subscriptionRepository.findActiveByCustomer(customerName);
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return subscriptionRepository.findById(new SubscriptionId(id));
    }
}
```

### 3.3 Adapter — Persistence (JPA)

```java
// src/main/java/com/shoptracker/subscription/adapter/out/persistence/SubscriptionJpaEntity.java
package com.shoptracker.subscription.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * ★ JPA Entity: @Entity, @Table 등 JPA 어노테이션은 여기만!
 *   도메인 Subscription 클래스에는 JPA import가 없다.
 *   FastAPI의 infrastructure/models.py (SQLAlchemy ORM 모델)에 대응.
 */
@Entity
@Table(name = "subscriptions")
public class SubscriptionJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "tier", nullable = false)
    private String tier;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected SubscriptionJpaEntity() {} // JPA용

    // Getters, Setters 생략 (Lombok @Data 또는 직접 작성)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
```

```java
// src/main/java/com/shoptracker/subscription/adapter/out/persistence/SpringDataSubscriptionRepository.java
package com.shoptracker.subscription.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

/**
 * ★ Spring Data JPA: 인터페이스만 정의하면 구현체 자동 생성.
 *   FastAPI의 SQLAlchemySubscriptionRepository에 대응하지만,
 *   Spring은 보일러플레이트가 훨씬 적다.
 */
public interface SpringDataSubscriptionRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    @Query("SELECT s FROM SubscriptionJpaEntity s " +
           "WHERE s.customerName = :customerName AND s.status = 'ACTIVE'")
    Optional<SubscriptionJpaEntity> findActiveByCustomerName(String customerName);
}
```

```java
// src/main/java/com/shoptracker/subscription/adapter/out/persistence/SubscriptionMapper.java
package com.shoptracker.subscription.adapter.out.persistence;

import com.shoptracker.subscription.domain.model.*;

/**
 * ★ 도메인 ↔ JPA 엔티티 변환.
 *   FastAPI의 infrastructure/mappers.py에 대응.
 *   도메인 객체가 JPA에 오염되지 않도록 이 계층에서 변환.
 */
public class SubscriptionMapper {

    public static Subscription toDomain(SubscriptionJpaEntity entity) {
        return new Subscription(
            new SubscriptionId(entity.getId()),
            entity.getCustomerName(),
            SubscriptionTier.fromString(entity.getTier()),
            SubscriptionStatus.valueOf(entity.getStatus()),
            entity.getStartedAt(),
            entity.getExpiresAt()
        );
    }

    public static SubscriptionJpaEntity toJpa(Subscription domain) {
        SubscriptionJpaEntity entity = new SubscriptionJpaEntity();
        entity.setId(domain.getId().value());
        entity.setCustomerName(domain.getCustomerName());
        entity.setTier(domain.getTier().getValue());
        entity.setStatus(domain.getStatus().name());
        entity.setStartedAt(domain.getStartedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        return entity;
    }
}
```

```java
// src/main/java/com/shoptracker/subscription/adapter/out/persistence/SubscriptionPersistenceAdapter.java
package com.shoptracker.subscription.adapter.out.persistence;

import com.shoptracker.subscription.domain.model.Subscription;
import com.shoptracker.subscription.domain.model.SubscriptionId;
import com.shoptracker.subscription.domain.port.out.SubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ★ Output Port 구현체.
 *   도메인의 SubscriptionRepository 인터페이스를 구현.
 *   내부에서 SpringDataSubscriptionRepository(JPA)를 사용.
 *   → 의존성 역전: 도메인이 인터페이스 정의, 인프라가 구현.
 */
@Repository
public class SubscriptionPersistenceAdapter implements SubscriptionRepository {
    private final SpringDataSubscriptionRepository jpaRepository;

    public SubscriptionPersistenceAdapter(SpringDataSubscriptionRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Subscription subscription) {
        jpaRepository.save(SubscriptionMapper.toJpa(subscription));
    }

    @Override
    public Optional<Subscription> findById(SubscriptionId id) {
        return jpaRepository.findById(id.value())
            .map(SubscriptionMapper::toDomain);
    }

    @Override
    public Optional<Subscription> findActiveByCustomer(String customerName) {
        return jpaRepository.findActiveByCustomerName(customerName)
            .map(SubscriptionMapper::toDomain);
    }
}
```

### 3.4 Adapter — Web (REST Controller)

```java
// src/main/java/com/shoptracker/subscription/adapter/in/web/CreateSubscriptionRequest.java
package com.shoptracker.subscription.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * ★ Request DTO (record).
 *   FastAPI의 Pydantic v2 schema에 대응.
 *   Bean Validation으로 요청 검증.
 */
public record CreateSubscriptionRequest(
    @NotBlank(message = "customerName is required")
    String customerName,

    @NotBlank(message = "tier is required")
    @Pattern(regexp = "basic|premium", message = "tier must be 'basic' or 'premium'")
    String tier
) {}
```

```java
// src/main/java/com/shoptracker/subscription/adapter/in/web/SubscriptionResponse.java
package com.shoptracker.subscription.adapter.in.web;

import com.shoptracker.subscription.domain.model.Subscription;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    String customerName,
    String tier,
    String status,
    boolean isActive,
    Instant startedAt,
    Instant expiresAt
) {
    public static SubscriptionResponse from(Subscription s) {
        return new SubscriptionResponse(
            s.getId().value(),
            s.getCustomerName(),
            s.getTier().getValue(),
            s.getStatus().name().toLowerCase(),
            s.isActive(),
            s.getStartedAt(),
            s.getExpiresAt()
        );
    }
}
```

```java
// src/main/java/com/shoptracker/subscription/adapter/in/web/SubscriptionController.java
package com.shoptracker.subscription.adapter.in.web;

import com.shoptracker.shared.exception.EntityNotFoundException;
import com.shoptracker.subscription.application.port.in.CreateSubscriptionUseCase;
import com.shoptracker.subscription.application.port.in.SubscriptionQueryPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final CreateSubscriptionUseCase createUseCase;
    private final SubscriptionQueryPort queryPort;

    public SubscriptionController(CreateSubscriptionUseCase createUseCase,
                                   SubscriptionQueryPort queryPort) {
        this.createUseCase = createUseCase;
        this.queryPort = queryPort;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        UUID id = createUseCase.create(request.customerName(), request.tier());
        return ResponseEntity
            .created(URI.create("/api/v1/subscriptions/" + id))
            .body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getById(@PathVariable UUID id) {
        return queryPort.findById(id)
            .map(SubscriptionResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "Subscription not found: " + id));
    }

    @GetMapping("/customer/{customerName}")
    public SubscriptionResponse getByCustomer(@PathVariable String customerName) {
        return queryPort.findActiveByCustomer(customerName)
            .map(SubscriptionResponse::from)
            .orElseThrow(() -> new EntityNotFoundException(
                "No active subscription for: " + customerName));
    }
}
```

### 3.5 SubscriptionContext 설정

```java
// src/main/java/com/shoptracker/shared/config/SubscriptionContextConfig.java
package com.shoptracker.shared.config;

import com.shoptracker.shared.SubscriptionContext;
import com.shoptracker.subscription.application.port.in.SubscriptionQueryPort;
import com.shoptracker.subscription.domain.model.Subscription;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class SubscriptionContextConfig {

    /**
     * ★ 핵심 학습 포인트:
     *   매 HTTP 요청마다 X-Customer-Name 헤더로 구독 상태를 조회.
     *   다른 모듈(Payments, Shipping)은 이 SubscriptionContext만 주입받으면 된다.
     *
     *   FastAPI의 Dishka SubscriptionProvider.subscription_context()에 대응.
     */
    @Bean
    @RequestScope
    public SubscriptionContext subscriptionContext(
            HttpServletRequest request,
            SubscriptionQueryPort subscriptionQueryPort) {

        String customerName = request.getHeader("X-Customer-Name");
        if (customerName == null || customerName.isBlank()) {
            return SubscriptionContext.none("guest");
        }

        return subscriptionQueryPort.findActiveByCustomer(customerName)
            .filter(Subscription::isActive)
            .map(sub -> new SubscriptionContext(
                customerName, sub.getTier().getValue(), true))
            .orElse(SubscriptionContext.none(customerName));
    }
}
```

---

## Step 4: Orders 모듈 — 도메인 레이어

### 4.1 Value Objects

```java
// src/main/java/com/shoptracker/orders/domain/model/Money.java
package com.shoptracker.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ★ Value Object (record).
 *   FastAPI의 Money 클래스에 대응.
 *   record이므로 자동으로 불변 + equals/hashCode 제공.
 */
public record Money(BigDecimal amount, String currency) {
    public static final Money ZERO = new Money(BigDecimal.ZERO, "KRW");

    public Money(BigDecimal amount) {
        this(amount, "KRW");
    }

    // BigDecimal을 가지고 있는데 int/long으로도 생성할 수 있게
    public Money(long amount) {
        this(BigDecimal.valueOf(amount), "KRW");
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money applyRate(BigDecimal rate) {
        return new Money(
            this.amount.multiply(rate).setScale(0, RoundingMode.FLOOR),
            this.currency
        );
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isNegativeOrZero() {
        return this.amount.compareTo(BigDecimal.ZERO) <= 0;
    }
}
```

```java
// src/main/java/com/shoptracker/orders/domain/model/OrderId.java
package com.shoptracker.orders.domain.model;

import java.util.UUID;

public record OrderId(UUID value) {
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }
}
```

```java
// src/main/java/com/shoptracker/orders/domain/model/OrderStatus.java
package com.shoptracker.orders.domain.model;

/**
 * ★ 상태 전이 규칙을 Enum 안에 캡슐화.
 *   FastAPI 버전과 동일한 비즈니스 규칙.
 */
public enum OrderStatus {
    CREATED, PAYMENT_PENDING, PAID, SHIPPING, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case CREATED -> target == PAYMENT_PENDING || target == CANCELLED;
            case PAYMENT_PENDING -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPING;
            case SHIPPING -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
```

### 4.2 Order 엔티티 (Aggregate Root)

```java
// src/main/java/com/shoptracker/orders/domain/model/Order.java
package com.shoptracker.orders.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * ★ Aggregate Root. 순수 Java, Spring/JPA 의존성 없음.
 *   FastAPI의 Order 엔티티에 대응.
 */
public class Order {
    private final OrderId id;
    private final String customerName;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final Money totalAmount;
    private Money shippingFee;
    private Money discountAmount;
    private Money finalAmount;
    private final Instant createdAt;

    // full constructor (Mapper, 테스트에서 사용)
    public Order(OrderId id, String customerName, List<OrderItem> items,
                 OrderStatus status, Money totalAmount, Money shippingFee,
                 Money discountAmount, Money finalAmount, Instant createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.items = List.copyOf(items); // 불변 복사
        this.status = status;
        this.totalAmount = totalAmount;
        this.shippingFee = shippingFee;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.createdAt = createdAt;
    }

    /**
     * ★ 팩토리 메서드: 비즈니스 규칙 검증 포함.
     *   "주문 항목은 최소 1개, 총 금액은 0원 초과"
     */
    public static Order create(String customerName, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least 1 item");
        }

        Money total = items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.ZERO, Money::add);

        if (total.isNegativeOrZero()) {
            throw new IllegalArgumentException("Order total must be positive");
        }

        return new Order(
            OrderId.generate(), customerName, items,
            OrderStatus.CREATED, total,
            Money.ZERO, Money.ZERO, total,
            Instant.now()
        );
    }

    /**
     * ★ 상태 전이: 유효하지 않은 전이는 예외 발생.
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void applyPricing(Money shippingFee, Money discountAmount) {
        this.shippingFee = shippingFee;
        this.discountAmount = discountAmount;
        this.finalAmount = totalAmount.add(shippingFee).subtract(discountAmount);
    }

    // Getters
    public OrderId getId() { return id; }
    public String getCustomerName() { return customerName; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getShippingFee() { return shippingFee; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public Instant getCreatedAt() { return createdAt; }
}
```

```java
// src/main/java/com/shoptracker/orders/domain/model/OrderItem.java
package com.shoptracker.orders.domain.model;

public record OrderItem(
    String productName,
    int quantity,
    Money unitPrice
) {
    public Money subtotal() {
        return new Money(unitPrice.amount().multiply(java.math.BigDecimal.valueOf(quantity)));
    }
}
```

### 4.3 Output Port + Exceptions

```java
// src/main/java/com/shoptracker/orders/domain/port/out/OrderRepository.java
package com.shoptracker.orders.domain.port.out;

import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    Page<Order> findAllByCustomerName(String customerName, Pageable pageable);
    Page<Order> findAll(Pageable pageable);
}
```

```java
// src/main/java/com/shoptracker/orders/domain/exception/OrderNotFoundException.java
package com.shoptracker.orders.domain.exception;

import com.shoptracker.shared.exception.EntityNotFoundException;
import java.util.UUID;

public class OrderNotFoundException extends EntityNotFoundException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
```

### 4.4 Orders Application + Adapter (간략)

> 패턴은 Subscription과 동일. UseCase 인터페이스 → Service 구현 → JPA Adapter → Controller.

```java
// orders/application/port/in/CreateOrderUseCase.java
public interface CreateOrderUseCase {
    UUID createOrder(String customerName, List<OrderItemRequest> items);
}

// orders/application/service/OrderCommandService.java
@Service
@Transactional
public class OrderCommandService implements CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ★ Phase 1에서는 이벤트를 발행만. 구독하는 모듈은 Phase 2에서 추가.
    @Override
    public UUID createOrder(String customerName, List<OrderItemRequest> items) {
        List<OrderItem> orderItems = items.stream()
            .map(i -> new OrderItem(i.productName(), i.quantity(),
                new Money(i.unitPrice())))
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
}
```

---

## Step 5: Flyway 마이그레이션

```sql
-- src/main/resources/db/migration/V1__create_subscriptions.sql
CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY,
    customer_name   VARCHAR(255) NOT NULL,
    tier            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_subscriptions_customer_status
    ON subscriptions (customer_name, status);
```

```sql
-- src/main/resources/db/migration/V2__create_orders.sql
CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_name   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    total_amount    DECIMAL(15,2) NOT NULL,
    shipping_fee    DECIMAL(15,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    final_amount    DECIMAL(15,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders(id),
    product_name  VARCHAR(255) NOT NULL,
    quantity      INT NOT NULL,
    unit_price    DECIMAL(15,2) NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_name);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_order_items_order ON order_items (order_id);
```

---

## Step 6: 테스트

### 6.1 단위 테스트 — DB 없이 순수 도메인

```java
// src/test/java/com/shoptracker/unit/MoneyTest.java
package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void addTwoMoneyValues() {
        Money a = new Money(1000);
        Money b = new Money(2000);
        assertThat(a.add(b)).isEqualTo(new Money(3000));
    }

    @Test
    void subtractMoney() {
        Money a = new Money(5000);
        Money b = new Money(3000);
        assertThat(a.subtract(b)).isEqualTo(new Money(2000));
    }

    @Test
    void applyDiscountRate() {
        Money amount = new Money(100000);
        Money discounted = amount.applyRate(new BigDecimal("0.10"));
        assertThat(discounted).isEqualTo(new Money(10000));
    }

    @Test
    void zeroMoneyIsNegativeOrZero() {
        assertThat(Money.ZERO.isNegativeOrZero()).isTrue();
    }

    @Test
    void moneyEqualityByValue() {
        // ★ record이므로 값이 같으면 equals == true
        Money a = new Money(new BigDecimal("1000"), "KRW");
        Money b = new Money(new BigDecimal("1000"), "KRW");
        assertThat(a).isEqualTo(b);
    }
}
```

```java
// src/test/java/com/shoptracker/unit/OrderStatusTransitionTest.java
package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderStatusTransitionTest {

    @Test
    void created_canTransitionTo_paymentPending() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
    }

    @Test
    void created_canTransitionTo_cancelled() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void created_cannotTransitionTo_delivered() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void paid_canTransitionTo_shipping() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPING)).isTrue();
    }

    @Test
    void paid_cannotTransitionTo_cancelled() {
        // ★ PAID 이후에는 취소 불가
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void delivered_cannotTransitionAnywhere() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(status)).isFalse();
        }
    }
}
```

```java
// src/test/java/com/shoptracker/unit/OrderTest.java
package com.shoptracker.unit;

import com.shoptracker.orders.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    void createOrder_calculatesTotal() {
        Order order = Order.create("홍길동", List.of(
            new OrderItem("노트북", 1, new Money(1000000)),
            new OrderItem("마우스", 2, new Money(25000))
        ));

        assertThat(order.getTotalAmount()).isEqualTo(new Money(1050000));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void createOrder_emptyItems_throws() {
        assertThatThrownBy(() -> Order.create("홍길동", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 1 item");
    }

    @Test
    void transitionStatus_validPath() {
        Order order = Order.create("테스트", List.of(
            new OrderItem("상품", 1, new Money(10000))));

        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    void transitionStatus_invalidPath_throws() {
        Order order = Order.create("테스트", List.of(
            new OrderItem("상품", 1, new Money(10000))));

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.DELIVERED))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

```java
// src/test/java/com/shoptracker/unit/SubscriptionTest.java
package com.shoptracker.unit;

import com.shoptracker.subscription.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.*;

class SubscriptionTest {

    @Test
    void activeSubscription_isActive() {
        Subscription sub = new Subscription(
            SubscriptionId.generate(), "홍길동", SubscriptionTier.PREMIUM,
            SubscriptionStatus.ACTIVE, Instant.now(),
            Instant.now().plus(15, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isTrue();
    }

    @Test
    void expiredSubscription_isNotActive() {
        Subscription sub = new Subscription(
            SubscriptionId.generate(), "홍길동", SubscriptionTier.PREMIUM,
            SubscriptionStatus.ACTIVE, Instant.now().minus(31, ChronoUnit.DAYS),
            Instant.now().minus(1, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isFalse();
    }

    @Test
    void cancelledSubscription_isNotActive() {
        Subscription sub = new Subscription(
            SubscriptionId.generate(), "홍길동", SubscriptionTier.BASIC,
            SubscriptionStatus.CANCELLED, Instant.now(),
            Instant.now().plus(15, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isFalse();
    }

    @Test
    void createSubscription_factoryMethod() {
        Subscription sub = Subscription.create("테스트", SubscriptionTier.BASIC);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.isActive()).isTrue();
    }
}
```

### 6.2 Spring Modulith 모듈 경계 검증 테스트

```java
// src/test/java/com/shoptracker/ModuleStructureTest.java
package com.shoptracker;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

    @Test
    void verifyModuleStructure() {
        // ★ 모듈 간 허용되지 않은 import가 있으면 이 테스트가 실패!
        ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
        modules.verify();
    }

    @Test
    void generateModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

---

## 체감 체크포인트 최종 확인

Phase 1을 완료했다면 다음을 확인하세요:

```bash
# 1. 도메인에 Spring 의존성이 없는지 확인
grep -r "import org.springframework" src/main/java/com/shoptracker/orders/domain/
grep -r "import org.springframework" src/main/java/com/shoptracker/subscription/domain/
grep -r "import jakarta.persistence" src/main/java/com/shoptracker/orders/domain/
# → 모두 0건이어야 함

# 2. 단위 테스트 실행 (DB 없이)
./gradlew test --tests "com.shoptracker.unit.*"
# → 전부 PASS, 0.5초 이내

# 3. Spring Modulith 모듈 경계 검증
./gradlew test --tests "com.shoptracker.ModuleStructureTest"
# → PASS

# 4. Docker Compose로 앱 실행
docker compose up -d
./gradlew bootRun

# 5. API 테스트
# 구독 생성
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"customerName": "홍길동", "tier": "premium"}'

# 구독 조회
curl http://localhost:8080/api/v1/subscriptions/customer/홍길동

# 주문 생성
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Name: 홍길동" \
  -d '{"customerName": "홍길동", "items": [{"productName": "노트북", "quantity": 1, "unitPrice": 1000000}]}'
```

---

## FastAPI 대비 Spring에서 달라진 점 (Phase 1)

| 관점 | FastAPI | Spring |
|------|---------|--------|
| DI 설정 | Dishka Provider 클래스 | @Configuration + @Bean 메서드 |
| Protocol (Port) | `typing.Protocol` | Java `interface` |
| 불변 DTO | `@dataclass(frozen=True)` | Java `record` |
| ORM Entity 분리 | `models.py` (SQLAlchemy Model) | `JpaEntity` + `Mapper` |
| Repository 자동생성 | 없음 (직접 구현) | Spring Data JPA (인터페이스만) |
| 마이그레이션 | Alembic | Flyway |
| Request Scope | Dishka `Scope.REQUEST` | `@RequestScope` |
| 모듈 경계 검증 | `grep` 수동 | `ApplicationModules.verify()` 자동 |

---

## 다음 Phase 예고

**Phase 2**: InMemoryEventBus 대신 Spring의 `ApplicationEventPublisher`를 사용하여
Orders → Payments 이벤트 통신을 구현하고, **구독 등급별 할인 정책이 DI로 주입**되는 것을 체감합니다.