# Module: company

**Status:** Company aggregate DONE and verified against MySQL 8.0.46 (Phase 2, v0.4.0).
The **SUPER_ADMIN console** shipped on top of it on 2026-07-29 (v0.12.0) — 8 more
endpoints, **`V12` not yet applied**. See *SUPER_ADMIN console* below.
Role Management shipped on top of it in Phase 3 (v0.5.0) — see
`MEMORY/modules/role.md`. Branches, hubs, service areas and rate cards are **later
phases of this same module** and are not built yet.
**Package:** `com.courier.modules.company`
**Depends on:** `shared`, `modules/subscription` (plan lookup),
`modules/auth` (admin provisioning, via its application service only).
**Depended on by:** every company-owned module, and `auth` at runtime through
`CompanyDirectoryPort`.

## Purpose

Owns the `Company` aggregate — **the company**. One company is one courier business
subscribing to the platform, and the root of all data ownership.

> **This module replaced the planned `modules/company`.** "Company" is the name the
> product uses, so the company root is a company and branches live underneath it.
> `MEMORY/modules/company.md` is now a redirect stub; nothing should be built there.

## Critical properties

**`Company` extends `BaseEntity`, not `CompanyOwnedEntity`.** It *is* the company, so
filtering it by `company_id` would be circular. Access is restricted to `SUPER_ADMIN`.

**`id` and `companyId` are two different UUIDs.**

| Field | Meaning |
|---|---|
| `id` | primary key of the `companies` row |
| `companyId` | the ownership key — stamped on every company-owned row, carried in the JWT `tid` claim, unique, immutable |

Kept apart so the ownership key never has to change if this table is restructured, and so
a leaked company id is not automatically a company id. `companyCode` doubles as the login
**slug**.

`company_roles` and `company_settings` *are* company-owned, extend `CompanyOwnedEntity`,
repeat the `@Filter`, and are written inside `CompanyContext.runAs(companyId, …)` — a
`SUPER_ADMIN` request carries no company of its own, so without that binding
`CompanyEntityListener` would have nothing to stamp and reads would not be filtered.

## Access control

`SUPER_ADMIN` only, on every endpoint including reads — a company row carries another
business's contact details, tax numbers and commercial terms.

1. `SecurityConfig` — URL rule on `/api/v1/companies/**`. Coarse outer gate.
2. `CompanyServiceImpl` — class-level `@PreAuthorize`, authoritative, on the
   implementation so it holds whichever proxy strategy Spring picks.

## Domain

```
Company (aggregate root, platform-level)
├── id, companyId              UUID, UUID          two distinct keys
├── companyCode               String  UNIQUE, UPPERCASE, IMMUTABLE, = login slug
├── companyName, legalName, displayName
├── subscriptionPlanId        UUID -> subscription_plans.id  (FK, RESTRICT)
├── status                    CompanyStatus  TRIAL|ACTIVE|INACTIVE|SUSPENDED|EXPIRED
├── trialStartDate, trialEndDate
├── subscriptionStartDate, subscriptionEndDate
├── email  UNIQUE, mobile, alternateMobile, website
├── gstNumber UNIQUE, panNumber UNIQUE, cinNumber
├── logo, favicon             URLs — binary assets belong in object storage
├── addressLine1/2, country, state, city, postalCode
├── timezone, currency, language, dateFormat, timeFormat
├── isActive                  derived from status, never set independently
└── remarks

CompanyRole (company-owned)          CompanySetting (company-owned)
├── roleCode  UNIQUE per company     ├── settingKey UNIQUE per company
├── roleName, description           ├── settingValue, category
├── roleType, isDefault  (V5)       ├── planDerived  read-only in the UI
├── systemRole  seeded eight        └── description
└── status  ACTIVE|INACTIVE  (V5)
   (permissions: rows in role_permissions since V6 — see MEMORY/modules/permission.md)
```

Plus `BaseEntity`: `createdAt/By`, `updatedAt/By`, `deleted/At/By`, `version`.

