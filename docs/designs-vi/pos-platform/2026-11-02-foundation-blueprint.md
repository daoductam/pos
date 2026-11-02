# ADR: Foundation Blueprint — Khung Sườn Kỹ Thuật Chuẩn cho POS Platform

Started at: 2026/11/02 21:48:00 +07
Finished at: 2026/11/02 21:54:00 +07
Total time: 6 minutes

---

## Nguồn tham chiếu

- **Tầm nhìn:** [`2026-10-12-vision.md`](./2026-10-12-vision.md)
- **Yêu cầu:** [`2026-10-12-requirements.md`](./2026-10-12-requirements.md)
- **Kiến trúc:** [`2026-10-12-architecture.md`](./2026-10-12-architecture.md)
- **Hợp đồng:** [`2026-10-12-contracts.md`](./2026-10-12-contracts.md)
- **Kết quả thiết kế:** [`2026-10-12-pos-platform_result.md`](./2026-10-12-pos-platform_result.md)
- **Ý tưởng ban đầu:** [`idea.md`](../../../idea.md)
- **AGENTS.md:** [`AGENTS.md`](../../../AGENTS.md)

---

## Mục đích Tài liệu

Tài liệu này là **Architecture Decision Record (ADR)** chốt toàn bộ quyết định kỹ thuật nền tảng trước khi bắt đầu sinh code. Nó bao gồm 12 mảng kỹ thuật, từ cấu trúc monorepo đến security baseline, tạo thành bản thiết kế khung sườn (Foundation Blueprint) chuẩn cho toàn bộ hệ thống POS Platform đa tenant.

**Nguyên tắc:** Mọi service và component trong hệ thống phải tuân theo các quyết định trong ADR này. Bất kỳ sai lệch nào cần phải được ghi thành ADR bổ sung trước khi thực hiện.

---

## Tóm tắt Quyết định Stack

| Quyết định | Lựa chọn | Cơ sở lý do |
|---|---|---|
| Repository strategy | **Monorepo** | Đồng bộ version, atomic commits xuyên service, shared libs dễ phát triển |
| Backend language | **Java 21 (LTS)** | Virtual threads, pattern matching, record classes; đội ngũ quen thuộc |
| Backend framework | **Spring Boot 3.4.x** | Hệ sinh thái trưởng thành, native GraalVM support, security/actuator built-in |
| Build tool | **Gradle 8.x (Kotlin DSL)** với version catalog | Incremental build nhanh, composite builds cho monorepo, type-safe configuration |
| Frontend framework | **Angular 18+ (Standalone Components)** | Signals, deferrable views, SSR ready; phù hợp enterprise POS |
| Database | **PostgreSQL 16** | ACID, JSONB, UUID v7 support, row-level security cho multi-tenant |
| Migration tool | **Flyway 10.x** | Forward-only, SQL-based, tích hợp Spring Boot auto-configuration |
| Internal architecture | **Hexagonal Architecture (Ports & Adapters)** | Tách biệt domain logic khỏi infrastructure; testability cao |
| CI/CD | **GitHub Actions** | Monorepo path filters, matrix builds, reusable workflows |
| IaC | **Terraform 1.9+** | Declarative, state management, AWS provider mature |
| Event infrastructure | **EventBridge + SQS** (with local adapter) | Fan-out routing, DLQ built-in, content-based filtering |
| Observability | **OpenTelemetry + AWS X-Ray + CloudWatch** | W3C Trace Context, auto-instrumentation cho Spring Boot |

---

## 1. Cấu trúc Monorepo và Quy tắc Tổ chức Thư mục

### 1.1 Rationale: Monorepo vs Multirepo

| Tiêu chí | Monorepo ✅ | Multirepo |
|---|---|---|
| Atomic refactor xuyên service | Một commit duy nhất | Phải đồng bộ nhiều PR |
| Shared library development | Import trực tiếp, không cần publish | Phải publish artifact riêng |
| CI complexity | Path filters + affected analysis | Đơn giản hơn per-repo nhưng khó orchestrate |
| Code discovery | Grep/search toàn bộ codebase | Phải clone nhiều repo |
| Versioning contracts | Luôn đồng bộ tại HEAD | Dễ bị version drift |

**Quyết định:** Monorepo vì quy mô đội nhỏ, cần tốc độ phát triển và đảm bảo consistency giữa 12 bounded contexts.

### 1.2 Cấu trúc Thư mục Chuẩn

```
pos/
├── .github/
│   ├── workflows/              # GitHub Actions workflows
│   │   ├── ci.yml              # Main CI pipeline
│   │   ├── cd-staging.yml      # Deploy to staging
│   │   ├── cd-production.yml   # Deploy to production
│   │   └── _reusable/          # Reusable workflow templates
│   │       ├── gradle-build.yml
│   │       ├── docker-build.yml
│   │       └── terraform-plan.yml
│   └── CODEOWNERS              # Ownership per service path
│
├── docs/
│   ├── designs-vi/             # Tài liệu thiết kế (đã có)
│   ├── adrs/                   # Architecture Decision Records
│   │   ├── 0001-monorepo.md
│   │   ├── 0002-hexagonal-architecture.md
│   │   └── ...
│   ├── runbooks/               # Hướng dẫn vận hành
│   └── api-specs/              # OpenAPI 3.1 specs (generated)
│
├── platform/
│   ├── shared-kernel/          # Shared library (Gradle subproject)
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/java/com/pos/shared/
│   │       │   ├── error/          # Error envelope, error codes
│   │       │   ├── event/          # CloudEvents envelope, base event
│   │       │   ├── correlation/    # CorrelationId, RequestContext
│   │       │   ├── idempotency/    # IdempotencyKey, IdempotencyFilter
│   │       │   ├── security/       # TenantContext, BranchScope, JwtClaims
│   │       │   ├── pagination/     # CursorPage, OffsetPage
│   │       │   ├── money/          # Money value object (integer VND)
│   │       │   └── id/             # UUIDv7 generator
│   │       └── test/
│   │
│   ├── shared-test/            # Test utilities dùng chung
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       └── main/java/com/pos/test/
│   │           ├── fixtures/       # Test data builders
│   │           ├── containers/     # Testcontainers config
│   │           ├── security/       # Mock JWT, tenant context
│   │           └── assertions/     # Custom AssertJ assertions
│   │
│   └── event-contracts/        # Event schema definitions (JSON Schema)
│       ├── build.gradle.kts
│       └── src/
│           └── main/resources/
│               └── schemas/
│                   ├── envelope.schema.json
│                   ├── order-paid.v1.schema.json
│                   ├── payment-paid.v1.schema.json
│                   └── ...
│
├── services/
│   ├── identity-access/        # Identity & Tenant Service
│   ├── catalog/                # Catalog Service
│   ├── order/                  # Order Service
│   ├── payment/                # Payment Service
│   ├── inventory/              # Inventory Service
│   ├── loyalty/                # Loyalty Service
│   ├── shift/                  # Shift Service
│   ├── recommendation/         # Recommendation Service
│   ├── reporting/              # Reporting Read Model Service
│   ├── audit/                  # Audit & Loss Prevention Service
│   ├── ai-assistant/           # AI Assistant Service
│   └── integration/            # Integration & E-Invoice Service
│
├── frontend/
│   └── pos-app/                # Angular 18+ POS & Dashboard
│       ├── angular.json
│       ├── package.json
│       ├── tsconfig.json
│       └── src/
│           ├── app/
│           │   ├── core/           # Singleton services, guards, interceptors
│           │   ├── shared/         # Reusable components, pipes, directives
│           │   ├── features/       # Feature modules (lazy-loaded)
│           │   └── app.routes.ts
│           ├── environments/
│           └── assets/
│
├── infra/
│   ├── docker/
│   │   ├── docker-compose.yml          # Orchestration cục bộ
│   │   ├── docker-compose.override.yml # Overrides cho dev
│   │   ├── postgres-init/              # Init scripts tạo DB + user riêng
│   │   │   └── 00-init-databases.sh
│   │   └── services/                   # Dockerfile per service
│   │       ├── Dockerfile.service      # Multi-stage template
│   │       └── Dockerfile.frontend
│   │
│   └── terraform/
│       ├── modules/
│       │   ├── networking/         # VPC, subnets, security groups
│       │   ├── ecs/                # ECS Fargate cluster + services
│       │   ├── rds/                # Aurora PostgreSQL clusters
│       │   ├── cognito/            # User pool, app client, resource server
│       │   ├── eventbridge/        # Event bus, rules, SQS targets
│       │   ├── step-functions/     # Checkout saga state machine
│       │   ├── secrets/            # Secrets Manager entries
│       │   └── observability/      # CloudWatch, X-Ray, alarms
│       ├── environments/
│       │   ├── sandbox/
│       │   ├── staging/
│       │   └── production/
│       └── backend.tf
│
├── tools/
│   ├── scripts/
│   │   ├── setup-local.sh          # Bootstrap local environment
│   │   ├── seed-data.sh            # Nạp dữ liệu demo
│   │   └── run-all-services.sh     # Start all services locally
│   └── k6/
│       ├── checkout-load.js        # Load test checkout flow
│       ├── recommendation-load.js  # Load test recommendation
│       └── helpers/
│
├── build.gradle.kts               # Root Gradle build
├── settings.gradle.kts             # Composite build settings
├── gradle.properties               # Shared properties
├── gradle/
│   ├── libs.versions.toml          # Version catalog
│   └── wrapper/
│
├── .gitignore
├── .editorconfig                   # Coding style consistency
├── AGENTS.md                       # (đã có)
├── README.md                       # (đã có)
└── idea.md                         # (đã có)
```

