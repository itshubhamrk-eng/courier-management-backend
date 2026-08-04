# Module: subscription

**Status:** DONE and verified against MySQL 8.0.46 — Phase 2, Super Admin, v0.3.0.
**Amended 2026-07-29 (v0.12.0):** gained `BillingCycle`, and a company's *place* on a plan
is now managed through three endpoints on `modules/company` — see
`MEMORY/modules/company.md` §*Subscription: three acts, not three fields*.
**Package:** `com.courier.modules.subscription`
**Depends on:** `shared` only.
**Depended on by:** `modules/company` (a company is created against a plan), and later
every quota check in `company` and `shipment`.

## Purpose

Owns the `SubscriptionPlan` aggregate: the catalogue of what a company may use and
what it costs. Written only by `SUPER_ADMIN`. Nothing here creates or touches a
tenant — that is the next module.

## Critical property

`SubscriptionPlan` extends **`BaseEntity`, not `CompanyOwnedEntity`**. The catalogue is
platform-wide, shared by every tenant. Consequences:

- No `tenant_id` column, no Hibernate `@Filter`, no `CompanyFilterAspect` involvement.
- Unique keys are **global**, not `tenant_id`-prefixed. The prefix rule in
  `ARCHITECTURE.md` §4 applies to company-owned tables only.
- `findById` is safe here, unlike in `UserRepository`, because there is no tenant
  filter for a primary-key load to bypass.
- A `nativeQuery` is permitted for the same reason — the §3 ban exists because native
  SQL escapes the *tenant* filter.

## Access control

Guarded by **`SUPER_ADMIN`**, a new top tier added in this phase and deliberately
**not** a rename of `PLATFORM_ADMIN`:

| Role | Operates on |
|---|---|
| `SUPER_ADMIN` | The platform itself — pricing, quotas, the plan catalogue |
| `PLATFORM_ADMIN` | *On behalf of* companies — tenant lifecycle, `X-Company-ID` impersonation |

Enforcement, outermost to innermost:

1. `SecurityConfig` — URL rule `"/api/v1/subscription-plans/**"` requires the role.
   A coarse gate, so a future controller under the same path cannot ship unguarded.
2. `SubscriptionPlanServiceImpl` — **class-level `@PreAuthorize`**, the authoritative
   check. On the implementation rather than the interface so it holds whichever proxy
   strategy Spring picks. The role string is built by compile-time constant folding
   from `Roles.SUPER_ADMIN`, so a rename cannot leave a stale literal in SpEL.

Read endpoints are guarded too: pricing and quota structure is commercial information,
not a public catalogue.

`SUPER_ADMIN` was also added to `modules/auth`'s `Role` enum (`RoleTest` keeps the two
in sync) and counts as `isPlatformScoped()`, so a company admin can never grant it.
`CompanyResolutionFilter` treats a super admin as platform tier and leaves the request
tenant-unbound — and deliberately does **not** honour `X-Company-ID` for it, because
impersonation belongs to `PLATFORM_ADMIN` and widening it here would hand the role
tenant data access nobody asked for.

## Domain

```
SubscriptionPlan (aggregate root, platform-level)
├── id                  UUID
├── planCode            String    stable machine key, UPPERCASE, unique, IMMUTABLE
├── planName            String    unique (case-insensitive)
├── description         String    <= 500
├── planType            PlanType  TRIAL | BASIC | STANDARD | PREMIUM | ENTERPRISE
├── monthlyPrice        BigDecimal  DECIMAL(19,4), >= 0
├── yearlyPrice         BigDecimal  DECIMAL(19,4), >= 0
├── currency            String    ISO-4217, default INR
├── trialDays           Integer   >= 0
├── maxUsers            Integer   null = unlimited
├── maxBranches         Integer   null = unlimited
├── maxHubs             Integer   null = unlimited
├── maxCustomers        Integer   null = unlimited
├── maxDrivers          Integer   null = unlimited
├── maxVehicles         Integer   null = unlimited
├── maxDailyBookings    Integer   null = unlimited
├── maxMonthlyBookings  Integer   null = unlimited
├── storageLimitGb      Integer   gigabytes, null = unlimited
├── apiRateLimit        Integer   requests/minute, null = unlimited
├── featureFlags        Map<String,Object>  MySQL JSON column
├── active              boolean   is_active — assignable to NEW companies
└── displayOrder        Integer   ascending sort key
```