> **Naming note.** The build request called these `createdDate`/`updatedDate`. The
> columns stay `created_at`/`updated_at` — renaming `BaseEntity` would rewrite every
> table — and `CompanyMapper` presents them as `createdDate`/`updatedDate` on the wire,
> so neither name leaks into the other layer.

### Lifecycle

```
TRIAL ──activate──> ACTIVE ──suspend──> SUSPENDED ──activate──> ACTIVE
  │                   │                     │
  └──expire───────────┴──> EXPIRED ──activate──> ACTIVE
INACTIVE ──activate──> ACTIVE
```

`TRIAL` and `ACTIVE` are **operational**: only then may users authenticate. Nothing ever
returns to `TRIAL`. A `SUSPENDED` company cannot be expired and vice versa — it is
already blocked, and a second blocking status only obscures why. Illegal moves throw
`BusinessRuleException` with `INVALID_STATE_TRANSITION` (422).

`isActive` is derived from `status` by `syncActiveFlag()` on every transition, so the two
can never disagree.

## Business rules

| Rule | Where enforced |
|---|---|
| `companyCode` unique, uppercase, immutable | service pre-check + DB unique key |
| `companyId` unique, generated, immutable | generated with a collision re-check, DB unique key |
| `email` unique | service pre-check + DB unique key |
| `gstNumber`, `panNumber` unique | service pre-check + DB unique keys |
| Blank tax id stored as `NULL` | `Company.normaliseTaxId` |
| Subscription plan must exist **and be active** | `SubscriptionPlanService.getById` + status check |
| Company starts `TRIAL` iff the plan has trial days | `CompanyServiceImpl.create` |
| Suspension requires a reason | service, 422 without one |
| End date never before start date | `Company.applyInvariants` |
| Soft delete only | `BaseEntity.softDelete`, never `repository.delete` |
| Optimistic locking on update | client-supplied `version`, 409 on mismatch |

**Uniqueness counts soft-deleted rows.** A deleted company keeps its code, email and tax
numbers reserved — reusing them would let a new company inherit a closed one's identity.
The pre-checks are native queries because `@SQLRestriction("deleted = false")` cannot be
switched off per query, and they return a **count**, not a boolean: MySQL has no boolean
type, so `SELECT COUNT(*) > 0` comes back as `BIGINT` and mapping it to `boolean` throws
`ClassCastException` at runtime. (That exact bug shipped in the subscription module and
was only caught by running it.)

## Initialization flow

`POST /companies` does all of this in **one transaction** — a half-provisioned company is
worse than none:

```
1. resolve plan            SubscriptionPlanService.getById — must exist and be active
2. uniqueness checks       code, email, GST, PAN — including soft-deleted rows
3. generate companyId       UUIDv7 + collision re-check (a collision would merge
                           two companies' data — the worst failure possible here)
4. derive status/dates     plan.trialDays > 0  ->  TRIAL, trial window, billing
                           starts at trial end;  else ACTIVE from today
5. save company
6. runAs(companyId):        seed 8 roles + permissions   (DefaultRoleCatalog)
7. runAs(companyId):        seed ~24 settings            (CompanySettingKeys)
8. provision admin         auth's UserProvisioningService — PENDING, random
                           password, email-verification link
9. audit + publish         COMPANY_CREATED  +  CompanyEvent.CompanyCreated
```

### Default roles

**Eight** since Phase 3 (`V5`): `COMPANY_ADMIN`, `BRANCH_MANAGER`, `HUB_MANAGER`,
`BOOKING_OPERATOR`, `DELIVERY_OPERATOR`, `FINANCE_USER`, `CUSTOMER_SERVICE`, `VIEWER`.
Seeded as `systemRole = true`: editable, never deletable, because a company with no
`COMPANY_ADMIN` has nobody who can administer it. `BOOKING_OPERATOR` carries
`isDefault`.