### 1.3 Quy tắc Tổ chức Thư mục

| Quy tắc | Mô tả |
|---|---|
| **Một service = một Gradle subproject** | Mỗi service trong `services/` là một subproject độc lập có `build.gradle.kts` riêng |
| **Shared code tối thiểu** | Chỉ primitives, error envelope, event envelope, correlation, idempotency, security context — **cấm** shared domain entity hoặc repository |
| **Không cross-import giữa services** | `services/order` không được import từ `services/payment`; giao tiếp qua API hoặc event |
| **Event contracts là source of truth** | JSON Schema trong `platform/event-contracts/` là hợp đồng chính thức cho mọi domain event |
| **Docs cạnh code** | API specs, ADRs nằm trong `docs/`; README per service nằm trong service folder |
| **Infra tách biệt** | Dockerfile, Compose, Terraform nằm trong `infra/`, không lẫn với application code |

---

## 2. Template Chuẩn cho Mỗi Spring Boot Service (Hexagonal Architecture)

### 2.1 Rationale: Tại sao Hexagonal Architecture

| So sánh | Layered Architecture | Hexagonal Architecture ✅ |
|---|---|---|
| Domain dependency | Domain phụ thuộc framework | Domain thuần Java, không phụ thuộc framework |
| Testability | Cần mock HTTP/DB layer | Test domain logic với plain unit tests |
| Adapter swapping | Khó thay thế data source | Swap adapter dễ dàng (AWS ↔ Local) |
| Phù hợp DDD | Trung bình | Rất phù hợp — domain ở trung tâm |

### 2.2 Package Structure chuẩn cho mỗi Service

```
services/<service-name>/
├── build.gradle.kts
├── README.md                           # Service overview, API summary
├── src/
│   ├── main/
│   │   ├── java/com/pos/<service>/
│   │   │   │
│   │   │   ├── PosXxxApplication.java  # @SpringBootApplication entry point
│   │   │   │
│   │   │   ├── domain/                 # ══ DOMAIN CORE (Pure Java) ══
│   │   │   │   ├── model/             # Aggregate roots, entities, value objects
│   │   │   │   │   ├── XxxAggregate.java
│   │   │   │   │   ├── XxxId.java     # Typed ID wrapper
│   │   │   │   │   └── XxxStatus.java # Enum with valid transitions
│   │   │   │   ├── command/           # Command objects (input to domain)
│   │   │   │   ├── event/             # Domain events (output from domain)
│   │   │   │   ├── exception/         # Domain-specific exceptions
│   │   │   │   ├── policy/            # Business rules, validators
│   │   │   │   └── port/              # ── PORTS (Interfaces) ──
│   │   │   │       ├── inbound/       # Use case interfaces (driven by adapters)
│   │   │   │       │   ├── CreateXxxUseCase.java
│   │   │   │       │   └── QueryXxxUseCase.java
│   │   │   │       └── outbound/      # Repository & external service interfaces
│   │   │   │           ├── XxxRepository.java
│   │   │   │           ├── XxxEventPublisher.java
│   │   │   │           └── ExternalServicePort.java
│   │   │   │
│   │   │   ├── application/            # ══ APPLICATION SERVICES ══
│   │   │   │   ├── service/           # Use case implementations (orchestrate domain)
│   │   │   │   │   ├── XxxCommandService.java  # Implements inbound ports
│   │   │   │   │   └── XxxQueryService.java
│   │   │   │   ├── mapper/            # Domain ↔ DTO mappers
│   │   │   │   └── dto/               # Application-level DTOs
│   │   │   │
│   │   │   └── adapter/               # ══ ADAPTERS (Infrastructure) ══
│   │   │       ├── inbound/           # ── Driving Adapters ──
│   │   │       │   ├── rest/          # REST Controllers
│   │   │       │   │   ├── XxxController.java
│   │   │       │   │   ├── request/   # Request DTOs (with validation)
│   │   │       │   │   └── response/  # Response DTOs
│   │   │       │   └── consumer/      # SQS/Event consumers
│   │   │       │       └── XxxEventConsumer.java
│   │   │       │
│   │   │       └── outbound/          # ── Driven Adapters ──
│   │   │           ├── persistence/   # JPA/JDBC implementations
│   │   │           │   ├── entity/    # JPA entities (@Entity)
│   │   │           │   ├── repository/# Spring Data repositories
│   │   │           │   └── mapper/    # Entity ↔ Domain mappers
│   │   │           ├── messaging/     # Event publishing (Outbox → EventBridge)
│   │   │           │   ├── OutboxRepository.java
│   │   │           │   └── OutboxPublisher.java
│   │   │           ├── client/        # REST clients to other services
│   │   │           └── config/        # Spring configuration classes
│   │   │
│   │   └── resources/
│   │       ├── application.yml             # Main config
│   │       ├── application-local.yml       # Local profile overrides
│   │       ├── application-aws.yml         # AWS profile overrides
│   │       └── db/migration/               # Flyway SQL migrations
│   │           ├── V1__create_core_tables.sql
│   │           ├── V2__create_outbox_and_idempotency.sql
│   │           └── V3__add_indexes_and_constraints.sql
│   │
│   └── test/
│       └── java/com/pos/<service>/
│           ├── domain/                     # Unit tests (no Spring context)
│           │   ├── model/
│           │   └── policy/
│           ├── application/                # Use case tests (mocked ports)
│           │   └── service/
│           ├── adapter/                    # Integration tests
│           │   ├── inbound/rest/           # @WebMvcTest
│           │   └── outbound/persistence/   # @DataJpaTest + Testcontainers
│           ├── integration/                # Full integration tests
│           │   └── XxxIntegrationTest.java  # @SpringBootTest + Testcontainers
│           └── contract/                   # Contract tests
│               └── XxxContractTest.java
```

### 2.3 Dependency Rule (Quy tắc Phụ thuộc)

```
                    ┌─────────────────────────────┐
                    │     adapter/inbound/rest     │  ← REST Controllers
                    │     adapter/inbound/consumer │  ← Event Consumers
                    └──────────┬──────────────────┘
                               │ depends on
                    ┌──────────▼──────────────────┐
                    │       application/           │  ← Use Case orchestration
                    └──────────┬──────────────────┘
                               │ depends on
                    ┌──────────▼──────────────────┐
                    │       domain/                │  ← Pure business logic
                    │   (model, port, policy)      │  ← NO framework imports
                    └──────────┬──────────────────┘
                               │ implemented by
                    ┌──────────▼──────────────────┐
                    │    adapter/outbound/         │  ← JPA, REST clients, messaging
                    └─────────────────────────────┘
```

**Quy tắc cứng:**

1. Package `domain` **KHÔNG ĐƯỢC** import bất kỳ class nào từ Spring Framework, JPA, Jackson annotations, hoặc adapter layer.
2. Package `application` chỉ phụ thuộc `domain` — không import adapter.
3. Package `adapter` phụ thuộc `application` và `domain` — đây là nơi duy nhất chứa framework-specific code.
4. Dependency injection qua constructor; `@Autowired` field injection bị cấm.
5. Domain model sử dụng `record` classes cho value objects và immutable data.

### 2.4 Naming Conventions trong Service

