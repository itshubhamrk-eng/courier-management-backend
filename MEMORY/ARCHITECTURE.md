# ARCHITECTURE

## 1. Layering (Clean Architecture, per feature)

Each feature package is self-contained and internally layered:

```
com.courier.modules.shipment
├── api                 REST controllers, request/response DTOs, mappers
│   ├── ShipmentController
│   └── dto/CreateShipmentRequest, ShipmentResponse
├── application         use cases, orchestration, @Transactional, @PreAuthorize
│   └── ShipmentService
├── domain              entities, value objects, domain events, repository *interfaces*
│   ├── Shipment, ShipmentStatus, Awb
│   └── ShipmentRepository
└── infrastructure      Spring Data implementations, external clients, adapters
    └── JpaShipmentRepository
```

**Dependency rule:** `api -> application -> domain`, and `infrastructure -> domain`.
`domain` depends on nothing but the JDK and JPA annotations. A controller never
touches a repository directly; a repository never returns a DTO.

**Cross-feature rule:** a feature may depend on another feature's `application`
service interface, never on its `domain` entities or repositories. `CompanyDashboardService`
pays for this openly: it counts subscription plans through `SubscriptionPlanService.search`
with a one-row page rather than a `count(*)` on that module's repository. If two features
need the same table, that is a signal the boundary is wrong — discuss before coding.

## 2. Request Pipeline

```
HTTP request
  │
  ├─ RequestIdFilter          generate/propagate X-Request-Id, push to MDC
  ├─ JwtAuthenticationFilter  verify signature+expiry -> AuthenticatedUser in SecurityContext
  ├─ CompanyResolutionFilter  resolve company -> CompanyContext (ThreadLocal)
  ├─ Spring Security          authorize (URL rules + @PreAuthorize)
  │
  ├─ Controller               validate DTO (@Valid), no business logic
  ├─ Service                  @Transactional, use case, audit event
  ├─ Repository               Hibernate applies companyFilter + @SQLRestriction
  └─ MySQL
  │
  └─ GlobalExceptionHandler   maps exceptions -> ApiResponse error envelope
  └─ finally: CompanyContext.clear(), MDC.clear()  <- MUST happen, thread reuse
```

## 3. Company Isolation Mechanics

See `MEMORY/adr/ADR-001.md` for the decision. **There is no separate tenant concept: a
company is the owner of every row it can see.**

1. **Storage** — every company-owned table has an owner column, `BINARY(16) NOT NULL`,
   indexed as the *leading* column of every composite index. It is still **named
   `tenant_id`** in SQL; the physical rename is a deferred migration, and until it lands
   new tables should be created with that name so the rename stays one migration. Java
   never says `tenant`.
2. **Entity** — such entities extend `CompanyOwnedEntity`, which maps `companyId` to
   `@Column(name = "tenant_id")` and declares `@FilterDef(name="companyFilter")`.
   **Each concrete entity must also repeat
   `@Filter(name="companyFilter", condition="tenant_id = :companyId")` on itself** —
   Hibernate does not reliably inherit `@Filter` from a `@MappedSuperclass`, and a
   silently unfiltered entity is a data leak.
3. **Write path** — `CompanyEntityListener` stamps `companyId` from `CompanyContext` in
   `@PrePersist`. Application code never sets it by hand.
4. **Read path** — `CompanyFilterAspect` enables the Hibernate filter on the session
   backing each Spring Data repository call, using `CompanyContext.getCompanyId()`.
   It is ordered `LOWEST_PRECEDENCE` so it runs inside the transaction interceptor
   and therefore touches the same session the query will use.
5. **Escape hatch** — platform-level queries (the plan catalogue, the company list) use
   entities that do *not* extend `CompanyOwnedEntity`. There is no "disable the filter"
   API exposed to feature code. Code that must act as another company uses
   `CompanyContext.runAs(...)`, which restores the previous binding afterwards.
6. **Global rows** — the six geography master lists are company-owned *entities* whose rows
   all belong to one reserved id, `GlobalMasters.PLATFORM_COMPANY_ID`. The filter still
   applies, which is the point: forgetting to bind that id returns nothing, not
   everything. See ADR-001 and decision 51.

**Two known holes in the automatic enforcement**, both documented on
`CompanyFilterAspect`:

- Native queries bypass Hibernate filters entirely, so `nativeQuery = true` is
  banned in company-owned repositories.
- `EntityManager.find()` by primary key is not filtered, so prefer derived queries
  over `findById` for company-owned entities.