> Managing them — create, rename, deactivate, delete custom ones — is **Role
> Management**, documented separately in `MEMORY/modules/role.md`. What follows is only
> what provisioning seeds.

Permissions come from `DefaultRoleCatalog` and are **filtered by the plan's feature
flags**. `BULK_BOOKING` and `API_ACCESS` are plan-gated, so a role can never be seeded
with a right the subscription does not include. A missing or non-`true` flag denies.

Roles are a *table*, not a second enum, so a company can recombine them later without a
migration — which is exactly what Role Management now allows. `Role` (the JWT authority
enum in `auth`) gained `COMPANY_ADMIN`, `HUB_MANAGER` and `VIEWER` to match;
`TENANT_ADMIN` stays because issued tokens carry it.

### Default settings

Five categories: `LOCALISATION` (from the company), `OPERATIONS` (AWB prefix derived
from the company code, COD on, auto-assign off), `NOTIFICATION`, `LIMITS` (one row per
plan quota, `planDerived`, **empty value = unlimited**) and `FEATURES` (one row per plan
feature flag).

### The first administrator

Created by `auth`'s `UserProvisioningService` — company **never** touches the `users`
table, per the cross-feature rule (§1 of ARCHITECTURE: depend on another feature's
application service, never its domain).

The account is `PENDING`, unverified, with a **random 32-byte password that is hashed and
immediately discarded**. Nobody knows it, it is never returned, logged or emailed. The
admin verifies their address and then sets a password through the normal reset flow, so
a leaked creation response grants nothing. `provisioning.verificationEmailSent = false`
means the account exists but the link must be reissued — a failed send must not roll back
a created company.

## Company directory

`CompanyDirectory implements CompanyDirectoryPort` and is marked `@Primary`,
displacing `StandaloneCompanyDirectory`. Two things start working the moment it loads:

- **Slug login.** `LoginRequest.companyCode` resolves against `companyCode`
  (case-insensitive). It used to be rejected with "slug lookup unavailable".
- **Status enforcement.** Only an operational company may authenticate; a suspended,
  expired, inactive or soft-deleted one returns `403 COMPANY_INACTIVE`.

`@Primary` rather than trusting the placeholder's `@ConditionalOnMissingBean`: ordering
between a scanned component and a conditional `@Bean` method is not worth gambling on
when the failure mode is "company status silently unenforced".

## API

