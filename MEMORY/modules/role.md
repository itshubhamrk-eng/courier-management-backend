# Module: role (Role Management)

**Status:** DONE and verified against MySQL 8.0.46 (Phase 3, v0.5.0).
**Update (v0.6.0):** permissions moved off this entity into `role_permissions` — see
`MEMORY/modules/permission.md`. Where this doc says a role "holds permissions", they are
now rows in that join table, read through `RolePermissionService`, not a field on `CompanyRole`.
**Update (Branch RBAC, 2026-07-30):** a ninth seeded role, `ACCOUNTS` — the branch's own
money desk (wallet top-ups, payments, invoices, COD reconciliation) — was already in
`DefaultRoleCatalog` but untested and unmigrated; `DefaultRoleCatalogTest` and
`DefaultPermissionCatalogTest` were red before this pass (see *Nine roles, not eight*
below). `DefaultRoleCatalog.BRANCH_ROLE_CODES` / `isBranchAssignable` now actually gate
who a branch manager may staff with — see `MEMORY/modules/branch.md` §"A branch manager
staffs their own branch" and `MEMORY/modules/user.md`.
**Package:** `com.courier.modules.company` — roles live *inside* the company module.
**Depends on:** `shared` only.
**Depended on by:** User Management (assigning roles) and Permission Management
(granting rights), neither of which is built yet.

## Why this is not a new table

`company_roles` already existed: `V4` created it and seeded five roles per company.
Phase 3 **extends** it rather than adding a second `roles` table — two tables both
meaning "a role" would drift within a release, and the seeded rows plus their
permissions would have had to be migrated across.

The entity is therefore still `CompanyRole`, not `Role`. `modules/auth` already owns a
`Role` enum (the JWT authorities `COMPANY_ADMIN`, `OPERATOR`, …), and two types called
`Role` in one codebase is a landmine. The API, the DTOs and this document all say
"role"; only the class keeps the longer name.

| Concept | Type | What it is |
|---|---|---|
| `auth.Role` | enum | JWT authority — what the token says you are |
| `company.CompanyRole` | entity | A company's own role — what your company says you may do |

## Domain

```
CompanyRole (company-owned)
├── id, companyId          UUID
├── roleCode              UNIQUE per company, UPPERCASE, IMMUTABLE
├── roleName              UNIQUE per company (case-insensitive)
├── description
├── roleType              ADMINISTRATION | OPERATIONS | FINANCE | SUPPORT | READ_ONLY
├── isSystemRole          seeded by the platform: editable, never deletable
├── isDefault             assigned to new users when none is specified
└── status                RoleStatus  ACTIVE | INACTIVE
```

> Permissions were a `Set<Permission>` field here until v0.6.0. They are now rows in
> `role_permissions`, read through `RolePermissionService` — see
> `MEMORY/modules/permission.md`.

Plus `BaseEntity`: `createdAt/By`, `updatedAt/By`, `deleted/At/By`, `version`. The API
presents the timestamps as `createdDate`/`updatedDate`; the columns stay
`created_at`/`updated_at`, and `RoleMapper` bridges the two.

### Three fields that look redundant and are not

- **`roleType` is not "system vs custom".** `isSystemRole` already records that. This is
  the functional grouping the roles screen renders under and reports slice by. A
  company's own "Night Shift Supervisor" is `OPERATIONS`, not a category of its own.
- **`status` replaced the boolean `is_active`.** A boolean answers "on or off" and
  nothing else; a status can grow an archived or pending-approval state without a second
  flag that contradicts the first.
- **`isDefault` is not `isSystemRole`.** The default is whichever role a *new user*
  receives — a company may point it at a custom role.

### Default roles: five became eight, then nine

`OPERATOR` split into `BOOKING_OPERATOR` and `DELIVERY_OPERATOR` — booking a parcel and
delivering it are different desks, and one role covering both meant every counter clerk
could also close deliveries. `FINANCE_USER` and `CUSTOMER_SERVICE` were added because
they were previously served by handing someone `BRANCH_MANAGER`, which is far more than
either needs. `ACCOUNTS` followed for the same reason, scoped to one branch's money desk
rather than `FINANCE_USER`'s company-wide rate/invoice reach.