| Element | Convention | Ví dụ |
|---|---|---|
| Aggregate root | `XxxAggregate` | `OrderAggregate` |
| Entity | `Xxx` | `OrderLine` |
| Value Object | `Xxx` (record) | `Money`, `OrderId` |
| Inbound Port | `XxxUseCase` | `CreateOrderUseCase` |
| Outbound Port | `XxxRepository`, `XxxPublisher`, `XxxPort` | `OrderRepository`, `OrderEventPublisher` |
| Application Service | `XxxCommandService`, `XxxQueryService` | `OrderCommandService` |
| REST Controller | `XxxController` | `OrderController` |
| JPA Entity | `XxxEntity` | `OrderEntity` |
| Spring Data Repo | `XxxJpaRepository` | `OrderJpaRepository` |
| Adapter Mapper | `XxxPersistenceMapper` | `OrderPersistenceMapper` |
| Domain Event | `XxxEvent` | `OrderPaidEvent` |
| Command | `XxxCommand` | `CreateOrderCommand` |
| Request DTO | `XxxRequest` | `CreateOrderRequest` |
| Response DTO | `XxxResponse` | `OrderResponse` |

---

## 3. Shared Library Design (shared-kernel)

### 3.1 Phạm vi Cho phép

Shared kernel chỉ chứa **cross-cutting primitives** — **tuyệt đối không chứa domain logic** của bất kỳ bounded context nào.

### 3.2 Thành phần Chi tiết

#### 3.2.1 Error Envelope (`com.pos.shared.error`)

```java
// Error envelope chuẩn — tất cả services PHẢI sử dụng
public record ErrorEnvelope(
    String code,           // e.g., "validation_error", "not_found"
    String message,        // Human-readable message
    List<FieldError> details,
    String requestId,
    String correlationId,
    Instant timestamp
) {}

public record FieldError(
    String field,          // e.g., "lines[0].quantity"
    String code,           // e.g., "min_value"
    String message
) {}

// Standard error codes enum
public enum StandardErrorCode {
    VALIDATION_ERROR("validation_error", 422),
    UNAUTHORIZED("unauthorized", 401),
    FORBIDDEN("forbidden", 403),
    NOT_FOUND("not_found", 404),
    CONFLICT("conflict", 409),
    PAYMENT_PENDING("payment_pending", 202),
    INVENTORY_UNAVAILABLE("inventory_unavailable", 409),
    DUPLICATE_REQUEST("duplicate_request", 409),
    RATE_LIMITED("rate_limited", 429),
    SERVICE_UNAVAILABLE("service_unavailable", 503),
    INTERNAL_ERROR("internal_error", 500);
    // ...
}
```

#### 3.2.2 Success Envelope (`com.pos.shared.envelope`)

```java
public record SuccessEnvelope<T>(
    T data,
    Meta meta
) {
    public record Meta(
        String requestId,
        String correlationId,
        Instant timestamp
    ) {}
}

// Cursor-based pagination
public record CursorPage<T>(
    List<T> data,
    CursorMeta meta
) {
    public record CursorMeta(
        boolean hasMore,
        String nextCursor,
        String requestId,
        String correlationId,
        Instant timestamp
    ) {}
}

// Offset-based pagination
public record OffsetPage<T>(
    List<T> data,
    OffsetMeta meta
) {
    public record OffsetMeta(
        int page,
        int limit,
        long total,
        int totalPages,
        String requestId,
        String correlationId,
        Instant timestamp
    ) {}
}
```

#### 3.2.3 Event Envelope (`com.pos.shared.event`)

```java
// CloudEvents v1.0 envelope
public record DomainEventEnvelope<T>(
    String specversion,        // "1.0"
    String id,                 // UUID v7
    String type,               // "com.pos.order.OrderPaid.v1"
    String source,             // "https://service.pos.internal/order"
    Instant time,
    String datacontenttype,    // "application/json"
    String tenantid,           // UUID
    String branchid,           // UUID
    String correlationid,      // UUID
    T data
) {
    public static <T> DomainEventEnvelope<T> create(
        String eventType,
        String source,
        TenantContext ctx,
        T data
    ) {
        return new DomainEventEnvelope<>(
            "1.0",
            UUIDv7.generate().toString(),
            eventType,
            source,
            Instant.now(),
            "application/json",
            ctx.tenantId().toString(),
            ctx.branchId().toString(),
            ctx.correlationId().toString(),
            data
        );
    }
}
```

#### 3.2.4 Correlation Context (`com.pos.shared.correlation`)

```java
// Immutable request context — propagated via ThreadLocal (hoặc ScopedValue ở Java 21)
public record RequestContext(
    String requestId,          // req_<uuid_v7>
    String correlationId,      // cor_<uuid_v7>
    Instant timestamp
) {}

// Servlet filter tự động trích xuất/tạo correlation headers
// Propagate correlation ID vào MDC cho structured logging
// Propagate correlation ID vào event envelope khi publish
```

#### 3.2.5 Idempotency (`com.pos.shared.idempotency`)

```java
// Idempotency key filter — check trước khi xử lý command
// Lưu kết quả vào bảng idempotency_records cùng transaction
// Return cached response nếu key đã tồn tại với cùng payload hash
// Return 409 Conflict nếu key đã tồn tại nhưng payload khác

public interface IdempotencyStore {
    Optional<IdempotencyRecord> find(String key);
    void save(IdempotencyRecord record);
}

public record IdempotencyRecord(
    String key,
    String payloadHash,
    String responseBody,
    int httpStatus,
    Instant createdAt,
    Instant expiresAt
) {}
```

#### 3.2.6 Security Context (`com.pos.shared.security`)

```java
// Tenant context — resolved from JWT + Identity DB
public record TenantContext(
    UUID userId,
    UUID tenantId,
    UUID activeBranchId,       // From X-Active-Branch-Id header, verified
    Set<UUID> allowedBranchIds,
    Set<String> roles,         // OWNER, MANAGER, CASHIER, KITCHEN
    String correlationId
) {
    public boolean hasBranchAccess(UUID branchId) {
        return allowedBranchIds.contains(branchId);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public void requireBranchAccess(UUID branchId) {
        if (!hasBranchAccess(branchId)) {
            throw new ForbiddenException("No access to branch " + branchId);
        }
    }
}
```

#### 3.2.7 Money Value Object (`com.pos.shared.money`)

```java
// Tất cả giá trị tiền tệ PHẢI dùng value object này — cấm dùng primitive
public record Money(long amountMinor, String currency) {
    public Money {
        if (amountMinor < 0) throw new IllegalArgumentException("Amount must be >= 0");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("Currency required");
    }

    public static Money vnd(long amount) {
        return new Money(amount, "VND");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountMinor + other.amountMinor, this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountMinor - other.amountMinor, this.currency);
    }

    public Money multiply(int quantity) {
        return new Money(this.amountMinor * quantity, this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}
```

#### 3.2.8 UUID v7 Generator (`com.pos.shared.id`)

```java
// UUID v7 — time-ordered, B-Tree friendly on PostgreSQL
public final class UUIDv7 {
    private UUIDv7() {}

    public static UUID generate() {
        // UUID v7 implementation: timestamp-based, monotonic
        // Sử dụng thư viện com.fasterxml.uuid:java-uuid-generator
        // hoặc java.util.UUID.randomUUID() khi JDK chưa native hỗ trợ
    }
}
```

### 3.3 Ranh giới Cứng

| ✅ Được phép trong shared-kernel | ❌ Cấm trong shared-kernel |
|---|---|
| Error/Success envelope | Domain entities (Order, Payment, ...) |
| Event envelope (CloudEvents) | Repository interfaces |
| Correlation/RequestContext | Business logic / policies |
| Idempotency primitives | Service-specific DTOs |
| TenantContext/BranchScope | Database migration scripts |
| Money value object | Spring controller / endpoint |
| UUID v7 generator | JPA entities |
| Pagination primitives | — |

---

## 4. Docker Compose Layout và Database Isolation Strategy

### 4.1 Chiến lược Cách ly Database

**Quyết định:** Tất cả database chạy trên **1 PostgreSQL container** nhưng mỗi service có **database và credentials riêng biệt**.

| Service | Database Name | Username | Cách ly |
|---|---|---|---|
| identity-access | `pos_identity` | `identity_svc` | Full database isolation |
| catalog | `pos_catalog` | `catalog_svc` | Full database isolation |
| order | `pos_order` | `order_svc` | Full database isolation |
| payment | `pos_payment` | `payment_svc` | Full database isolation |
| inventory | `pos_inventory` | `inventory_svc` | Full database isolation |
| loyalty | `pos_loyalty` | `loyalty_svc` | Full database isolation |
| shift | `pos_shift` | `shift_svc` | Full database isolation |
| recommendation | `pos_recommendation` | `recommendation_svc` | Full database isolation |
| reporting | `pos_reporting` | `reporting_svc` | Full database isolation |
| audit | `pos_audit` | `audit_svc` | Full database isolation |
| ai-assistant | `pos_ai_assistant` | `ai_assistant_svc` | Full database isolation |
| integration | `pos_integration` | `integration_svc` | Full database isolation |

