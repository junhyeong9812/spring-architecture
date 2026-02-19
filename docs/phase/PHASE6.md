# Phase 6: 프로덕션 배포

> **목표**: Docker로 프로덕션 환경 구성 + Virtual Threads + 선택적 GraalVM 네이티브
>
> **선행**: Phase 5 완료 (CQRS + Observability)
>
> **예상 소요**: 1~2일

---

## 이 Phase에서 체감할 것

| # | 체감 포인트 | 확인 방법 |
|---|-----------|----------|
| 1 | Multi-stage Docker 빌드로 이미지 최적화 | 빌드 결과 ~300MB (JVM) 또는 ~80MB (네이티브) |
| 2 | Virtual Threads로 높은 동시성 | 한 줄 설정 → Thread.sleep이 효율적으로 동작 |
| 3 | 환경별 설정 분리 | `SPRING_PROFILES_ACTIVE=prod` 로 전환 |
| 4 | Actuator Health check | Docker HEALTHCHECK 연동 |
| 5 | Docker Compose로 전체 스택 | PostgreSQL + App 한 번에 기동 |

---

## Step 1: Dockerfile (Multi-stage Build)

```dockerfile
# Dockerfile
# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle/ gradle/

# 의존성 캐싱
RUN ./gradlew dependencies --no-daemon

# 소스 복사 & 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre

WORKDIR /app

# 보안: non-root 사용자
RUN groupadd -r appuser && useradd -r -g appuser appuser

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appuser /app
USER appuser

# JVM 튜닝
ENV JAVA_OPTS="-XX:+UseZGC \
               -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## Step 2: Docker Compose (전체 스택)

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: shoptracker
      POSTGRES_USER: shoptracker
      POSTGRES_PASSWORD: ${DB_PASSWORD:-shoptracker123}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U shoptracker"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/shoptracker
      SPRING_DATASOURCE_USERNAME: shoptracker
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-shoptracker123}
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3

volumes:
  postgres_data:
```

---

## Step 3: 환경별 설정

### application-prod.yml

```yaml
# src/main/resources/application-prod.yml
spring:
  jpa:
    show-sql: false
    properties:
      hibernate:
        format_sql: false

  flyway:
    clean-disabled: true   # ★ 프로덕션에서 clean 절대 금지

  threads:
    virtual:
      enabled: true        # ★ Virtual Threads 활성화

  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

logging:
  level:
    root: WARN
    com.shoptracker: INFO
  pattern:
    console: '{"timestamp":"%d","level":"%p","logger":"%logger","message":"%m"}%n'

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics
  endpoint:
    health:
      show-details: when-authorized
  tracing:
    sampling:
      probability: 0.1    # 프로덕션: 10% 샘플링
```

### application-dev.yml

```yaml
# src/main/resources/application-dev.yml
spring:
  jpa:
    show-sql: true
  flyway:
    clean-disabled: false
  threads:
    virtual:
      enabled: true

logging:
  level:
    com.shoptracker: DEBUG
    org.springframework.modulith: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: "*"
  tracing:
    sampling:
      probability: 1.0    # 개발: 100% 샘플링
```

---

## Step 4: Virtual Threads 설명

Spring Boot 4 + JDK 21에서 Virtual Threads를 활성화하면:

```yaml
spring.threads.virtual.enabled: true
```

이 한 줄로:
- 내장 Tomcat이 Virtual Threads 사용 (요청당 경량 스레드)
- `Thread.sleep()`, 블로킹 I/O가 효율적으로 동작
- FakePaymentGateway의 `Thread.sleep(300)`이 OS 스레드를 점유하지 않음
- DB 커넥션 대기도 효율적

```
기존 (Platform Threads):      Virtual Threads:
요청 200개 → 스레드 200개       요청 200개 → Virtual Thread 200개
→ OS 스레드 200개 점유           → OS 스레드 ~8개만 사용
→ 메모리 ~200MB                  → 메모리 ~2MB
```

> **주의**: HikariCP 커넥션 풀 크기가 병목이 될 수 있음.
> Virtual Threads는 무한정 만들 수 있지만, DB 커넥션은 유한.
> `maximum-pool-size`를 적절히 설정해야 함.

---

## Step 5: (선택) GraalVM 네이티브 이미지

### 5.1 build.gradle.kts 수정

```kotlin
plugins {
    // ... 기존 플러그인
    id("org.graalvm.buildtools.native") version "0.10.4"
}
```

### 5.2 빌드 & 실행

```bash
# 네이티브 이미지 빌드 (시간 오래 걸림: 3~10분)
./gradlew nativeCompile

# 실행 (시작 시간 ~50ms!)
./build/native/nativeCompile/shoptracker
```

### 5.3 네이티브 Dockerfile

```dockerfile
# Dockerfile.native
FROM ghcr.io/graalvm/native-image-community:21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile --no-daemon

FROM ubuntu:24.04
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/shoptracker .
RUN groupadd -r appuser && useradd -r -g appuser appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["./shoptracker"]
```