Plus the `BaseEntity` columns: `createdAt`/`createdBy`, `updatedAt`/`updatedBy`,
`deleted`/`deletedAt`/`deletedBy`, `version`.

> **Naming note.** The build request called these `createdDate`/`updatedDate`. The
> project's existing columns are `created_at`/`updated_at` and were not renamed —
> changing `BaseEntity` would rewrite every table in the schema.

### `null` means unlimited

Decided this phase, over a `-1` sentinel. A forgotten guard around a sentinel makes
`current < -1` evaluate as "over quota" and silently blocks everything; a forgotten
null check throws and is caught in test. **Every consumer must compare through
`SubscriptionPlan.withinLimit(limit, current)`.**

### Type invariants

Enforced in `SubscriptionPlan.applyTypeInvariants()`, called on every create and
update — in the entity, not the service, so no write path can bypass them:

| Rule | Behaviour |
|---|---|
| Prices are never negative | `422 BUSINESS_RULE_VIOLATION` |
| `TRIAL` cannot be priced | `422` if either price > 0 |
| `TRIAL` must grant >= 1 trial day | `422` |
| `ENTERPRISE` has unlimited quotas | Supplied quotas are **nulled**, not rejected |
| `planCode`, `currency` uppercased; `planName` trimmed | normalised on write |

`ENTERPRISE` quotas are normalised rather than rejected: an operator typing a number
for an uncapped tier is expressing intent the tier overrides, and a 422 would only
teach them to type zeroes.

### Uniqueness and soft delete

`plan_code` and `plan_name` are unique **including soft-deleted rows**. A retired
plan keeps its code reserved — reusing it would attach new pricing to an identifier
that old companies and invoices already point at.

Because `@SQLRestriction("deleted = false")` is appended to every HQL query and cannot
be disabled per query, the pre-checks (`isPlanCodeTaken` / `isPlanNameTaken`) are
**native** queries. Without them the caller would get an opaque 409 from the database
constraint instead of a message naming the offending field.

### Optimistic locking

`PUT` requires the `version` the client last read. `@Version` alone only catches a
conflict inside one transaction; the real hazard is two admins editing the same plan
in two requests, where the second silently discards the first one's pricing change.
A mismatch raises `ObjectOptimisticLockingFailureException`, which
`GlobalExceptionHandler` already maps to `409 CONCURRENT_MODIFICATION`.

### Deactivate vs delete

- **Deactivate** — withdraws the plan from the catalogue offered to *new* companies.
  Existing subscribers are grandfathered, never cancelled. Idempotent.
- **Delete** — soft delete only. Also deactivates, so the row stays out of the
  assignable catalogue even if restored by hand. Nothing is ever hard deleted.

## API