All endpoints require `SUPER_ADMIN` and a bearer token.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/companies` | `201` + `Location`. Runs the whole initialization flow |
| `PUT` | `/api/v1/companies/{id}` | Full replacement. `version` required |
| `GET` | `/api/v1/companies/{id}` | Full representation |
| `GET` | `/api/v1/companies` | Paged, sorted, filtered, searchable |
| `PATCH` | `/api/v1/companies/{id}/activate` | Idempotent |
| `PATCH` | `/api/v1/companies/{id}/suspend` | Body: `reason` (required) |
| `PATCH` | `/api/v1/companies/{id}/expire` | Idempotent |
| `DELETE` | `/api/v1/companies/{id}` | Soft delete, `200` with envelope |
| `GET` | `/api/v1/companies/{id}/roles` | The seeded roles and permissions (super-admin view; companies use `/api/v1/roles`) |
| `GET` | `/api/v1/companies/{id}/settings` | The seeded settings, by category |

`DELETE` returns `200`, not `204`: every response carries the `ApiResponse` envelope and
a `204` must have an empty body.

### List parameters

`status` (repeatable), `isActive`, `subscriptionPlanId`, `country`, `state`, `city`,
`expiringBefore` (the renewals worklist — matches a trial *or* subscription ending on or
before that date), `createdFrom`, `createdTo`, `search`, plus `page`/`size`/`sort`.

Sortable: `companyCode`, `companyName`, `legalName`, `status`, `isActive`, `email`,
`city`, `state`, `country`, `trialEndDate`, `subscriptionEndDate`, `createdDate`,
`updatedDate`. Anything else is `400` — Spring binds `sort` straight onto an entity
attribute, so an unknown name would otherwise surface as a 500 from deep in the
repository. `size` is capped at **100**.

`search` covers code, name, legal name, email and mobile, case-insensitively, and
**escapes** `%`, `_` and `\` rather than rejecting them — company codes legitimately
contain underscores, so rejecting them would break searching for `ACME_LOGISTICS`.

### Errors

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation, bad sort key, `createdFrom > createdTo` |
| 401 | `UNAUTHENTICATED` | No/expired token |
| 403 | `ACCESS_DENIED` | Authenticated but not `SUPER_ADMIN` |
| 404 | `RESOURCE_NOT_FOUND` | Unknown or soft-deleted id |
| 409 | `DUPLICATE_RESOURCE` | Code, email, GST or PAN taken — deleted rows included |
| 409 | `CONCURRENT_MODIFICATION` | Stale `version` |
| 422 | `BUSINESS_RULE_VIOLATION` | Inactive plan, missing suspension reason, bad dates |
| 422 | `INVALID_STATE_TRANSITION` | e.g. `SUSPENDED -> EXPIRED` |

## Events

`CompanyEvent` is a **sealed** interface, so a new event type cannot be silently ignored
by an existing `switch`: `CompanyCreated`, `CompanyUpdated`, `CompanyActivated`,
`CompanySuspended`, `CompanyExpired`.

Published with `ApplicationEventPublisher`, consumed at
`@TransactionalEventListener(AFTER_COMMIT)` — a listener must never react to a company
whose transaction rolled back. In-process on purpose: there is no broker yet, and an
outbox table with no consumer is infrastructure for its own sake. Swapping to one later
changes the listener, not the publisher.

Events carry identifiers and minimal context, never the entity, which would be stale by
the time it is read. `CompanyUpdated.changedFields` uses the same `{from, to}` shape as
the audit trail.

## Persistence — `V4__company.sql`

Three tables. `companies` is platform-level; `company_roles` and `company_settings` are
company-owned with `company_id` leading every index.

```sql
companies
  id BINARY(16) PK, company_id BINARY(16) UNIQUE,
  company_code VARCHAR(50) UNIQUE, company_name, legal_name, display_name,
  subscription_plan_id -> subscription_plans(id) ON DELETE RESTRICT,
  status VARCHAR(20), trial_/subscription_ start+end DATE,
  email UNIQUE, mobile, gst_number UNIQUE, pan_number UNIQUE, cin_number,
  address/localisation columns, is_active, remarks, + BaseEntity columns
  KEY (status, is_active), (subscription_plan_id), (company_name),
      (subscription_end_date, trial_end_date)   -- renewals worklist

company_roles              UNIQUE (company_id, role_code), FK -> companies(company_id)
company_role_permissions   PK (role_id, permission), FK -> company_roles ON DELETE CASCADE
company_settings           UNIQUE (company_id, setting_key), FK -> companies(company_id)
```

Tax identifiers are `NULL` when absent, never `''` — MySQL allows repeated `NULL`s in a
unique index but not repeated empty strings, so two companies without a GSTIN must both
be storable.

The plan FK is `RESTRICT`, not `CASCADE`: plans are soft-deleted so the row persists, and
a plan must never be removable while companies are billed against it.

**Deferred: `users.company_id -> companies.company_id`.** The development database holds a
hand-inserted user row (`ops@acme.test`) whose owner matches no company, so the
constraint would fail the migration on boot. It ships as its own migration once that row
is reconciled — tracked in `BACKLOG.md`.

## Audit

`COMPANY_CREATED`, `COMPANY_UPDATED`, `COMPANY_ACTIVATED`, `COMPANY_SUSPENDED`,
`COMPANY_EXPIRED`, `COMPANY_DELETED`, plus `USER_CREATED` from provisioning. Suspension
reason is in the payload. `COMPANY_UPDATED` records **only changed fields** — a
before/after dump of twenty-six fields per edit makes the trail unreadable. Idempotent
no-ops emit nothing.

## Layout

```
com.courier.modules.company
├── api
│   ├── CompanyController          thin: bind, validate, map, whitelist sort
│   ├── CompanyMapper              hand-written; no MapStruct in this project
│   └── dto/  CreateCompanyRequest, UpdateCompanyRequest, CompanyResponse,
│             CompanySummaryResponse, CompanySearchRequest, SuspendCompanyRequest
├── application
│   ├── CompanyService / CompanyServiceImpl     @PreAuthorize + @Transactional
│   ├── CompanyProvisioningService              roles, settings, admin
│   ├── command/  CreateCompanyCommand, UpdateCompanyCommand
│   └── event/    CompanyEvent (sealed), CompanyEventListener
├── domain
│   ├── Company, CompanyStatus
│   ├── CompanyRole, Permission, DefaultRoleCatalog
│   ├── CompanySetting, CompanySettingKeys
│   ├── CompanyRepository, CompanyRoleRepository, CompanySettingRepository
│   ├── CompanyCriteria, CompanySpecifications
└── infrastructure
    └── CompanyDirectory     implements auth's CompanyDirectoryPort, @Primary
