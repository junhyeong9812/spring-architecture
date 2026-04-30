# 🛒 ShopTracker (Spring Edition)

**Spring Boot 4 + Spring Modulith 클린 아키텍처 학습을 위한 모듈러 모놀리스 프로젝트**

주문 → 결제 → 배송 흐름을 Hexagonal Architecture로 구현하며, 모듈 간 통신은 Spring Modulith Events, 정책 교체는 Spring DI로 처리합니다.

> 🔗 이 프로젝트는 [FastAPI ShopTracker](./fastapi/)와 **동일한 도메인·동일한 아키텍처 패턴**을 Spring Boot로 구현한 쌍둥이 프로젝트입니다.
> 같은 설계를 두 프레임워크로 나란히 비교하면서 각 생태계의 관용적 구현 방식과 최신 트렌드를 체감합니다.

---

## 왜 같은 프로젝트를 두 프레임워크로 만드는가

| 목적 | 설명 |
|------|------|
| **1:1 패턴 비교** | 동일한 Hexagonal Architecture를 Python Protocol vs Java Interface, Dishka vs Spring IoC 등으로 나란히 비교 |
| **프레임워크 관용구 차이** | "같은 DI인데 Spring은 이렇게, FastAPI는 저렇게" — 추상 개념이 각 생태계에서 어떻게 구체화되는지 체감 |
| **최신 트렌드 적용** | Spring Boot 4 모듈화, Spring Modulith 2.0, Virtual Threads, JSpecify, OpenTelemetry 등 2025~2026 생태계 변화를 실전 적용 |
| **전환 비용 감각** | Python → Java, Java → Python 전환 시 진짜 바뀌는 것과 안 바뀌는 것(도메인 로직)의 경계를 직접 확인 |

---

## 무엇을 배우는 프로젝트인가

| 패턴 | 어디서 체감하는가 | FastAPI는 | Spring은 |
|------|----------------|-----------|----------|
| **Hexagonal Architecture** | `domain/`에 Spring·JPA import 0건 | Protocol + dataclass | Interface + record |
| **의존성 역전 (DIP)** | Repository를 인터페이스로 정의, 구현체 교체 | `typing.Protocol` | Java `interface` |
| **DI + 정책 주입** | 구독 등급에 따라 할인·배송비 정책이 자동 교체 | Dishka Provider match | `@Bean` + `@RequestScope` switch |
| **모듈 간 이벤트** | 직접 import 없이 이벤트로만 소통 | InMemoryEventBus (직접 구현) | Spring ApplicationEvent (내장) |
| **모듈 경계 검증** | 모듈 간 허용되지 않은 의존 차단 | `grep` 수동 확인 | `ApplicationModules.verify()` 자동 |
| **이벤트 지속성** | 이벤트 발행 후 장애 시 재처리 | 직접 구현 필요 | Spring Modulith 내장 |
| **CQRS** | Command(주문 생성)와 Query(목록 조회)의 서비스 분리 | Handler 분리 | CommandService / QueryService |
| **Saga / 추적** | 주문→결제→배송 전체 여정을 Tracking 모듈이 기록 | 동일 | `@ApplicationModuleListener` |
| **테스트 전략** | 도메인 단위 테스트(DB 없음) + 모듈 테스트 + 통합 | pytest + httpx | JUnit 5 + Modulith Scenario + Testcontainers |
| **프로덕션 배포** | 컨테이너 기반 배포 | Gunicorn + Uvicorn | Virtual Threads + (선택) GraalVM Native |

---

## 아키텍처

```
┌──────────────────────────────────────────────────────┐
│            ShopTracker App (Spring Boot 4)            │
│              Spring Modulith 2.0 모듈 경계             │
│                                                      │
│  ┌─────────┐  ┌──────────┐  ┌─────────┐            │
│  │ orders  │  │ payments │  │shipping │            │
│  └────┬────┘  └─────┬────┘  └────┬────┘            │
│       │        ┌────┴─────┐      │                  │
│       │        │subscription│     │                  │
│       │        └────┬─────┘      │                  │
│       └─────────────┼────────────┘                  │
│              ┌──────┴───────┐                       │
│              │   tracking   │                       │
│              └──────┬───────┘                       │
│   ┌─────────────────┴──────────────────────┐        │
│   │  Spring ApplicationEventPublisher       │        │
│   │  + @ApplicationModuleListener           │        │
│   └────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────┘
```

**모듈 간 규칙**: 직접 import 금지. `shared/events`에 정의된 이벤트 record로만 소통.
Spring Modulith가 테스트 시점에 이 규칙을 **자동 검증**합니다.

---

## 모듈별 역할