| Code | Type | Default | Notes |
|---|---|---|---|
| `COMPANY_ADMIN` | ADMINISTRATION | | Every permission |
| `BRANCH_MANAGER` | OPERATIONS | | Runs one branch: staff, menus, wallet, booking through delivery |
| `HUB_MANAGER` | OPERATIONS | | Inbound/outbound, scans, vehicles |
| `BOOKING_OPERATOR` | OPERATIONS | **yes** | Creates and prices; cannot cancel |
| `DELIVERY_OPERATOR` | OPERATIONS | | Receives, dispatches, delivers; cannot create or price |
| `ACCOUNTS` | FINANCE | | One branch's wallet, payments, invoices; never the road |
| `FINANCE_USER` | FINANCE | | Rates and invoicing company-wide; no operational writes |
| `CUSTOMER_SERVICE` | SUPPORT | | May cancel a booking on request |
| `VIEWER` | READ_ONLY | | Auditors and analysts |

Seeded with `isSystemRole = true` and permissions filtered by the plan's feature flags,
so a role can never start with a right the subscription excludes.

### Which roles a branch manager may hand out

`DefaultRoleCatalog.BRANCH_ROLE_CODES` is `{BRANCH_MANAGER, BOOKING_OPERATOR,
DELIVERY_OPERATOR, ACCOUNTS}` — the four roles a branch manager may put their own staff
into, plus any **custom** (non-system) role the company has defined; never `COMPANY_ADMIN`,
`HUB_MANAGER`, `FINANCE_USER`, `CUSTOMER_SERVICE` or `VIEWER`, which stay a company
administrator's call. `isBranchAssignable(roleCode, systemRole)` is the single check —
`!systemRole || BRANCH_ROLE_CODES.contains(roleCode)` — enforced in
`UserServiceImpl.assignRole`/`create` (see `MEMORY/modules/user.md`). The set is closed
deliberately: expressed as "anything except COMPANY_ADMIN" it would make every future
system role branch-assignable on the day it is added rather than the day someone decides
it should be.

## Business rules

| Rule | Where enforced |
|---|---|
| `roleCode` unique **within the company** | service pre-check + `UNIQUE (tenant_id, role_code)` |
| `roleName` unique within the company, case-insensitive | service pre-check |
| `roleCode` uppercased, spaces → underscores, immutable | `CompanyRole.normaliseCode` |
| System roles cannot be **deleted** (may be renamed) | service, 422 |
| The default role cannot be deleted or deactivated | service, 422 |
| At most one default per company | service demotes the previous one |
| `isSystemRole` never settable by a caller | absent from both request DTOs |
| Soft delete only | `BaseEntity.softDelete` |
| Optimistic locking on update | client-supplied `version`, 409 on mismatch |

**Uniqueness counts soft-deleted rows.** A deleted role keeps its code reserved, so a
new role cannot inherit a deleted one's identity in the audit trail. The pre-checks are
native queries returning a **count**: `@SQLRestriction` cannot be disabled per query, and
MySQL returns `BIGINT` for `COUNT(*) > 0`, which threw `ClassCastException` when a
previous module declared it `boolean`.

Deactivating is not deleting: **existing holders keep the role.** A deactivation that
stripped access from everyone holding it would be an outage, not a configuration change.

## Security

| Actor | May do |
|---|---|
| `COMPANY_ADMIN` | Everything, **only within their own company** |
| `SUPER_ADMIN` | Read any company's roles; **never write** |
| Anyone else | 403 |

Per-method `@PreAuthorize` on `RoleServiceImpl`, not class-level, because reads and
writes genuinely differ here. `SecurityConfig` only requires authentication on
`/api/v1/roles/**` — no URL pattern can express "writes need one role, reads accept two".

### Company isolation, two layers

1. The Hibernate filter narrows every query to the bound company.
2. **Every single-row load goes through `findByIdWithinCompany`.** A primary-key load
   bypasses the filter entirely, so without this a company admin could fetch — and edit —
   another company's role by guessing an id.