### 4.2 Init Script

```bash
# infra/docker/postgres-init/00-init-databases.sh
#!/bin/bash
set -e

declare -A SERVICES=(
    ["pos_identity"]="identity_svc"
    ["pos_catalog"]="catalog_svc"
    ["pos_order"]="order_svc"
    ["pos_payment"]="payment_svc"
    ["pos_inventory"]="inventory_svc"
    ["pos_loyalty"]="loyalty_svc"
    ["pos_shift"]="shift_svc"
    ["pos_recommendation"]="recommendation_svc"
    ["pos_reporting"]="reporting_svc"
    ["pos_audit"]="audit_svc"
    ["pos_ai_assistant"]="ai_assistant_svc"
    ["pos_integration"]="integration_svc"
)

for db in "${!SERVICES[@]}"; do
    user="${SERVICES[$db]}"
    password="${user}_password"  # Overridden by env in production

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
        CREATE USER ${user} WITH PASSWORD '${password}';
        CREATE DATABASE ${db} OWNER ${user};
        GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${user};
        -- Cấm kết nối từ user của service khác
        REVOKE ALL ON DATABASE ${db} FROM PUBLIC;

        \c ${db}
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
        CREATE EXTENSION IF NOT EXISTS "pg_trgm";
EOSQL
done
```

### 4.3 Docker Compose Structure

```yaml
# infra/docker/docker-compose.yml
version: "3.9"

x-service-common: &service-common
  restart: unless-stopped
  networks:
    - pos-network
  environment: &common-env
    SPRING_PROFILES_ACTIVE: local
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
    OTEL_SERVICE_NAME: ${SERVICE_NAME}
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 15s
    timeout: 5s
    retries: 3

services:
  # ══════════════════════════════════════
  # INFRASTRUCTURE
  # ══════════════════════════════════════
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres_root
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./postgres-init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - pos-network

  localstack:
    image: localstack/localstack:3.8
    ports:
      - "4566:4566"
    environment:
      SERVICES: sqs,events,secretsmanager,stepfunctions
      DEFAULT_REGION: ap-southeast-1
      DOCKER_HOST: unix:///var/run/docker.sock
    volumes:
      - localstack-data:/var/lib/localstack
      - ./localstack-init:/etc/localstack/init/ready.d
    networks:
      - pos-network

  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.110.0
    command: ["--config", "/etc/otel-config.yml"]
    volumes:
      - ./otel-config.yml:/etc/otel-config.yml
    ports:
      - "4317:4317"   # gRPC OTLP
      - "4318:4318"   # HTTP OTLP
      - "9090:9090"   # Prometheus metrics
    networks:
      - pos-network

  jaeger:
    image: jaegertracing/all-in-one:1.62
    ports:
      - "16686:16686"  # Jaeger UI
      - "14268:14268"
    networks:
      - pos-network

  # ══════════════════════════════════════
  # APPLICATION SERVICES
  # ══════════════════════════════════════
  identity-access:
    <<: *service-common
    build:
      context: ../../
      dockerfile: infra/docker/services/Dockerfile.service
      args:
        SERVICE_NAME: identity-access
    ports:
      - "8081:8080"
    environment:
      <<: *common-env
      SERVICE_NAME: identity-access
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pos_identity
      SPRING_DATASOURCE_USERNAME: identity_svc
      SPRING_DATASOURCE_PASSWORD: identity_svc_password
    depends_on:
      postgres:
        condition: service_healthy

  # ... (Các services khác theo cùng pattern, ports 8082-8092)

volumes:
  postgres-data:
  localstack-data:

networks:
  pos-network:
    driver: bridge
```

### 4.4 Port Mapping chuẩn

| Service | Host Port | Container Port |
|---|---|---|
| PostgreSQL | 5432 | 5432 |
| LocalStack | 4566 | 4566 |
| Jaeger UI | 16686 | 16686 |
| OTEL Collector | 4317-4318 | 4317-4318 |
| identity-access | 8081 | 8080 |
| catalog | 8082 | 8080 |
| order | 8083 | 8080 |
| payment | 8084 | 8080 |
| inventory | 8085 | 8080 |
| loyalty | 8086 | 8080 |
| shift | 8087 | 8080 |
| recommendation | 8088 | 8080 |
| reporting | 8089 | 8080 |
| audit | 8090 | 8080 |
| ai-assistant | 8091 | 8080 |
| integration | 8092 | 8080 |
| Angular POS App | 4200 | 4200 |

### 4.5 Dockerfile Multi-Stage Template

```dockerfile
# infra/docker/services/Dockerfile.service
FROM eclipse-temurin:21-jdk-alpine AS build
ARG SERVICE_NAME
WORKDIR /workspace

# Cache Gradle wrapper
COPY gradle/ gradle/
COPY gradlew .
RUN ./gradlew --version

# Cache dependencies
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml gradle/
COPY platform/ platform/
COPY services/${SERVICE_NAME}/build.gradle.kts services/${SERVICE_NAME}/
RUN ./gradlew :services:${SERVICE_NAME}:dependencies --no-daemon || true

# Build service
COPY services/${SERVICE_NAME}/src services/${SERVICE_NAME}/src
RUN ./gradlew :services:${SERVICE_NAME}:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE_NAME
RUN addgroup -S posapp && adduser -S posapp -G posapp
USER posapp
WORKDIR /app

COPY --from=build /workspace/services/${SERVICE_NAME}/build/libs/*.jar app.jar

# OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar otel-agent.jar

EXPOSE 8080
ENTRYPOINT ["java", \
    "-javaagent:otel-agent.jar", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
```

---

## 5. Flyway Migration Conventions

### 5.1 Quy tắc Đặt tên File Migration

```
Vx__<mô_tả_ngắn_gọn>.sql
│  │
│  └── Dấu gạch dưới đôi (double underscore) là bắt buộc
└── Version tăng dần: V1, V2, V3, ...

Repeatable migrations (chạy lại khi thay đổi):
R__<tên_view_hoặc_function>.sql
```

| Loại | Pattern | Ví dụ |
|---|---|---|
| **Khởi tạo schema** | `V1__create_core_tables.sql` | Tạo bảng chính, primary key, not null |
| **Outbox & Idempotency** | `V2__create_outbox_and_idempotency.sql` | Bảng outbox_events, idempotency_records |
| **Indexes** | `V3__add_indexes_and_constraints.sql` | Index, unique constraint, check constraint |
| **Seed data** | `V4__seed_demo_data.sql` | Dữ liệu demo/bootstrap |
| **Feature increment** | `V5__add_xxx_column.sql` | Mở rộng schema khi phát triển tính năng |

### 5.2 Quy tắc SQL Migration

```sql
-- Mỗi file migration PHẢI bắt đầu bằng comment mô tả mục đích
-- Migration: V1__create_core_tables.sql
-- Service: order-service
-- Description: Tạo bảng orders, order_lines và price_snapshots

-- 1. UUID v7 extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Bảng chính
CREATE TABLE orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    branch_id       UUID            NOT NULL,
    shift_id        UUID            NOT NULL,
    order_number    TEXT            NOT NULL,
    status          TEXT            NOT NULL DEFAULT 'DRAFT',
    service_mode    TEXT            NOT NULL,
    customer_id     UUID,
    subtotal_minor  BIGINT          NOT NULL DEFAULT 0,
    discount_minor  BIGINT          NOT NULL DEFAULT 0,
    tax_minor       BIGINT          NOT NULL DEFAULT 0,
    total_minor     BIGINT          NOT NULL DEFAULT 0,
    currency        TEXT            NOT NULL DEFAULT 'VND',
    note            TEXT,
    version         INTEGER         NOT NULL DEFAULT 1,
    created_by      UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 3. Comment trên TỪNG CỘT là bắt buộc
COMMENT ON TABLE orders IS 'Đơn hàng POS — nguồn sự thật cho vòng đời đơn';
COMMENT ON COLUMN orders.id IS 'UUID v7 — khóa chính sắp xếp theo thời gian';
COMMENT ON COLUMN orders.tenant_id IS 'Mã chuỗi thương hiệu sở hữu — dùng cho data isolation';
COMMENT ON COLUMN orders.branch_id IS 'Mã chi nhánh tạo đơn — dùng cho scope filter';
COMMENT ON COLUMN orders.total_minor IS 'Tổng tiền đơn hàng bằng số nguyên VND (minor unit)';
COMMENT ON COLUMN orders.version IS 'Optimistic locking version — tăng mỗi lần cập nhật';
-- ... (mỗi cột phải có COMMENT)

-- 4. Indexes cho truy vấn thường xuyên
CREATE INDEX idx_orders_tenant_branch ON orders(tenant_id, branch_id);
CREATE INDEX idx_orders_branch_status ON orders(branch_id, status);
CREATE INDEX idx_orders_branch_created ON orders(branch_id, created_at DESC);
CREATE UNIQUE INDEX uidx_orders_order_number ON orders(tenant_id, order_number);
```