```

The service takes **commands** and returns **entities**; the mapper bridges both
directions and lives in `api`, which owns the contract.

## Tests

50 unit tests, all green (192 in the suite):

- `CompanyTest` — normalisation, blank tax ids becoming null, inverted date windows,
  every legal and illegal transition, `isActive` tracking status, window boundaries.
- `DefaultRoleCatalogTest` — the five roles, admin holds everything, viewer is
  read-only, plan gating denies on missing/false/non-boolean flags, returned set is a
  fresh copy.
- `CompanyServiceImplTest` — company id distinct from company id and re-checked for
  collisions, trial vs non-trial start, provisioning wiring and admin-email fallback,
  four uniqueness rules, inactive plan refused, stale version 409, immutable code and
  company id, changed-fields-only audit, suspension reason required, idempotent
  activate, soft delete never hard-deleting, and `runAs` binding for company-owned reads.

Method security needs a proxy and belongs to an integration slice — tracked.

## Verified by running it

Against MySQL 8.0.46 on 2026-07-22: `V4` applied, `ddl-auto: validate` passed, then
end-to-end — 401/403/200 by role; create returning `201` with 5 roles, 24 settings and a
`PENDING` admin; `BULK_BOOKING` seeded to 2 roles while `API_ACCESS` (disabled on the
plan) was seeded to none; limits stored empty for unlimited quotas; duplicate code 409;
missing suspension reason 400; `SUSPENDED -> EXPIRED` 422; stale version 409; bad sort
400; `search=acme_log` matching despite the underscore; soft delete leaving the row with
`deleted=1, is_active=0` and the code still reserved; slug login resolving and a
suspended company returning `COMPANY_INACTIVE`.

## Next in this module

- [ ] `users.company_id` FK once the orphan dev row is reconciled
- [ ] **Branch / Hub** — code, address, geo, type (`HEAD_OFFICE | HUB | BRANCH |
      WAREHOUSE`), parent hierarchy, `UNIQUE (company_id, code)`, exactly one head office
- [ ] `ServiceArea` — serviceable pincodes, the hottest read in the system
- [ ] `RateCard` + slabs — non-overlapping, gapless from zero
- [ ] Quota enforcement reading `limit.*` settings before creating users/branches/bookings
- [ ] Company self-service: `GET /companies/me` for `COMPANY_ADMIN`, role customisation
- [ ] A scheduled job moving companies past `trialEndDate`/`subscriptionEndDate` to
      `EXPIRED` — today expiry is manual

---

# SUPER_ADMIN console (2026-07-29, v0.12.0) — code complete, `V12` unapplied

## What a super admin can now do

| Capability | Endpoint | Audit action |
|---|---|---|
| Create a company | `POST /api/v1/companies` | `COMPANY_CREATED` |
| Update a company | `PUT /companies/{id}` | `COMPANY_UPDATED` |
| Activate | `PATCH /companies/{id}/activate` | `COMPANY_ACTIVATED` |
| **Deactivate** | `PATCH /companies/{id}/deactivate` | `COMPANY_DEACTIVATED` |
| Suspend | `PATCH /companies/{id}/suspend` | `COMPANY_SUSPENDED` |
| Expire | `PATCH /companies/{id}/expire` | `COMPANY_EXPIRED` |
| **Assign subscription** | `POST /companies/{id}/subscription` | `COMPANY_SUBSCRIPTION_ASSIGNED` |
| **Renew subscription** | `POST /companies/{id}/subscription/renew` | `COMPANY_SUBSCRIPTION_RENEWED` |
| **Suspend subscription** | `POST /companies/{id}/subscription/suspend` | `COMPANY_SUBSCRIPTION_SUSPENDED` |
| **Company statistics** | `GET /companies/{id}/statistics` | — |
| **Platform dashboard** | `GET /api/v1/super-admin/dashboard` | — |
| **Create platform operator** | `POST /api/v1/super-admin/users` | `SUPER_ADMIN_USER_CREATED` |
| **List platform operators** | `GET /api/v1/super-admin/users` | — |

## What it deliberately cannot do

No branch, shipment, customer or manifest creation, and no wallet movement. Those are a
company's own operations, performed by its own staff under its own roles.

The reason is not tidiness. A record a platform operator created is **indistinguishable in
the data** from one the company created itself: months later nobody can say whether the
company booked that shipment or the vendor booked it "to help", and for money that
question is the entire purpose of a ledger.

`SuperAdminBoundaryTest` asserts this by reading the `@PreAuthorize` expressions directly.
It is the one test in the suite that asserts something does *not* work — a guard is removed
by loosening one annotation, and nothing else would notice. It already earned its keep:
wallet `openRecharge`/`completeRecharge` were bare `isAuthenticated()`, and a super admin
was kept out only by not happening to have a branch. They now exclude the platform tier
explicitly.

`DefaultRoleCatalog` carries the mirror-image rule: `COMPANY_ADMIN`'s grants are *derived*
from the whole permission catalogue, so `SUBSCRIPTION_*`, `GLOBAL_MASTER_*` and
`SUPER_ADMIN_USER_*` — and the platform half of `COMPANY_*` — are excluded by module and
action, not by a list of codes. A right added to a platform-only module is therefore
excluded the day it is added.

## Subscription: three acts, not three fields

`PUT /companies/{id}` can already change `subscriptionPlanId`. It stays that way, and it is
**not** how a subscription is managed, because it audits as "company updated" and says
nothing about billing dates — which makes *"when did Acme move up to ENTERPRISE, and who
approved it"* unanswerable.

- **Assign** opens a paid window and activates the company. It also **closes any trial**:
  two open windows with no rule about which is in force is not a state worth having, and
  every expiry report would have to guess.
- **Renew** extends from **the later of the current end and today**. Paying a week early
  must not forfeit the week already bought; paying a month late must not bill for the month
  the customer could not use. That single rule is why this is an operation rather than a
  settable `subscriptionEndDate`, and why the request carries **no start date** — it is not
  the caller's to choose. A renewal reactivates an `EXPIRED` or `SUSPENDED` company and may
  carry an upgrade, recorded as one event because the customer experiences one.
- **Suspend** closes the paid window as of today, so the company stops appearing as paid on
  every renewals report while being unable to sign in. Requires a reason.

`BillingCycle` (`MONTHLY`/`QUARTERLY`/`HALF_YEARLY`/`YEARLY`) lives in
`modules/subscription` because the plan carries both a monthly and a yearly price — the
cycle is chosen per company, not per plan. An explicit `endDate` always overrides the
cycle: real contracts do not always land on a boundary, and the system must not disagree
with the invoice.

## `INACTIVE` is not `SUSPENDED`

Both stop authentication. Only one is an accusation, and support quotes the difference back
to the customer. So deactivate is its own verb, legal from every status except itself, and
its reason is **optional** — demanding a justification for routine housekeeping only
teaches operators to type "n/a". Suspension's reason stays mandatory.

## The first administrator now gets a temporary password

`provisionAdmin` no longer creates an unusable password (reversing decision 21; see
decision 49). It generates a policy-valid one and returns it **once**, in
`provisioning.temporaryPassword` on the create response — never logged, audited, emailed or
readable again.

What made the old design wrong in practice: an unusable password made the activation email
the *sole* way into a brand-new company, so a bounced or filtered message left the customer
with an account nobody could enter and a super admin with nothing to hand them.

What keeps the new one safe is not the password alone. **The account is still `PENDING`**,
so the password opens nothing until the activation link is followed — both factors must
arrive. And `NotificationPort.sendCompanyActivation(...)` is **deliberately never given the
password**: an email puts a plaintext credential in a mailbox, a mail server and every
backup of both, at an address nobody has yet proved they own.

## Platform operators

`POST /super-admin/users` creates `SUPER_ADMIN` accounts and nothing else — there is no
role field, because a role parameter on the endpoint with the least surface is a way to
mint any account on the platform.

The address must be unused **across the whole platform**, not per company. Ordinary
accounts are unique per company because two unrelated businesses may employ the same
person; a platform operator signs in with **no company code**, and
`AuthService.resolvePlatformCompany` finds their home company by locating the single
platform account with that address. A second one makes that lookup ambiguous — and an
ambiguous match is refused as a bad credential, so the newer account could never sign in at
all. Better to refuse the creation.

The account is created ACTIVE and pre-verified: a platform operator is onboarded by another
platform operator, and there is nobody above them to recover the account if an email never
arrives.

`homeCompanyId` anchors the row because `users` has a non-null owner column. It confers
nothing — a super admin already reaches every company.

The list includes `PLATFORM_ADMIN` as well, because the question it answers is "who acts
above the companies", and omitting the role that can impersonate any company would be wrong
in the one direction that matters.

## `CompanyDashboardService` — the read model

Separate from `CompanyService` on purpose: that service owns the lifecycle and every method
touches one aggregate, while this one crosses into users, branches, roles and plans purely
to count them. Folding them together would give the lifecycle service four more
repositories it has no business writing to.

Two things in it are load-bearing:

- **Company-owned counts run inside `CompanyContext.runAs`.** A super admin's request
  carries no company, so the Hibernate filter would have nothing to apply and every count
  would silently be a platform-wide total presented as one company's. One binding wraps all
  six counts, so two of them can never disagree about which company is being counted.
- **Plan facts come from `SubscriptionPlanService`, never its repository** — the
  cross-feature rule. A count therefore costs a one-row page rather than a `count(*)`,
  which is the right price for the boundary.

**There is no `shipmentCount`,** on the company statistics or the platform dashboard.
`modules/shipment` does not exist, and a field that is always zero reads as "this company
has booked nothing" rather than "nobody has built this yet" — indistinguishable on screen.
It arrives with the module that can populate it.

## Verification status

**`V12` now applied** (2026-07-30, during the `tenant_id` → `company_id` rename — see
`MEMORY/CHANGELOG.md`), which also fixed a real bug in its `TENANT_ADMIN` rewrite. Still
unverified against actual duplicate/legacy data, since the rebuilt dev database had none:
the geography merge and the name-collision rename. Row count for the permission catalogue
addition is confirmed (219 rows). See *Next Task* in `MEMORY/AI_CONTEXT.md`.

---

# The Company Admin boundary (2026-07-29)

The mirror of the section above. That one says what the platform may do to a company; this
one says what a company may do for itself, and where it stops. Both halves are asserted by
`CompanyAdminBoundaryTest`, the companion to `SuperAdminBoundaryTest`.

## What a company admin runs

Fifteen modules, and `COMPANY_ADMIN` holds **every** code the catalogue defines in each:

| Responsibility | `PermissionModule` | Shipped? |
|---|---|---|
| Branches | `BRANCH` | yes — `/api/v1/branches` |
| Company users | `USER` | yes — `/api/v1/users` |
| Roles | `ROLE` | yes — `/api/v1/roles` |
| Permissions | `PERMISSION` | yes — `/api/v1/permissions`, `/roles/{id}/permissions` |
| Routes | `ROUTE_MASTER` | yes — `/api/v1/master/routes` |
| Company settings | `SETTINGS` | yes — `/api/v1/company-settings` |
| Branch wallet | `WALLET` | yes — `/api/v1/branch-wallet` |
| Rate master | `RATE_MASTER` | **no service yet** |
| Vehicles | `VEHICLE` | **no** — `master/vehicle-types` is a catalogue of *types*, not a fleet |
| Drivers | `DRIVER` | **no service yet** |
| Customers | `CUSTOMER` | **no service yet** |
| Customer addresses | `ADDRESS` | **no service yet** |
| Shipment orders | `SHIPMENT` | **no service yet** |
| Manifest | `MANIFEST` | **no service yet** |
| Reports | `REPORT` | **no service yet** |

The permission codes exist for all fifteen and the admin already holds them, so the day a
module ships its owner is not in question. The right-hand column is the honest part: eight
of them are codes with nothing behind them.

## What a company admin cannot do, and where that is enforced

| Refusal | Enforced by |
|---|---|
| Create a company | `CompanyServiceImpl` class-level `hasRole('SUPER_ADMIN')` + `SecurityConfig` URL rule on `/api/v1/companies/**` |
| Delete a company | same |
| Manage a subscription | same guard reaches `assignSubscription` / `renewSubscription` / `suspendSubscription`; `SubscriptionPlanServiceImpl` is `SUPER_ADMIN` for the catalogue |
| Reach another company | Hibernate `@Filter` on every company-owned entity + a `…WithinCompany` load on every single-row read; a foreign id is a 404, never a 403 |

`COMPANY_ADMIN` **does** keep `COMPANY_READ` and `COMPANY_UPDATE` — that is why
`DefaultRoleCatalog` excludes platform *actions* on the `COMPANY` module rather than the
whole module. An admin who cannot see the business they administer is not an admin.

**The refusal test checks the method guard or the class guard**, whichever reaches the
method. A refusal is broken from either end: by loosening the class annotation, or by
adding a looser one to a single method. Checking one would miss half the ways it goes
wrong.

## Company isolation — what was verified, 2026-07-29

Read, not changed. Recorded so this is not re-derived next module.

- **Every business table carries the owner column.** The four without it are deliberate:
  `subscription_plans` and `permissions` are platform catalogues, `user_roles` is auth's
  element collection of JWT authorities (a child of `users` by `ON DELETE CASCADE`), and
  `company_role_permissions` was dropped by `V6`. `V12`'s `_v12_map_*` tables are migration
  scratch and are dropped at the end of it.
- **All 27 company-owned entities repeat `@Filter` and `@SQLRestriction`.** Hibernate does
  not inherit `@Filter` from a `@MappedSuperclass`; an entity that forgets it is not
  slightly less filtered, it is unfiltered. Now asserted, from a written-out list rather
  than a classpath scan — a scan would pass by finding whatever exists.
  `CompanySettings` is the single deliberate exception to `@SQLRestriction`: one row per
  company, created on first access, never deleted.
- **The owner column is still named `company_id`**, mapped in exactly one place. The test
  asserts that too, because the failure mode of a well-meaning rename is every
  company-owned table at once.
- **Frontend:** every `companies*` and `platform/*` route is `roleGuard` +
  `[SUPER_ADMIN]`. There is no console path for a company admin to create, delete or
  re-subscribe a company.

**The one check still impossible:** a cross-company HTTP attempt. `RIVAL_CO` has no active
user, so there is nothing to attack from. It has been on every module's verification list
since Branch Wallet.