All endpoints require `SUPER_ADMIN` and a bearer token.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/subscription-plans` | `201` + `Location`. Unique code and name |
| `PUT` | `/api/v1/subscription-plans/{id}` | Full replacement. `version` required |
| `GET` | `/api/v1/subscription-plans/{id}` | Full representation |
| `GET` | `/api/v1/subscription-plans` | Paged, sorted, filtered, searchable |
| `PATCH` | `/api/v1/subscription-plans/{id}/activate` | Idempotent |
| `PATCH` | `/api/v1/subscription-plans/{id}/deactivate` | Idempotent |
| `DELETE` | `/api/v1/subscription-plans/{id}` | Soft delete, `200` with envelope |

`DELETE` returns `200`, not `204`: every response carries the standard `ApiResponse`
envelope and a `204` must have an empty body.

### List parameters

| Parameter | Meaning |
|---|---|
| `planType` | Exact tier |
| `isActive` | true = offered catalogue, false = retired |
| `currency` | ISO-4217, case-insensitive |
| `minPrice` / `maxPrice` | Inclusive bounds on `monthlyPrice`. `min > max` -> 400 |
| `search` | Case-insensitive LIKE over code, name and description |
| `page` / `size` | `size` capped at **100** |
| `sort` | Whitelisted (below); default `displayOrder,planCode` ascending |

Sortable: `planCode`, `planName`, `planType`, `monthlyPrice`, `yearlyPrice`,
`currency`, `trialDays`, `displayOrder`, `isActive`, `createdAt`, `updatedAt`.
Anything else is rejected with `400 VALIDATION_FAILED` — Spring binds `sort` straight
onto an entity attribute, so an unknown name would otherwise surface as a 500 from
deep inside the repository, and an unintended one would order by columns that are not
the client's business.

`search` escapes `%`, `_` and `\` before building the LIKE pattern; without that, a
search for `%` returns the whole catalogue regardless of the other filters.

### Errors

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation, bad sort key, `minPrice > maxPrice` |
| 401 | `UNAUTHENTICATED` | No/expired token |
| 403 | `ACCESS_DENIED` | Authenticated but not `SUPER_ADMIN` |
| 404 | `RESOURCE_NOT_FOUND` | Unknown or soft-deleted id |
| 409 | `DUPLICATE_RESOURCE` | Plan code or name already taken, deleted rows included |
| 409 | `CONCURRENT_MODIFICATION` | Stale `version` |
| 422 | `BUSINESS_RULE_VIOLATION` | Priced `TRIAL`, negative price, trial with 0 days |

## Persistence — `V3__subscription.sql`

Migration versions follow build order, not plan order: auth took `V2`, this took `V3`,
and the company module now takes **`V4`**. Flyway here is forward-only with
out-of-order disabled, so a reserved gap could never have been filled later.

```sql
CREATE TABLE subscription_plans (
  id BINARY(16) NOT NULL,
  plan_code VARCHAR(50) NOT NULL,          -- UPPERCASE, immutable
  plan_name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  plan_type VARCHAR(20) NOT NULL,
  monthly_price DECIMAL(19,4) NOT NULL,
  yearly_price  DECIMAL(19,4) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'INR',
  trial_days INT NOT NULL DEFAULT 0,
  max_users INT NULL, max_branches INT NULL, max_hubs INT NULL,
  max_customers INT NULL, max_drivers INT NULL, max_vehicles INT NULL,
  max_daily_bookings INT NULL, max_monthly_bookings INT NULL,
  storage_limit_gb INT NULL, api_rate_limit INT NULL,   -- NULL = unlimited
  feature_flags JSON NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  display_order INT NOT NULL DEFAULT 0,
  -- BaseEntity columns: created_at/by, updated_at/by, deleted/at/by, version
  PRIMARY KEY (id),
  UNIQUE KEY uk_subscription_plans_code (plan_code),
  UNIQUE KEY uk_subscription_plans_name (plan_name),
  KEY idx_subscription_plans_active_order (is_active, display_order),
  KEY idx_subscription_plans_type (plan_type),
  KEY idx_subscription_plans_deleted (deleted, display_order),
  CONSTRAINT ck_subscription_plans_monthly_price CHECK (monthly_price >= 0),
  CONSTRAINT ck_subscription_plans_yearly_price  CHECK (yearly_price  >= 0),
  CONSTRAINT ck_subscription_plans_trial_days    CHECK (trial_days    >= 0)
);
```

`utf8mb4_unicode_ci` is case-insensitive, so `uk_subscription_plans_name` also
rejects `Standard` when `STANDARD` exists — matching the `LOWER()` comparison the
application performs.

No seed rows: populating the catalogue is a `SUPER_ADMIN` action, not a migration.

`feature_flags` is schemaless on purpose. Features are added far more often than
plans, and a `BOOLEAN` column per feature means a migration for each one.

## Layout

```
com.courier.modules.subscription
├── api
│   ├── SubscriptionPlanController        thin: bind, validate, map
│   ├── SubscriptionPlanMapper            hand-written; no MapStruct in this project
│   └── dto/  CreateSubscriptionPlanRequest, UpdateSubscriptionPlanRequest,
│             SubscriptionPlanResponse, SubscriptionPlanSummary
├── application
│   ├── SubscriptionPlanService           interface
│   ├── SubscriptionPlanServiceImpl       @PreAuthorize + @Transactional + audit
│   └── command/  CreateSubscriptionPlanCommand, UpdateSubscriptionPlanCommand
└── domain
    ├── SubscriptionPlan, PlanType
    ├── SubscriptionPlanRepository        JpaRepository + JpaSpecificationExecutor
    ├── SubscriptionPlanCriteria          filter object
    └── SubscriptionPlanSpecifications    Criteria API predicates