### 5.3 Bảng Outbox chuẩn (Mỗi service đều có)

```sql
-- V2__create_outbox_and_idempotency.sql
CREATE TABLE outbox_events (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  TEXT            NOT NULL,
    aggregate_id    UUID            NOT NULL,
    event_type      TEXT            NOT NULL,
    event_version   TEXT            NOT NULL DEFAULT 'v1',
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    branch_id       UUID            NOT NULL,
    correlation_id  UUID            NOT NULL,
    status          TEXT            NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

-- Bảng idempotency
CREATE TABLE idempotency_records (
    idempotency_key TEXT            PRIMARY KEY,
    payload_hash    TEXT            NOT NULL,
    response_body   JSONB,
    http_status     INTEGER         NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);
```

### 5.4 Nguyên tắc An toàn

| Quy tắc | Mô tả |
|---|---|
| **Forward-only** | Không bao giờ dùng `flyway:clean` trên staging/production |
| **Backward compatible** | Thêm cột mới phải có DEFAULT hoặc NULLABLE; không rename/drop cột trực tiếp |
| **Expand-Migrate-Contract** | (1) Thêm cột mới → (2) Migrate dữ liệu → (3) Xóa cột cũ ở migration sau |
| **No data migration in DDL** | Tách data migration (DML) thành file riêng với version riêng |
| **Idempotent CREATE** | Sử dụng `IF NOT EXISTS` cho CREATE TABLE/INDEX; `IF EXISTS` cho DROP |

---

## 6. Angular Frontend Shell Architecture

### 6.1 Cấu trúc Thư mục

```
frontend/pos-app/
├── angular.json
├── package.json
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.spec.json
│
├── src/
│   ├── index.html
│   ├── main.ts
│   ├── styles.scss                     # Global styles + CSS variables
│   │
│   ├── app/
│   │   ├── app.component.ts            # Root component (standalone)
│   │   ├── app.config.ts               # Application providers
│   │   ├── app.routes.ts               # Top-level route definitions
│   │   │
│   │   ├── core/                       # ══ SINGLETON SERVICES ══
│   │   │   ├── auth/
│   │   │   │   ├── auth.service.ts         # Cognito OIDC integration
│   │   │   │   ├── auth.guard.ts           # Route guard
│   │   │   │   ├── auth.interceptor.ts     # JWT token injection
│   │   │   │   └── auth.model.ts           # Token/user types
│   │   │   ├── api/
│   │   │   │   ├── api.service.ts          # Base HTTP client wrapper
│   │   │   │   ├── error.interceptor.ts    # Global error handling
│   │   │   │   ├── correlation.interceptor.ts  # Correlation-ID injection
│   │   │   │   └── api.model.ts            # SuccessEnvelope, ErrorEnvelope
│   │   │   ├── tenant/
│   │   │   │   ├── tenant.service.ts       # Active tenant/branch state
│   │   │   │   └── branch.resolver.ts      # Branch selection resolver
│   │   │   └── layout/
│   │   │       ├── main-layout.component.ts    # POS layout shell
│   │   │       ├── sidebar.component.ts
│   │   │       └── header.component.ts
│   │   │
│   │   ├── shared/                     # ══ REUSABLE COMPONENTS ══
│   │   │   ├── components/
│   │   │   │   ├── data-table/             # Generic table with pagination
│   │   │   │   ├── confirm-dialog/
│   │   │   │   ├── toast-notification/
│   │   │   │   ├── loading-spinner/
│   │   │   │   └── empty-state/
│   │   │   ├── pipes/
│   │   │   │   ├── currency-vnd.pipe.ts    # Format VND (integer → display)
│   │   │   │   ├── phone-mask.pipe.ts      # Mask phone numbers
│   │   │   │   └── relative-time.pipe.ts
│   │   │   └── directives/
│   │   │       ├── role-check.directive.ts # *posHasRole="MANAGER"
│   │   │       └── branch-scope.directive.ts
│   │   │
│   │   └── features/                   # ══ LAZY-LOADED FEATURES ══
│   │       ├── auth/                       # Login, branch selection
│   │       │   ├── login.component.ts
│   │       │   └── branch-select.component.ts
│   │       ├── pos/                        # POS terminal (cashier view)
│   │       │   ├── pos.routes.ts
│   │       │   ├── catalog-grid/
│   │       │   ├── cart/
│   │       │   ├── checkout/
│   │       │   ├── recommendation-panel/
│   │       │   └── customer-lookup/
│   │       ├── shift/                      # Shift management
│   │       │   ├── open-shift/
│   │       │   └── close-shift/
│   │       ├── catalog-admin/              # Catalog management (owner/manager)
│   │       ├── reporting/                  # Dashboard & reports
│   │       ├── audit/                      # Audit log viewer
│   │       └── admin/                      # Tenant/branch/user management
│   │
│   ├── environments/
│   │   ├── environment.ts              # Development defaults
│   │   └── environment.prod.ts         # Production overrides
│   │
│   └── assets/
│       ├── icons/
│       └── i18n/                       # Internationalization files
│           └── vi.json
```

### 6.2 Quy tắc Frontend

| Quy tắc | Mô tả |
|---|---|
| **Standalone Components** | Tất cả components là standalone; không sử dụng NgModules |
| **Signals-first** | Ưu tiên Angular Signals cho reactive state thay vì RxJS BehaviorSubject |
| **Feature-based routing** | Mỗi feature folder có `xxx.routes.ts` riêng, lazy-loaded |
| **API envelope awareness** | Frontend phải parse `SuccessEnvelope` và `ErrorEnvelope` chuẩn |
| **Correlation-ID propagation** | `correlation.interceptor.ts` gắn `X-Correlation-Id` vào mọi request |
| **Branch-scope header** | `X-Active-Branch-Id` gắn từ `TenantService.activeBranch()` |
| **Currency display** | Luôn dùng `currencyVnd` pipe — KHÔNG format tiền bằng code ad-hoc |
| **PII masking** | Số điện thoại hiển thị qua `phoneMask` pipe — KHÔNG hiển thị raw |
| **Eventual consistency UI** | Báo cáo hiển thị `lastUpdatedAt` và badge "Đang đồng bộ..." khi cần |

---

## 7. CI/CD Pipeline Design (GitHub Actions)

### 7.1 Pipeline Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    GitHub Actions CI                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  PR / Push to main:                                      │
│  ┌──────────────────────────────────────────────────┐   │
│  │  1. Detect Changed Paths (affected services)     │   │
│  ├──────────────────────────────────────────────────┤   │
│  │  2. Lint & Format Check (parallel)               │   │
│  │     ├── Java: Checkstyle + SpotBugs              │   │
│  │     ├── Angular: ESLint + Prettier               │   │
│  │     └── Terraform: fmt + validate                │   │
│  ├──────────────────────────────────────────────────┤   │
│  │  3. Build & Test (matrix: affected services)     │   │
│  │     ├── Unit Tests                               │   │
│  │     ├── Integration Tests (Testcontainers)       │   │
│  │     └── Contract Tests                           │   │
│  ├──────────────────────────────────────────────────┤   │
│  │  4. Security Scan                                │   │
│  │     ├── SAST (Semgrep)                           │   │
│  │     ├── Dependency Audit (Gradle, npm)           │   │
│  │     └── Secret Detection (Gitleaks)              │   │
│  ├──────────────────────────────────────────────────┤   │
│  │  5. Build Docker Images (affected services)      │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  Merge to main:                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  6. Push Images to ECR                           │   │
│  │  7. Terraform Plan (staging)                     │   │
│  │  8. Deploy to Staging (manual approval gate)     │   │
│  │  9. E2E Tests on Staging                         │   │
│  │ 10. Deploy to Production (manual approval gate)  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 7.2 Affected Service Detection

```yaml
# .github/workflows/_reusable/detect-affected.yml
# Sử dụng dorny/paths-filter để xác định services bị ảnh hưởng
# Tránh build lại toàn bộ 12 services khi chỉ sửa 1 service

paths-filter:
  platform-shared: platform/**
  identity-access: services/identity-access/** | platform/**
  catalog: services/catalog/** | platform/**
  order: services/order/** | platform/**
  payment: services/payment/** | platform/**
  inventory: services/inventory/** | platform/**
  loyalty: services/loyalty/** | platform/**
  shift: services/shift/** | platform/**
  recommendation: services/recommendation/** | platform/**
  reporting: services/reporting/** | platform/**
  audit: services/audit/** | platform/**
  ai-assistant: services/ai-assistant/** | platform/**
  integration: services/integration/** | platform/**
  frontend: frontend/**
  infra: infra/terraform/**
```