A `COMPANY_ADMIN`'s search is **pinned** to their own company: a `companyId` in the query
string is overridden, not honoured. A `SUPER_ADMIN` has no bound company, so the filter is
inactive and they see every company — intentional for reads, and the reason writes are
closed to them.

Not-found is returned as **404, never 403**: telling a caller "this exists but is not
yours" leaks the existence of other companies' data.

## API

| Method | Path | Role |
|---|---|---|
| `POST` | `/api/v1/roles` | `COMPANY_ADMIN` |
| `PUT` | `/api/v1/roles/{id}` | `COMPANY_ADMIN` |
| `GET` | `/api/v1/roles/{id}` | `COMPANY_ADMIN` (own) or `SUPER_ADMIN` (any) |
| `GET` | `/api/v1/roles` | `COMPANY_ADMIN` (own) or `SUPER_ADMIN` (all) |
| `GET` | `/api/v1/roles/assignable` | ACTIVE roles, for a role picker |
| `PATCH` | `/api/v1/roles/{id}/activate` | `COMPANY_ADMIN`, idempotent |
| `PATCH` | `/api/v1/roles/{id}/deactivate` | `COMPANY_ADMIN`, idempotent |
| `DELETE` | `/api/v1/roles/{id}` | `COMPANY_ADMIN`, soft delete |

A new role starts `ACTIVE` with **no permissions** — granting them is Permission
Management's job (`POST /api/v1/roles/{id}/permissions`). The role responses show the
codes a role holds, fetched from `RolePermissionService`.

### List parameters

`companyId` (super admin only), `status`, `roleType` (repeatable), `isSystemRole`,
`isDefault`, `permission`, `search`, plus `page`/`size`/`sort`.

`permissionCode` finds every role granting a specific right — the query to run before
changing what a permission means. Since v0.6.0 it is an EXISTS subquery on
`role_permissions`, not an element-collection join.

Sortable: `roleCode`, `roleName`, `roleType`, `status`, `isSystemRole`, `isDefault`,
`createdDate`, `updatedDate`. Anything else is 400. `size` capped at 100 — uncapped, a
super admin's listing would scan every company's roles at once.

`search` escapes `%`, `_` and `\` rather than rejecting them: role codes contain
underscores, so rejecting would break searching for `BOOKING_OPERATOR`.

### Errors

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation, unknown sort key |
| 403 | `ACCESS_DENIED` | Not `COMPANY_ADMIN` (writes) or neither role (reads) |
| 404 | `RESOURCE_NOT_FOUND` | Unknown id, or another company's role |
| 409 | `DUPLICATE_RESOURCE` | Code or name taken — soft-deleted rows included |
| 409 | `CONCURRENT_MODIFICATION` | Stale `version` |
| 422 | `BUSINESS_RULE_VIOLATION` | Deleting a system role, or touching the default |

## Persistence — `V5__role_management.sql`

Alters `company_roles` rather than creating a table:

```sql
ALTER TABLE company_roles
  ADD role_type  VARCHAR(20) NOT NULL DEFAULT 'OPERATIONS',
  ADD is_default BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE company_roles SET status = IF(is_active,'ACTIVE','INACTIVE');   -- carry it across
UPDATE company_roles SET role_code='BOOKING_OPERATOR', is_default=TRUE
 WHERE role_code='OPERATOR';                                            -- rename, not recreate
ALTER TABLE company_roles DROP COLUMN is_active;