| 모듈 | 역할 |
|------|------|
| **orders** | 주문 생성·취소, 상태 전이 관리 |
| **payments** | 결제 처리, 구독 기반 할인 정책 적용, Fake PG |
| **shipping** | 배송 생성·상태 관리, 구독 기반 배송비 정책 적용 |
| **subscription** | 구독 등급 관리 (NONE / BASIC / PREMIUM) |
| **tracking** | 모든 이벤트를 구독하여 주문 여정(Saga) 기록 |

---

## 구독 등급별 혜택

| 혜택 | NONE | BASIC | PREMIUM |
|------|:----:|:-----:|:-------:|
| 결제 할인 | 0% | 5% | 10% |
| 배송비 | 3,000원 | 50% 할인 | 무료 |
| 무료배송 기준 | 5만원↑ | 3만원↑ | 항상 |

---

## 기술 스택

| 영역 | 기술 | FastAPI 대응 |
|------|------|-------------|
| **Framework** | Spring Boot 4.0.x | FastAPI 0.129+ |
| **Language** | Java 21 (LTS) | Python 3.12 |
| **모듈화** | Spring Modulith 2.0 | — (수동 관리) |
| **DB** | PostgreSQL 16 | PostgreSQL 16 + asyncpg |
| **ORM** | Spring Data JPA + Hibernate 7 | SQLAlchemy 2.0 (async) |
| **Migration** | Flyway | Alembic |
| **DI** | Spring IoC (내장) | Dishka (외부) |
| **Validation** | Jakarta Bean Validation 3.0 | Pydantic v2 |
| **Settings** | `application.yml` + `@ConfigurationProperties` | pydantic-settings |
| **Testing** | JUnit 5 + Testcontainers + Modulith Scenario | pytest + httpx AsyncClient |
| **Logging** | SLF4J + Logback | structlog |
| **Observability** | spring-boot-starter-opentelemetry | — (직접 구성) |
| **Container** | Docker Compose | Docker Compose |
| **Server** | 내장 Tomcat + Virtual Threads | Gunicorn + Uvicorn workers |
| **Build** | Gradle 8 (Groovy DSL) | pyproject.toml |

---

## 프로젝트 구조

```
src/main/java/com/shoptracker/
├── ShopTrackerApplication.java
├── shared/                         # 모듈 간 공유 계약
│   ├── SubscriptionContext.java    # 불변 DTO (record)
│   ├── config/                     # SubscriptionContextConfig, JPA
│   ├── events/                     # 이벤트 record 7개
│   └── exception/                  # GlobalExceptionHandler
├── orders/                         # 📦 주문
│   ├── domain/model/               # Order, Money, OrderStatus
│   ├── domain/port/out/            # OrderRepository (Interface)
│   ├── application/service/        # CommandService, QueryService
│   ├── application/eventhandler/   # PaymentApproved → 상태변경
│   ├── adapter/out/persistence/    # JPA Entity, Mapper, Adapter
│   └── adapter/in/web/            # Controller, Request/Response DTO
├── payments/                       # 💳 결제
│   ├── domain/model/policy/        # SubscriptionDiscountPolicy, NoDiscountPolicy
│   ├── domain/port/out/            # DiscountPolicy, PaymentGateway (Interface)
│   ├── adapter/out/                # FakePaymentGateway
│   └── internal/                   # PaymentsPolicyConfig ← DI 정책 조립
├── shipping/                       # 🚚 배송
│   ├── domain/model/policy/        # Standard/Basic/PremiumShippingFeePolicy
│   ├── domain/port/out/            # ShippingFeePolicy (Interface)
│   └── internal/                   # ShippingPolicyConfig ← DI 정책 조립
├── subscription/                   # 🎫 구독
│   ├── domain/model/               # Subscription, SubscriptionTier
│   └── application/port/in/        # SubscriptionQueryPort
└── tracking/                       # 📊 추적 (Saga)
    ├── domain/model/               # OrderTracking, TrackingEvent
    └── application/eventhandler/   # 모든 이벤트를 @ApplicationModuleListener로 구독
```

각 모듈은 **Hexagonal Architecture**를 따릅니다:

- `domain/` — 순수 Java. Spring·JPA import 없음.
- `application/` — Use Case (Command/Query 서비스, 이벤트 핸들러)
- `adapter/out/` — JPA, 외부 서비스 연동
- `adapter/in/` — REST Controller, DTO
- `internal/` — Spring Modulith 내부 패키지 (모듈 밖에서 접근 불가)

---

## 시작하기

### 요구사항

- JDK 21+
- Docker & Docker Compose

### 실행

```bash
# 저장소 클론
git clone <repo-url>
cd shoptracker-spring

# 컨테이너 실행 (PostgreSQL + App)
docker compose up --build -d

# Health check
curl http://localhost:8080/actuator/health

# API 문서 확인
open http://localhost:8080/swagger-ui.html
```

### 개발 환경