**Threat model:** the company id comes from a *signed* JWT claim (`cid`; `tid` is still
read for tokens minted before the rename). A client cannot choose its own company.
`X-Company-ID` is read only when the principal holds `PLATFORM_ADMIN`, and that
impersonation is always audit-logged. It is deliberately **not** honoured for
`SUPER_ADMIN`.

## 4. Persistence Conventions

| Concern | Convention |
|---|---|
| PK | `UUID id` -> `BINARY(16)`, generated in `BaseEntity` via time-ordered UUID |
| Naming | `snake_case` tables/columns, plural table names |
| Timestamps | `created_at`, `updated_at` — `TIMESTAMP(6)`, UTC, set by JPA auditing |
| Actor | `created_by`, `updated_by` — UUID of acting user, null for system |
| Soft delete | `deleted BOOLEAN`, `deleted_at`, `deleted_by`; `@SQLRestriction("deleted = false")` |
| Concurrency | `@Version Long version` on every aggregate root |
| Money | `DECIMAL(19,4)`, never `double` |
| Enums | `@Enumerated(STRING)`, stored as `VARCHAR`, never ordinal |
| FKs | Real FK constraints; `ON DELETE RESTRICT` (soft delete means rows persist) |

**Unique constraints must include the owner column.** e.g. AWB is
`UNIQUE (tenant_id, awb_number)`, not `UNIQUE (awb_number)`. The one deliberate exception
is `wallet_transactions.payment_reference` (decision 38) — one merchant account serves the
whole platform, so a per-company key would let one company claim another's payment.

## 5. Security

- Stateless. `SessionCreationPolicy.STATELESS`, CSRF disabled (no cookies used).
- Access token: 15 min, HS256, claims `sub` (userId), `cid` (companyId), `roles`, `typ`.
  The pre-rename `tid` is still read and never written; delete that fallback once the
  longest refresh TTL has elapsed since the rename shipped.
- Refresh token: 7 days, opaque-ish JWT with `typ=refresh`, rotated on use,
  previous jti pushed to a Redis denylist until natural expiry.
- Signing key from `JWT_SECRET` env var, minimum 32 bytes, app fails fast if weak.
- Passwords: BCrypt strength 12.
- Authorization enforced at the **service** layer with `@PreAuthorize`; URL rules in
  `SecurityConfig` are a coarse first gate, not the source of truth.

## 6. Error Model

Uniform envelope, always:

```json
{
  "success": false,
  "message": "Shipment not found",
  "data": null,
  "errorCode": "RESOURCE_NOT_FOUND",
  "errors": [ { "field": "awbNumber", "message": "must not be blank" } ],
  "timestamp": "2026-07-22T10:15:30.123Z",
  "path": "/api/v1/shipments/AWB123",
  "requestId": "0f1c...":
}
```

Rules: never leak stack traces or SQL to the client; log at `WARN` for 4xx and
`ERROR` with stack trace for 5xx; always include `requestId` so a support ticket
maps to a log line.

## 7. Caching (Redis)

| Key pattern | TTL | Purpose |
|---|---|---|
| `auth:denylist:{jti}` | token TTL | Revoked refresh/access tokens |
| `auth:refresh:{userId}:{jti}` | 7 d | Active refresh tokens (for "log out everywhere") |
| `company:{companyId}:config` | 10 min | Hot company settings |
| `rate:{companyId}:{key}` | 1 min | Rate limiting counters |

**Every cache key is company-prefixed.** A cache without a company prefix is a
cross-company leak waiting to happen.

## 8. Migrations

- Flyway, `db/migration/V<n>__<snake_case>.sql`, forward-only.
- Never edit a merged migration — write a new one.
- `baseline-on-migrate: true` for existing envs, `validate-on-migrate: true` always.
- Hibernate `ddl-auto: validate` — Hibernate never creates schema.

## 9. Observability

- `X-Request-Id` in, echoed out, in MDC for every log line.
- Log pattern includes `requestId` and `companyId`.
- Actuator: `/actuator/health`, `/actuator/info`, `/actuator/metrics` (secured in prod).

## 10. Testing Strategy

| Level | Tool | What |
|---|---|---|
| Unit | JUnit 5 + Mockito | Domain rules, services with mocked repos |
| Slice | `@DataJpaTest` + Testcontainers MySQL | Repository queries, company filter, soft delete |
| Integration | `@SpringBootTest` + Testcontainers | Auth flow, end-to-end request pipeline |
| Isolation | dedicated suite | **Every company-owned repo needs a cross-company leak test** |
| Boundary | `SuperAdminBoundaryTest` | Asserts what a `SUPER_ADMIN` may *not* do, by reading the `@PreAuthorize` expressions |