### 7.3 Quy tắc CI/CD

| Quy tắc | Mô tả |
|---|---|
| **Affected-only builds** | Chỉ build/test services bị ảnh hưởng bởi thay đổi; thay đổi `platform/` triggers tất cả services |
| **Testcontainers cho Integration** | Integration tests chạy PostgreSQL via Testcontainers — không dùng H2 |
| **Security gate** | SAST + Secret Detection phải pass trước khi merge |
| **Manual approval** | Deploy staging/production yêu cầu manual approval |
| **Image tagging** | `<service>:<git-sha-short>` cho traceability |
| **No skip tests** | Pipeline không có option skip tests trên PR |

---

## 8. Coding Standards & Conventions

### 8.1 Java Coding Standards

#### Naming Conventions

| Element | Convention | Ví dụ |
|---|---|---|
| Package | `com.pos.<service>.<layer>.<sublayer>` | `com.pos.order.domain.model` |
| Class | PascalCase, danh từ | `OrderAggregate`, `PaymentStatus` |
| Interface | PascalCase, danh từ/tính từ | `OrderRepository`, `Auditable` |
| Method | camelCase, động từ | `createOrder()`, `findByBranch()` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_CURRENCY` |
| Test method | snake_case mô tả kịch bản | `should_return_404_when_order_not_found()` |
| Config property | kebab-case (Spring standard) | `pos.payment.vnpay.timeout-ms` |

#### Code Rules

| Quy tắc | Chi tiết |
|---|---|
| **No field injection** | Sử dụng constructor injection; cấm `@Autowired` trên field |
| **Records cho DTOs** | Request/Response/Command/Event dùng Java `record` |
| **Immutable domain** | Domain objects immutable; mutation trả về instance mới |
| **No null returns** | Trả `Optional<T>` thay vì null; throw exception cho lỗi nghiệp vụ |
| **Validated inputs** | `@Valid` + Jakarta Bean Validation trên Controller; domain validation riêng trong domain layer |
| **No checked exceptions** | Domain exceptions extends `RuntimeException`; xử lý bởi `@ControllerAdvice` |
| **Parameterized queries** | Cấm string concatenation trong SQL; sử dụng JPA named parameters hoặc Spring JDBC named params |
| **Structured logging** | SLF4J + Logback; log dưới dạng JSON với MDC chứa correlationId, tenantId, branchId |
| **Max line length** | 120 characters |
| **Max method length** | ~30 lines; extract nếu dài hơn |
| **Max nesting depth** | 3 levels; sử dụng guard clauses |

### 8.2 Testing Standards

```
                          Test Pyramid

           ┌─────────────────────────────────┐
           │        E2E / Contract           │  Ít nhất — chạy lâu
           │      (Playwright, RestAssured)   │
           ├─────────────────────────────────┤
           │      Integration Tests           │  Vừa phải
           │  (Testcontainers + Spring Boot)  │
           ├─────────────────────────────────┤
           │         Unit Tests               │  Nhiều nhất — chạy nhanh
           │   (JUnit 5 + AssertJ + Mockito)  │
           └─────────────────────────────────┘
```

| Tầng | Scope | Tools | Convention |
|---|---|---|---|
| **Unit** | Domain logic, policies, value objects | JUnit 5, AssertJ, Mockito | `*Test.java` — no Spring context |
| **Integration** | Repository, messaging, external adapters | Testcontainers, `@DataJpaTest`, `@WebMvcTest` | `*IntegrationTest.java` |
| **Contract** | API request/response schemas, event schemas | Spring Cloud Contract hoặc Pact | `*ContractTest.java` |
| **E2E** | Full flow xuyên services | RestAssured, Docker Compose test profile | `*E2ETest.java` |
| **Security** | Negative authorization, IDOR | Custom test fixtures | `*SecurityTest.java` |
| **Load** | Performance under load | k6 scripts | `tools/k6/` |

**Quy tắc test:**
- Test method name: `should_<expected>_when_<condition>()`
- Cấu trúc AAA: Arrange → Act → Assert
- Mỗi test chỉ assert 1 logical outcome
- Không skip tests — xóa hoặc fix
- Không logic (if/for/try-catch) trong test body
- Cover boundaries: null, empty, zero, max, concurrent, error paths

### 8.3 Angular Coding Standards

| Element | Convention |
|---|---|
| Component selector | `pos-<feature>-<name>` (e.g., `pos-cart-item`) |
| File naming | `kebab-case` (e.g., `cart-item.component.ts`) |
| Service naming | `<name>.service.ts` (e.g., `order.service.ts`) |
| State management | Angular Signals (signals-first) |
| HTTP calls | Wrapped in service methods, never directly in components |
| Error handling | Global `ErrorInterceptor` catches all API errors |
| Testing | Jasmine + Karma; component tests for UI; service tests for logic |

---

## 9. Terraform / IaC Scaffolding Structure

### 9.1 Module Structure

```
infra/terraform/
├── modules/                        # Reusable Terraform modules
│   ├── networking/
│   │   ├── main.tf                 # VPC, subnets, NAT gateway
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── security-groups.tf
│   │
│   ├── ecs/
│   │   ├── main.tf                 # ECS Fargate cluster
│   │   ├── service.tf              # Service definition (reusable per service)
│   │   ├── task-definition.tf
│   │   ├── auto-scaling.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── rds/
│   │   ├── main.tf                 # Aurora PostgreSQL cluster
│   │   ├── databases.tf            # Per-service database creation
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── cognito/
│   │   ├── main.tf                 # User pool, app client
│   │   ├── resource-server.tf      # OAuth2 scopes
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── eventbridge/
│   │   ├── main.tf                 # Event bus
│   │   ├── rules.tf                # Routing rules per event type
│   │   ├── sqs-targets.tf          # SQS queues + DLQ per consumer
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── step-functions/
│   │   ├── main.tf                 # Checkout saga state machine
│   │   ├── definition.json         # ASL definition
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   ├── secrets/
│   │   ├── main.tf                 # Secrets Manager entries
│   │   ├── variables.tf
│   │   └── outputs.tf
│   │
│   └── observability/
│       ├── main.tf                 # CloudWatch log groups, X-Ray
│       ├── dashboards.tf           # CloudWatch dashboards
│       ├── alarms.tf               # CloudWatch alarms
│       ├── variables.tf
│       └── outputs.tf
│
├── environments/
│   ├── sandbox/
│   │   ├── main.tf                 # Module composition for sandbox
│   │   ├── variables.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf              # S3 backend for state
│   ├── staging/
│   │   └── ...
│   └── production/
│       └── ...
│
└── README.md                       # IaC usage guide
```

### 9.2 Quy tắc Terraform

| Quy tắc | Mô tả |
|---|---|
| **Module-first** | Tất cả resources được tạo qua modules, không inline trong environment |
| **Remote state** | S3 backend + DynamoDB locking cho mọi environment |
| **No hardcoded values** | Tất cả config qua variables; secrets qua `terraform.tfvars` (gitignored) |
| **Environment parity** | Sandbox/staging/production dùng cùng modules, khác ở biến |
| **Plan before apply** | CI luôn chạy `terraform plan` trước; `apply` cần manual approval |
| **Resource naming** | `pos-<env>-<service>-<resource>` (e.g., `pos-sandbox-order-ecs-service`) |
| **Tagging** | Mọi resource có tags: `Project`, `Environment`, `Service`, `ManagedBy=terraform` |

---

## 10. Observability Setup

### 10.1 Kiến trúc Observability

```
┌─────────────────────────────────────────────────────────┐
│                   Application Layer                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Spring Boot Service                              │  │
│  │  ├── OpenTelemetry Java Agent (auto-instrument)   │  │
│  │  ├── SLF4J + Logback (JSON structured logging)    │  │
│  │  ├── Micrometer (metrics: JVM, HTTP, custom)      │  │
│  │  └── MDC: correlationId, tenantId, branchId       │  │
│  └───────────────────┬───────────────────────────────┘  │
│                      │ OTLP gRPC                        │
│  ┌───────────────────▼───────────────────────────────┐  │
│  │  OpenTelemetry Collector                          │  │
│  │  ├── Receivers: OTLP                              │  │
│  │  ├── Processors: batch, memory_limiter            │  │
│  │  └── Exporters:                                   │  │
│  │       ├── AWS X-Ray (traces)                      │  │
│  │       ├── CloudWatch (metrics)                    │  │
│  │       ├── CloudWatch Logs (logs)                  │  │
│  │       └── Jaeger (local dev traces)               │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 10.2 Structured Logging Format