DROP INDEX idx_company_roles_tenant ON company_roles;                   -- was (tenant_id, is_active)
CREATE INDEX idx_company_roles_tenant ON company_roles (tenant_id, status);
CREATE INDEX idx_company_roles_type   ON company_roles (tenant_id, role_type);
```

Then it **backfills every existing company** with `DELIVERY_OPERATOR`, `FINANCE_USER`
and `CUSTOMER_SERVICE` plus their permissions, and adds `BULK_BOOKING` to
`BOOKING_OPERATOR` only where the company's plan enables it (`JSON_EXTRACT` on the plan's
feature flags). Without the backfill a company created before this release would show a
different catalogue from one created after it.

Three details worth keeping:

- The old boolean is **carried across before being dropped** — an inactive role must not
  silently come back as `ACTIVE`.
- `OPERATOR` is **renamed, not deleted and recreated**, so users already holding it keep
  their assignment and its id stays stable.
- Every insert is guarded by `NOT EXISTS`, so the migration is safe against a database
  where some rows already exist.

## Audit

`ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`, `ROLE_ACTIVATED`, `ROLE_DEACTIVATED` —
distinct from the older `ROLE_GRANTED`/`ROLE_REVOKED`, which are about giving a role to a
*user*. `ROLE_UPDATED` records only changed fields; idempotent no-ops emit nothing.

## Layout

```
com.courier.modules.company
├── api
│   ├── RoleController          COMPANY_ADMIN writes, SUPER_ADMIN reads
│   ├── RoleMapper
│   └── dto/  CreateRoleRequest, UpdateRoleRequest, RoleResponse,
│             RoleSummaryResponse, RoleSearchRequest
├── application
│   ├── RoleService / RoleServiceImpl        per-method @PreAuthorize
│   └── command/  CreateRoleCommand, UpdateRoleCommand
└── domain
    ├── CompanyRole, RoleType, RoleStatus, Permission
    ├── DefaultRoleCatalog        the eight seeded roles
    ├── CompanyRoleRepository     + JpaSpecificationExecutor
    └── RoleCriteria, RoleSpecifications
```

## Tests

35 unit tests (227 in the suite):

- `CompanyRoleTest` — code normalisation, null defaults, status lifecycle, default flag,
  permission replacement, soft delete.
- `DefaultRoleCatalogTest` — nine roles, exactly one default, every role typed, the
  booking/delivery split is meaningful, finance and accounts are scoped, plan gating
  denies on missing/false/non-boolean flags, branch-assignability (the four branch
  roles yes, `COMPANY_ADMIN`/`HUB_MANAGER`/`FINANCE_USER` no, any custom role yes).
- `RoleServiceImplTest` — normalisation, `systemRole` forced false, per-company
  uniqueness, default demotion, stale version 409, company-scoped loads, system and
  default roles undeletable, idempotent lifecycle, soft delete, super-admin fallback.

## Verified by running it

Against MySQL 8.0.46 on 2026-07-22, **with `V4`-shaped data seeded first** so the
migration's transform path was actually exercised:

- `V5` applied, `ddl-auto: validate` passed.
- `OPERATOR` → `BOOKING_OPERATOR`, `is_default = 1`, permissions preserved through the
  rename and `BULK_BOOKING` added from the plan.
- `VIEWER`, which was `is_active = 0`, came out `INACTIVE` — not silently reactivated.
- The three new roles backfilled with correct types and permission counts;
  `is_active` dropped; both indexes rebuilt.
- 401 without a token; `OPERATOR` 403; `SUPER_ADMIN` read 200 but write **403**;
  `COMPANY_ADMIN` create 201.
- Duplicate code and name 409; system-role delete 422; default-role deactivate 422;
  stale version 409; unknown sort key 400; renaming a system role allowed.
- Promoting a role to default demoted the previous one.
- **Cross-company: a second company's role id returned 404 on GET, PUT, DELETE and
  PATCH; it did not appear in search; a spoofed `companyId` returned the caller's own
  roles; the rival role was untouched afterwards.**
- Soft delete: 200 → 404, row retained `deleted=1`, code still reserved (409),
  `/assignable` excluded it.

## Next

- [ ] **Permission Management** — granting and revoking `Permission` values on a role.
      Until then a custom role is created with none.
- [ ] **User Management** — assigning roles to users, and honouring `isDefault` on
      creation. Deleting a role does not currently reassign its holders.
- [ ] Authorise on permissions, not just roles: `@PreAuthorize` still checks JWT role
      names, so a company re-permissioning a role does not yet change what its users can
      reach.
- [ ] Method-security integration slice for the read/write split.