```bash
# DB만 실행
docker compose up -d postgres

# 개발 서버 (Flyway 마이그레이션 자동 실행)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 테스트
./gradlew test                                              # 전체
./gradlew test --tests "com.shoptracker.unit.*"             # 단위 (DB 불필요)
./gradlew test --tests "com.shoptracker.integration.*"      # 통합 (Testcontainers)
./gradlew test --tests "com.shoptracker.ModuleStructureTest" # 모듈 경계 검증
```

---

## 이벤트 흐름

```
주문 생성 ──▶ OrderCreatedEvent
                  │
            ┌─────┼──────────┐
            ▼     ▼          ▼
        Payments  Tracking   (기록)
            │
    ┌───────┴───────┐
    ▼               ▼
 Approved        Rejected
    │               │
 ┌──┴──┐        ┌──┴──┐
 ▼     ▼        ▼     ▼
Ship  Track   Cancel  Track
```

결제·배송 생성은 API가 아닌 **`@ApplicationModuleListener` 이벤트 핸들러**가 자동 처리합니다.
Spring Modulith는 이벤트를 DB에 저장하여, 처리 실패 시 자동 재시도합니다.

---

## API 요약

| 모듈 | Endpoint | 주요 기능 |
|------|----------|----------|
| Subscriptions | `POST /api/v1/subscriptions` | 구독 생성 |
| Orders | `POST /api/v1/orders` | 주문 생성 |
| Orders | `GET /api/v1/orders` | 주문 목록 (페이지네이션) |
| Payments | `GET /api/v1/payments/order/{id}` | 결제 상세 (할인 내역) |
| Shipping | `GET /api/v1/shipping/order/{id}` | 배송 상세 (배송비 내역) |
| Tracking | `GET /api/v1/tracking/order/{id}/timeline` | 주문 여정 타임라인 |

전체 API 명세는 `/swagger-ui.html` (Swagger UI)에서 확인할 수 있습니다.

---

## 테스트

```bash
./gradlew test --tests "*.unit.*"                # 도메인 로직 (DB 없음, <1초)
./gradlew test --tests "*.module.*"              # Spring Modulith 시나리오
./gradlew test --tests "*.integration.*"         # 이벤트 + DB 연동 (Testcontainers)
./gradlew test --tests "ModuleStructureTest"     # 모듈 경계 자동 검증
./gradlew test jacocoTestReport                  # 커버리지
```

단위 테스트는 DB, Spring Context, 외부 서비스 없이 **순수 도메인 로직만** 검증합니다.
이것이 Hexagonal Architecture의 핵심 이점입니다.

**Spring Modulith 모듈 테스트**(`@ApplicationModuleTest`)는 이벤트 발행 → 수신 → 결과를
`Scenario` API로 선언적으로 검증합니다 — FastAPI에는 없는 Spring만의 차별점입니다.

---

## FastAPI ↔ Spring 핵심 차이 요약

| 관점 | FastAPI | Spring |
|------|---------|--------|
| **DI 컨테이너** | Dishka (외부 라이브러리) | Spring IoC (프레임워크 핵심) |
| **Port 정의** | `typing.Protocol` | Java `interface` |
| **불변 DTO** | `@dataclass(frozen=True)` | Java `record` |
| **이벤트 버스** | `InMemoryEventBus` 직접 구현 | `ApplicationEventPublisher` 내장 |
| **이벤트 수신** | `event_bus.subscribe()` 수동 등록 | `@ApplicationModuleListener` 선언적 |
| **이벤트 지속성** | 직접 구현 필요 | Spring Modulith DB 자동 저장 |
| **모듈 경계 검증** | `grep` 수동 | `ApplicationModules.verify()` 자동 |
| **정책 주입** | Dishka Provider `match` 문 | `@Configuration` + `@Bean` + `switch` |
| **Request Scope** | `Scope.REQUEST` | `@RequestScope` |
| **모듈 테스트** | — | `@ApplicationModuleTest` + `Scenario` |
| **DB 접근** | SQLAlchemy 2.0 async | Spring Data JPA (인터페이스만 작성) |
| **마이그레이션** | Alembic | Flyway |
| **동시성** | asyncio + Uvicorn | Virtual Threads (JDK 21) |
| **네이티브 빌드** | — | GraalVM Native Image (선택) |
| **관찰성** | structlog (직접 구성) | `spring-boot-starter-opentelemetry` (한 줄) |

### 바뀌지 않는 것 (프레임워크 독립)

- **도메인 엔티티 구조**: Order, Payment, Shipment, Subscription, Tracking — 필드와 비즈니스 규칙이 동일
- **정책 패턴**: DiscountPolicy / ShippingFeePolicy 인터페이스 + 구현체 분리
- **이벤트 흐름**: OrderCreated → PaymentApproved → ShipmentCreated
- **모듈 경계**: 모듈 간 직접 의존 0건
- **테스트 전략**: 도메인 단위 테스트 → 통합 테스트 → E2E 테스트 피라미드
- **Hexagonal 레이어**: domain → application → adapter 방향 일관