```json
{
  "timestamp": "2026-10-12T07:15:01.234Z",
  "level": "INFO",
  "logger": "com.pos.order.application.service.OrderCommandService",
  "message": "Order created successfully",
  "service": "order-service",
  "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
  "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
  "userId": "0192b2d0-7f1d-7abc-8d2c-123456789005",
  "traceId": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
  "spanId": "1a2b3c4d5e6f7a8b",
  "data": {
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789099",
    "totalMinor": 121000
  }
}
```

### 10.3 Correlation ID Propagation

```
Client Request
  │  X-Correlation-Id: cor_xxx (hoặc auto-generated)
  ▼
API Gateway → gắn vào header nếu chưa có
  │
  ▼
Service A (Order)
  │  MDC.put("correlationId", cor_xxx)
  │  → Log: {"correlationId": "cor_xxx", ...}
  │  → Outbox Event: {"correlationid": "cor_xxx", ...}
  │  → REST call to Service B: header X-Correlation-Id: cor_xxx
  │
  ▼
Service B (Inventory)
  │  MDC.put("correlationId", cor_xxx)  ← extracted from header
  │  → Log: {"correlationId": "cor_xxx", ...}
  │
  ▼
EventBridge Event
  │  {"correlationid": "cor_xxx", ...}  ← embedded in CloudEvents envelope
  │
  ▼
Service C (Reporting)
     MDC.put("correlationId", cor_xxx)  ← extracted from event
     → Log: {"correlationId": "cor_xxx", ...}
```

### 10.4 Metrics chuẩn

| Metric | Type | Labels | Mục đích |
|---|---|---|---|
| `pos_http_requests_total` | Counter | `service`, `method`, `path`, `status` | Tổng số request |
| `pos_http_request_duration_seconds` | Histogram | `service`, `method`, `path` | Latency p50/p95/p99 |
| `pos_order_created_total` | Counter | `service`, `branch_id`, `service_mode` | Số đơn hàng tạo mới |
| `pos_checkout_duration_seconds` | Histogram | `service`, `payment_method` | Thời gian checkout |
| `pos_outbox_events_pending` | Gauge | `service`, `event_type` | Số event chưa publish |
| `pos_idempotency_hits_total` | Counter | `service`, `hit_type` | Cache hit/miss |
| `pos_saga_executions_total` | Counter | `outcome` (success/compensated/failed) | Kết quả Saga |

### 10.5 Alerting Rules

| Alert | Condition | Severity | Action |
|---|---|---|---|
| **HighErrorRate** | HTTP 5xx > 5% trong 5 phút | Critical | Page on-call |
| **SagaCompensationSpike** | Compensation rate > 10% trong 15 phút | Warning | Investigate payment provider |
| **OutboxBacklog** | Pending events > 100 trong 10 phút | Warning | Check EventBridge connectivity |
| **DLQMessages** | DLQ message count > 0 | Warning | Investigate and replay |
| **HighLatency** | p95 > 1s cho API đồng bộ | Warning | Check database/external deps |
| **DatabaseConnectionPool** | Active connections > 80% pool | Warning | Scale or optimize queries |

---

## 11. Event Infrastructure Design

### 11.1 Kiến trúc Adapter Pattern (Local ↔ AWS)

```
┌────────────────────────────────────────────────────┐
│                 Service Code                        │
│  ┌──────────────────────────────────────────────┐  │
│  │        EventPublisher (Port/Interface)        │  │
│  │   void publish(DomainEventEnvelope<?> event)  │  │
│  └───────────┬─────────────────────┬────────────┘  │
│              │                     │                │
│   ┌──────────▼──────────┐  ┌──────▼──────────┐    │
│   │  LocalBrokerAdapter │  │  AwsEventBridge  │    │
│   │   (Docker/Dev)      │  │   Adapter (Prod) │    │
│   │                     │  │                   │    │
│   │  LocalStack SQS     │  │  EventBridge →   │    │
│   │  + EventBridge      │  │  SQS → Consumer  │    │
│   └─────────────────────┘  └───────────────────┘    │
│         ↑ @Profile("local")   ↑ @Profile("aws")    │
└────────────────────────────────────────────────────┘
```

### 11.2 Outbox Publisher Flow

```
1. Service ghi domain data + outbox record trong 1 PostgreSQL transaction
2. OutboxPublisher (scheduled task, mỗi 500ms):
   a. SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT 50
   b. Publish mỗi event lên EventBridge (local hoặc AWS)
   c. UPDATE outbox_events SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?
   d. Nếu publish fail → tăng retry_count, áp dụng exponential backoff
   e. Nếu retry_count > MAX_RETRIES → status = 'DEAD_LETTER', alert
```

### 11.3 Consumer Pattern

```java
// Mọi consumer PHẢI theo pattern này:
@SqsListener("${pos.sqs.queue.order-paid}")
public void handleOrderPaid(DomainEventEnvelope<OrderPaidPayload> envelope) {
    // 1. Idempotency check
    if (inboxStore.exists(envelope.id())) {
        log.info("Duplicate event ignored: {}", envelope.id());
        return;
    }

    // 2. Process event
    loyaltyService.accruePoints(envelope.data());

    // 3. Record in inbox (same transaction as step 2)
    inboxStore.save(envelope.id(), envelope.type());
}
```

### 11.4 SQS Queue Naming Convention

```
pos-<env>-<consumer-service>-<event-type>-queue
pos-<env>-<consumer-service>-<event-type>-dlq

Ví dụ:
pos-sandbox-loyalty-order-paid-queue
pos-sandbox-loyalty-order-paid-dlq
pos-sandbox-reporting-order-created-queue
pos-sandbox-reporting-order-created-dlq
```

### 11.5 EventBridge Rule Convention

```
pos-<env>-<event-type>-to-<consumer>

Ví dụ:
pos-sandbox-order-paid-to-loyalty
pos-sandbox-order-paid-to-reporting
pos-sandbox-order-paid-to-audit
```

---

## 12. Security Baseline

### 12.1 Authentication Flow

```
┌──────────┐        ┌──────────┐         ┌──────────────┐
│ Angular  │──(1)──▶│ Cognito  │──(2)──▶ │ JWT Token    │
│ POS App  │        │ OIDC     │         │ (id + access)│
└──────────┘        └──────────┘         └──────┬───────┘
                                                │
     ┌──────────────────────────────────────────┘
     │  (3) Authorization: Bearer <JWT>
     ▼
┌────────────┐     ┌────────────────────┐
│ API Gateway│──▶  │ Cognito Authorizer │  (4) Verify signature, iss, aud, exp
│            │     └────────┬───────────┘
└────────────┘              │
     │                      │  (5) Forward to service
     ▼
┌────────────────────────────────────────────────────────┐
│ Spring Boot Service                                     │
│  (6) Re-validate JWT (signature, iss, aud, exp)        │
│  (7) Extract sub, tenant_memberships from JWT          │
│  (8) Call Identity Service → resolve actual scope      │
│       (tenantId, branchIds, roles)                     │
│  (9) Build TenantContext                                │
│  (10) Apply scope to all repository queries             │
│       WHERE tenant_id = :tenantId                      │
│       AND branch_id = :branchId                         │
└────────────────────────────────────────────────────────┘
```

### 12.2 Authorization Checklist (Mỗi Service PHẢI thực hiện)

| # | Kiểm tra | Tầng | Chi tiết |
|---|---|---|---|
| 1 | JWT Signature + Expiry | Filter/Interceptor | Re-validate chữ ký RSA, `iss`, `aud`, `exp`, `nbf` |
| 2 | Tenant Context Resolution | Security Filter | Gọi Identity Service lấy scope thực tế từ DB |
| 3 | Role-Based Access | Controller/Use Case | `@PreAuthorize` hoặc manual check `tenantContext.hasRole()` |
| 4 | Branch Scope | Controller | Verify `X-Active-Branch-Id` nằm trong `allowedBranchIds` |
| 5 | Resource Ownership | Repository | `WHERE tenant_id = :tid AND branch_id = :bid` trên MỌI query |
| 6 | IDOR Prevention | Service Layer | Verify resource thuộc tenant/branch trước khi trả về |
| 7 | Sensitive Action Audit | Service Layer | Log audit event cho: hủy món, giảm giá, mở két, thay đổi quyền |