| 비교 | JVM (JRE) | GraalVM Native |
|------|-----------|----------------|
| 이미지 크기 | ~300MB | ~80MB |
| 시작 시간 | ~2초 | ~50ms |
| 메모리 | ~200MB | ~50MB |
| 빌드 시간 | ~30초 | ~5분 |
| Reflection 호환성 | 완벽 | 설정 필요 |

> **권장**: 학습 프로젝트에서는 JVM으로 충분. 네이티브는 체험 목적으로만.

---

## Step 6: 실행 & 검증

```bash
# 1. 전체 스택 기동
docker compose up --build -d

# 2. 로그 확인
docker compose logs -f app

# 3. Health check
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}

# 4. 전체 흐름 테스트
# Premium 구독 → 주문 → 결제(10% 할인) → 배송(무료) → Tracking
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"customerName": "최프리미엄", "tier": "premium"}'

curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-Customer-Name: 최프리미엄" \
  -d '{"customerName": "최프리미엄", "items": [{"productName": "모니터", "quantity": 1, "unitPrice": 500000}]}'

sleep 3

# 타임라인 확인
curl http://localhost:8080/api/v1/tracking/order/{orderId}/timeline | jq .

# Swagger UI
open http://localhost:8080/swagger-ui.html

# 5. 정리
docker compose down
```

---

## 최종 프로젝트 구조 요약

```
shoptracker/
├── docker-compose.yml
├── Dockerfile
├── Dockerfile.native (선택)
├── build.gradle.kts
├── src/main/java/com/shoptracker/
│   ├── ShopTrackerApplication.java
│   ├── shared/          # 이벤트, SubscriptionContext, 설정, 에러 핸들링
│   ├── subscription/    # Phase 1: 구독 모듈
│   ├── orders/          # Phase 1: 주문 모듈
│   ├── payments/        # Phase 2: 결제 + 할인 정책
│   ├── shipping/        # Phase 3: 배송 + 배송비 정책
│   └── tracking/        # Phase 4: 전체 Saga 추적
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/    # V1~V5 Flyway
└── src/test/java/com/shoptracker/
    ├── ModuleStructureTest.java     # Spring Modulith 경계 검증
    ├── unit/            # Phase 1~3: DB 없는 도메인 테스트
    ├── integration/     # Phase 2~4: Testcontainers 통합 테스트
    └── module/          # Phase 2~4: Modulith Scenario 테스트
```

---

## 전체 Phase 완료 후 확인할 것

| # | 확인 항목 | 명령어 |
|---|---------|--------|
| 1 | 도메인에 Spring 의존성 없음 | `grep -r "import org.springframework" */domain/` → 0건 |
| 2 | 모듈 간 직접 import 없음 | `grep -r "import.*payments" orders/` → 0건 |
| 3 | Spring Modulith 경계 검증 | `./gradlew test --tests ModuleStructureTest` → PASS |
| 4 | 단위 테스트 DB 불필요 | `./gradlew test --tests "*.unit.*"` → 0.5초 이내 |
| 5 | 통합 테스트 전체 통과 | `./gradlew test` → ALL PASS |
| 6 | Docker 배포 정상 | `docker compose up --build` → Health UP |
| 7 | 전체 Saga 타임라인 | `/tracking/order/{id}/timeline` → 3개+ 이벤트 |

---

## FastAPI ↔ Spring 최종 비교

| 관점 | FastAPI ShopTracker | Spring ShopTracker |
|------|--------------------|--------------------|
| 프레임워크 | FastAPI 0.129+ | Spring Boot 4.0.x |
| 언어 | Python 3.12 | Java 21 (LTS) |
| DI | Dishka (외부) | Spring IoC (내장) |
| 이벤트 | InMemoryEventBus (직접 구현) | Spring ApplicationEvent (내장) |
| 모듈 검증 | grep 수동 | Spring Modulith 자동 |
| 이벤트 지속성 | 직접 구현 필요 | Spring Modulith 내장 |
| ORM | SQLAlchemy 2.0 async | Spring Data JPA |
| 마이그레이션 | Alembic | Flyway |
| 테스트 | pytest + httpx | JUnit 5 + Testcontainers + Modulith Scenario |
| 배포 | Gunicorn + Uvicorn | Tomcat + Virtual Threads |
| 관찰성 | structlog | spring-boot-starter-opentelemetry |
| 네이티브 | 해당 없음 (인터프리터) | GraalVM 네이티브 (선택) |

---

## 나중에 확장 가능한 방향

| 현재 | 확장 | 변경 범위 |
|------|------|----------|
| ApplicationEvent | Spring Cloud Stream (Kafka) | 설정 + @ApplicationModuleListener 유지 |
| FakePaymentGateway | 실제 PG 연동 | 구현체 교체 (인터페이스 동일) |
| X-Customer-Name 헤더 | Spring Security + JWT | SecurityConfig 추가 |
| 단일 DB | 모듈별 DB 분리 | Repository 구현체 교체 |
| 모듈러 모놀리스 | 마이크로서비스 | Spring Modulith → 별도 서비스 분리 |
| JVM | GraalVM Native | 빌드 설정만 변경 |