```

The service takes **commands**, not request DTOs, and returns **entities**, not
responses — otherwise `application` would depend on `api` and invert the dependency
rule. The mapper bridges both directions and lives in `api`, which owns the contract.

`SubscriptionPlanCriteria` sits in `domain` so the controller (which builds it) and
the service (which passes it on) share it without either depending on the other.

## Audit

Every write emits an event via `AuditService`: `SUBSCRIPTION_PLAN_CREATED`,
`_UPDATED`, `_ACTIVATED`, `_DEACTIVATED`, `_DELETED`.

`_UPDATED` records **only the fields that changed**, as `{field: {from, to}}`. A full
before/after dump of twenty fields per edit makes the trail unreadable. Idempotent
no-ops (activating an active plan) emit nothing, so real activations are not buried.

## Tests

40 unit tests, all green:

- `SubscriptionPlanTest` — normalisation, pricing rules, `ENTERPRISE` quota clearing,
  `withinLimit` semantics, feature flags, soft delete.
- `SubscriptionPlanServiceImplTest` — duplicate code/name (deleted rows included),
  priced trial rejected, enterprise nulling, stale version -> 409, immutable code,
  changed-fields-only audit, idempotent activate, delete is soft and never calls
  `repository.delete`.
- `SubscriptionPlanMapperTest` — null quotas survive every hop as "unlimited".
- `RoleTest` — extended: `SUPER_ADMIN` is platform-scoped and distinct from
  `PLATFORM_ADMIN`.

Method security is not covered by unit tests — `@PreAuthorize` needs a proxy, so it
belongs to an integration slice. Tracked in `BACKLOG.md`. It *was* exercised by hand
against a running server: `PLATFORM_ADMIN` gets 403, `SUPER_ADMIN` gets 200.

### Two defects unit tests could not have caught

Both surfaced on the first real run and are fixed:

1. **`plan_type` rendered as a native MySQL `enum(...)`.** Since Hibernate 6.5 the
   MySQL dialect maps a `STRING` enum to a native enum column, which does not match
   `VARCHAR(20)` in `V3` and fails `ddl-auto: validate` at startup. Pinned with
   `@JdbcTypeCode(SqlTypes.VARCHAR)` on the field — **keep it there**, and do the same
   for any new enum column in this project.
2. **`SELECT COUNT(*) > 0` returned `BIGINT` into a `boolean`.** MySQL has no boolean
   type, so every create died with *"class java.lang.Long cannot be cast to class
   java.lang.Boolean"* as a 500. The native queries now return a count and compare in
   Java. A repository mock returns whatever type the signature declares, so no unit
   test could ever have failed on this.

## Open items for the next module

- **`companies.subscription_plan_id`** FK lands in `V4__tenant.sql`, not here.
- **Delete guard**: once companies exist, deleting a plan that has subscribers must be
  refused. There is nothing to check against yet.
- **Quota enforcement** is defined here and applied by the modules that own the
  counts (users, branches, bookings). Always via `SubscriptionPlan.withinLimit`.

---

## `BillingCycle` (2026-07-29)

`MONTHLY` (1), `QUARTERLY` (3), `HALF_YEARLY` (6), `YEARLY` (12).

It lives here rather than on `SubscriptionPlan` because a plan carries **both** a monthly
and a yearly price — the cycle is not a property of the plan, it is chosen per company when
the subscription is assigned or renewed. Keeping it as an enum rather than a bare
`int months` means the price and the end date are derived from one decision instead of two
that can disagree: `endOf(start, periods)` and `priceOn(plan)` are the same choice, twice.

`plusMonths` clamps 31 January + 1 month to 28/29 February, which is what a customer
expects and what every billing system does.

**A company's subscription window is not modelled here.** There is no `subscriptions`
table: the plan link and the dates live on `companies`, which is why the three commercial
acts are endpoints on `CompanyController` and audited under `COMPANY_SUBSCRIPTION_*`. A
separate table becomes worth it when the product needs subscription *history* — the audit
trail is currently what answers that.