### 12.3 Secrets Management

| Secret | Storage | Rotation |
|---|---|---|
| Database passwords | AWS Secrets Manager (prod) / Docker env (local) | 90 days |
| Cognito client secret | AWS Secrets Manager | Manual |
| VNPAY HMAC secret key | AWS Secrets Manager | Per provider policy |
| JWT signing keys | Cognito managed (RSA key pair) | Automatic |
| Service-to-service mTLS certs | AWS Certificate Manager | Automatic |

**Quy tắc cứng:**
- **KHÔNG BAO GIỜ** commit secret vào source code hoặc config files
- Startup validation: nếu thiếu required secret → fail fast với log rõ ràng (không log giá trị secret)
- Log KHÔNG chứa: password, token, checksum, PII (số điện thoại raw), thông tin thẻ

### 12.4 Input Validation Layers

```
Client Input
     │
     ▼
┌─────────────────────────────────┐
│  Layer 1: API Gateway           │  Rate limiting, request size limit
├─────────────────────────────────┤
│  Layer 2: Controller            │  Jakarta Bean Validation (@Valid)
│           (@Size, @NotNull,     │  Request DTO schema validation
│            @Pattern)            │
├─────────────────────────────────┤
│  Layer 3: Application Service   │  Business rule validation
│           (Domain policies)     │  Cross-field validation
├─────────────────────────────────┤
│  Layer 4: Repository            │  Parameterized queries (SQL injection prevention)
│           (JPA/JDBC)            │  Tenant/branch scope enforcement
├─────────────────────────────────┤
│  Layer 5: Database              │  CHECK constraints, NOT NULL, UNIQUE
│           (PostgreSQL)          │  Foreign key constraints (within same DB)
└─────────────────────────────────┘
```

### 12.5 Error Response Security

| Quy tắc | Mô tả |
|---|---|
| **Không trả stack trace** | Sản xuất: `internal_error` + message chung; log chi tiết phía server |
| **Không tiết lộ existence** | `not_found` cho cả "không tồn tại" và "không có quyền" (chống enumeration) |
| **Không trả raw DB error** | Catch `DataAccessException` → map sang application error |
| **Không trả tenant context** | Error response không chứa tenantId, branchId, userId |
| **Rate limit response** | `429` với `Retry-After` header; không tiết lộ rate limit threshold |

---

## Gradle Build Configuration

### Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
spring-boot = "3.4.1"
spring-dependency-management = "1.1.7"
java = "21"
postgresql = "42.7.4"
flyway = "10.21.0"
testcontainers = "1.20.4"
opentelemetry-agent = "2.10.0"
opentelemetry-sdk = "1.44.1"
assertj = "3.26.3"
archunit = "1.3.0"
awssdk = "2.29.14"
uuid-creator = "6.0.0"
jackson = "2.18.2"

[libraries]
# Spring Boot
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-oauth2-resource-server = { module = "org.springframework.boot:spring-boot-starter-oauth2-resource-server" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }

# Database
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }

# Testing
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
testcontainers-localstack = { module = "org.testcontainers:localstack", version.ref = "testcontainers" }
assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj" }
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

# AWS SDK
awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk" }
awssdk-sqs = { module = "software.amazon.awssdk:sqs" }
awssdk-eventbridge = { module = "software.amazon.awssdk:eventbridge" }
awssdk-secretsmanager = { module = "software.amazon.awssdk:secretsmanager" }
awssdk-sfn = { module = "software.amazon.awssdk:sfn" }

# Observability
micrometer-registry-cloudwatch = { module = "io.micrometer:micrometer-registry-cloudwatch2" }
opentelemetry-sdk = { module = "io.opentelemetry:opentelemetry-sdk", version.ref = "opentelemetry-sdk" }

# Utils
uuid-creator = { module = "com.github.f4b6a3:uuid-creator", version.ref = "uuid-creator" }
jackson-datatype-jsr310 = { module = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310", version.ref = "jackson" }

[bundles]
spring-boot-service = [
    "spring-boot-starter-web",
    "spring-boot-starter-data-jpa",
    "spring-boot-starter-security",
    "spring-boot-starter-oauth2-resource-server",
    "spring-boot-starter-actuator",
    "spring-boot-starter-validation",
]
database = ["postgresql", "flyway-core", "flyway-postgresql"]
testing = [
    "spring-boot-starter-test",
    "testcontainers-postgresql",
    "testcontainers-junit-jupiter",
    "assertj-core",
    "archunit-junit5",
]

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

### Root Build (`build.gradle.kts`)

```kotlin
// Quy ước chung cho tất cả subprojects
plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.pos"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
    }
}
```

### Settings (`settings.gradle.kts`)

```kotlin
rootProject.name = "pos-platform"

// Platform shared libraries
include("platform:shared-kernel")
include("platform:shared-test")
include("platform:event-contracts")

// Application services
include("services:identity-access")
include("services:catalog")
include("services:order")
include("services:payment")
include("services:inventory")
include("services:loyalty")
include("services:shift")
include("services:recommendation")
include("services:reporting")
include("services:audit")
include("services:ai-assistant")
include("services:integration")
```

### Service Build Template (`services/<name>/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Shared kernel
    implementation(project(":platform:shared-kernel"))

    // Spring Boot service bundle
    implementation(libs.bundles.spring.boot.service)

    // Database
    implementation(libs.bundles.database)

    // AWS SDK (managed by BOM)
    implementation(platform(libs.awssdk.bom))
    implementation(libs.awssdk.sqs)
    implementation(libs.awssdk.eventbridge)
    implementation(libs.awssdk.secretsmanager)

    // Utils
    implementation(libs.uuid.creator)
    implementation(libs.jackson.datatype.jsr310)

    // Observability
    runtimeOnly(libs.micrometer.registry.cloudwatch)

    // Testing
    testImplementation(project(":platform:shared-test"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.localstack)
}
```

---

## ArchUnit Architecture Tests

Mỗi service PHẢI có test kiểm tra quy tắc kiến trúc:

```java
@AnalyzeClasses(packages = "com.pos.order", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson.."
            )
            .as("Domain layer MUST NOT depend on Spring, JPA or Jackson");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_adapters =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule controllers_should_reside_in_adapter_inbound =
        classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..adapter.inbound.rest..");

    @ArchTest
    static final ArchRule jpa_entities_should_reside_in_adapter_outbound =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..adapter.outbound.persistence.entity..");

    @ArchTest
    static final ArchRule services_should_not_cross_import =
        noClasses()
            .that().resideInAPackage("com.pos.order..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.pos.payment..",
                "com.pos.inventory..",
                "com.pos.catalog..",
                "com.pos.loyalty..",
                "com.pos.shift.."
            )
            .as("Services MUST NOT cross-import other service packages");
}
```

---

## Tóm tắt Checklist Triển khai Phase 0

Dựa trên ADR này, Phase 0 (Foundation & Spikes) cần hoàn thành:

- [ ] Khởi tạo cấu trúc monorepo theo section 1
- [ ] Tạo `shared-kernel` với error/success envelope, correlation, idempotency, event envelope, security context, money, UUID v7
- [ ] Tạo `shared-test` với Testcontainers config, mock JWT, test fixtures
- [ ] Tạo `event-contracts` với JSON Schema cho các events P0
- [ ] Cấu hình Gradle version catalog và build scripts
- [ ] Tạo service template (1 service mẫu hoàn chỉnh với hexagonal structure)
- [ ] Thiết lập Docker Compose với PostgreSQL (12 databases) + LocalStack + OTEL + Jaeger
- [ ] Tạo Flyway baseline migrations cho service mẫu
- [ ] Khởi tạo Angular 18+ shell với core services (auth, api, tenant)
- [ ] Thiết lập GitHub Actions CI pipeline (lint + build + test)
- [ ] Khởi tạo Terraform modules cơ bản (networking, cognito, secrets)
- [ ] Cấu hình structured logging + correlation ID propagation
- [ ] Tạo ArchUnit tests cho dependency rules
- [ ] Spike: JWT validation từ Cognito
- [ ] Spike: VNPAY HMAC signature verification
- [ ] Spike: Step Functions Saga (1 flow thành công + 1 flow bồi hoàn)
- [ ] Viết README hướng dẫn setup và chạy local

---

## Kết luận

ADR này là **single source of truth** cho toàn bộ quyết định kỹ thuật nền tảng. Mọi service mới phải clone từ template, tuân thủ naming conventions, dependency rules và testing standards đã quy định. Sai lệch yêu cầu ADR bổ sung.

**Verdict: SẴN SÀNG CHO IMPLEMENTATION PHASE 0**