---

## 2025~2026 최신 트렌드 적용 현황

| 트렌드 | 적용 내용 | 상세 |
|--------|---------|------|
| **Spring Boot 4.0** | 모듈화된 autoconfigure, Jakarta EE 11 | Phase 1 |
| **Spring Modulith 2.0** | 모듈 경계 검증, 이벤트 지속성, Scenario 테스트 | Phase 1~4 |
| **Virtual Threads (JDK 21)** | `spring.threads.virtual.enabled=true` 한 줄로 활성화 | Phase 6 |
| **JSpecify Null Safety** | `@NonNull` / `@Nullable` 표준화 | Phase 1 |
| **RestClient** | RestTemplate 대체 (Framework 7의 권장 HTTP 클라이언트) | 외부 연동 시 |
| **spring-boot-starter-opentelemetry** | 단일 의존성으로 traces + metrics + logs | Phase 5 |
| **`@Observed`** | Micrometer Observation API 기반 커스텀 계측 | Phase 5 |
| **Java Records** | 불변 DTO, Value Object, 이벤트를 record로 선언 | 전체 |
| **Pattern Matching switch** | 정책 팩토리에서 `switch (tier)` 표현식 활용 | Phase 2~3 |
| **ProblemDetail (RFC 9457)** | 표준 에러 응답 형식 | Phase 5 |
| **GraalVM Native Image** | 시작 시간 ~50ms, 메모리 ~50MB (선택) | Phase 6 |
| **Flyway** | Spring Boot 4 통합 마이그레이션 | Phase 1 |
| **Testcontainers** | 테스트에서 실제 PostgreSQL 사용 | Phase 2~4 |

---

## 구현 Phase

| Phase | 내용 | 핵심 체감 | 소요 |
|:-----:|------|----------|:----:|
| **1** | 뼈대 + Subscription + Orders | Hexagonal 구조, `domain/`에 Spring import 0건 | 3~4일 |
| **2** | Spring Events + Payments + 할인 정책 | DI 정책 주입, 모듈 간 이벤트 통신 | 3~4일 |
| **3** | Shipping + 배송비 정책 | 두 번째 정책 주입, 느슨한 결합 | 2~3일 |
| **4** | Tracking + 전체 Saga | 이벤트 타임라인, 실패 보상 (결제 거절→주문 취소) | 2~3일 |
| **5** | CQRS 고도화 + Observability | ReadModel, 페이지네이션, OpenTelemetry | 2일 |
| **6** | 프로덕션 배포 | Docker, Virtual Threads, GraalVM Native (선택) | 1~2일 |

각 Phase의 상세 구현 가이드는 `docs/` 폴더의 Phase별 README를 참고하세요.

---

## 설계 문서

상세 설계 (도메인 엔티티, 이벤트 정의, DI 구조, 정책 객체, FastAPI↔Spring 비교 등)는 [`PROJECT-DESIGN.md`](./PROJECT-DESIGN.md)를 참고하세요.

---

## 문서 인덱스

### Phase 가이드 (`docs/phase/`)
- `PHASE1.md` ~ `PHASE6.md` — 단계별 구현 가이드
- `PHASE5_fix.md` — Phase 5 누락 항목 7건 분석
- **`PHASE_END.md`** — 누락된 모든 내용 보강 + Spring Boot 3→4 마이그레이션 + 모듈 경계 위반 해소 + 테스트 결과 종합. 이 코드베이스를 동작 가능하게 만든 모든 변경의 단일 소스.

### Study 문서 (`docs/study/`) — 사용된 기술/개념 정리
- `spring-boot.md` / `java-21.md` / `spring-ioc-di.md`
- `spring-application-events.md` / `spring-modulith.md` / `spring-data-jpa.md`
- `flyway.md` / `testing-stack.md`
- `tomcat-virtual-threads.md` / `opentelemetry.md` / `graalvm-native.md`
- `cqrs-and-hexagonal.md`
- `observability-concepts.md` — 트레이싱/샘플링/exemplar/카디널리티
- `resilience-patterns.md` — Circuit Breaker, Retry, Bulkhead, Rate Limiter
- `health-library.md` — Spring Boot Health/Actuator

---

## 현재 테스트 상태

```
./gradlew test
42 tests, 39 PASS / 3 FAIL
```

**통과 (39)**: 모든 단위 테스트(30개), 모듈 경계 검증, 컨텍스트 로드, 핵심 결제/배송 통합 테스트.

**잔여 실패 (3)**: Spring Boot 4 환경에서의 타이밍/모듈 격리 이슈. 자세한 분석은 `docs/phase/PHASE_END.md` 16절.

---

## License

MIT