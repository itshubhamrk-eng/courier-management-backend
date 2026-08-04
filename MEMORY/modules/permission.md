# Module: permission (Permission Management)

**Status:** DONE and verified against MySQL 8.0.46 (Phase 3, v0.6.0).
**Package:** `com.courier.modules.company` — permissions live inside the company module,
alongside roles.
**Depends on:** `shared` only.
**Depended on by:** User Management (users inherit permissions through roles), and
eventually every `@PreAuthorize` in the system.

## Why the enum became a table

`Permission` was a **Java enum of 30 constants** stored in a
`company_role_permissions` element collection. That was fine while permissions were
hard-coded. A catalogue of **174 rights across 28 modules** that operators must list,
search, filter and extend is a table — an enum cannot be paged, cannot carry a
description or a display order, and cannot gain a new value without a release.

Two things replaced it:

| Before | After |
|---|---|
| `enum Permission` (30 constants) | `permissions` table, 174 seeded rows, **platform-level** |
| `company_role_permissions` (element collection) | `role_permissions` join entity, **company-owned** |

The grant is now a real entity, so "who gave this role the right to cancel shipments,
and when" is answerable — the element collection could not answer it at all.

## Permission structure

A permission code is always **`MODULE_ACTION`**, derived, never supplied:

```
Permission (platform-level)
├── id
├── permissionCode        SHIPMENT_CREATE — derived, UNIQUE, IMMUTABLE
├── permissionName        "Create Shipments" — defaults from module + action
├── module                one of 28 (PermissionModule)
├── resource              URL spelling: shipments, rate-master
├── action                one of 15 (PermissionAction)
├── description
├── isSystemPermission    seeded: read-only, never editable or deletable
├── status                ACTIVE | INACTIVE
├── displayOrder          module base + action offset
└── requiredFeatureFlag   subscription feature required, or null
```

`resource` is stored separately from `module` so an authorisation filter can eventually
map a request path to a permission without a lookup table.

`requiredFeatureFlag` is **not in the requested field list** but was kept deliberately:
the enum it replaced carried plan gating, and dropping it would have silently removed a
control that already existed.

### Modules and actions

28 modules: `AUTH COMPANY USER ROLE PERMISSION BRANCH HUB CUSTOMER ADDRESS PINCODE
RATE_MASTER ROUTE_MASTER SHIPMENT TRACKING MANIFEST PICKUP DELIVERY DRIVER VEHICLE VENDOR
WALLET PAYMENT INVOICE REPORT DASHBOARD SETTINGS NOTIFICATION AUDIT`.

15 actions: `CREATE READ UPDATE DELETE SEARCH EXPORT IMPORT APPROVE REJECT PRINT UPLOAD
DOWNLOAD ASSIGN ACTIVATE DEACTIVATE`.

**Not the 28 × 15 cross product.** `DefaultPermissionCatalog` declares which actions
exist per module, because `DASHBOARD_DELETE` and `TRACKING_APPROVE` are not rights anyone
can hold — seeding 420 rows would put mostly-meaningless entries in front of every
operator building a role, and each would look grantable. Three deliberate shapes:

- **`TRACKING`** has no `UPDATE` or `DELETE`. A scan history that can be rewritten is not
  evidence.
- **`AUDIT`** is `READ`, `SEARCH`, `EXPORT` only — append-only by design.
- **`PAYMENT`, `INVOICE`, `WALLET`** have no `DELETE`. Financial records are corrected by
  further records, never removed.

`READ` and `SEARCH` are separate: reading one shipment you were given the number for is
not the same right as listing every shipment in the company, and support staff routinely
get the first without the second.

## Role–permission mapping

```
RolePermission (company-owned)
├── id, companyId
├── roleId          -> company_roles.id   (FK, CASCADE)
├── permissionId    -> permissions.id     (FK, RESTRICT)
└── permissionCode  denormalised copy of the catalogue code
```

`companyId` is redundant with the role's own and deliberately so: it lets the Hibernate
filter apply to this table directly, without joining `company_roles` on every read.

`permissionCode` is denormalised because **every authorisation decision needs the code,
not the id** — this turns "what may this role do" into one indexed read instead of a join
to a platform-level table on the hot path. Safe because the code is immutable, which is
exactly why it was made immutable.

Users inherit permissions through roles. `resolveEffectiveCodes(roleIds)` collapses
duplicates across roles and is what User Management will call.

## Business rules

| Rule | Where enforced |
|---|---|
| `permissionCode` unique, derived as `MODULE_ACTION` | `Permission.applyInvariants` + DB unique keys |
| `(module, action)` unique | DB unique key |
| System permissions are **read-only** — no edit, no delete | `Permission.requireEditable`, 422 |
| A permission still granted anywhere cannot be deleted | service checks grants across all companies, 422 |
| Only `SUPER_ADMIN` writes the catalogue | per-method `@PreAuthorize` |
| Only `COMPANY_ADMIN` grants, and only in their own company | `@PreAuthorize` + company predicate |
| A company cannot grant a permission its plan excludes | checked against seeded `feature.*` settings |
| An `INACTIVE` permission cannot be granted | service, reported as `rejected` |
| A change may not remove the company's last `ROLE_UPDATE` | lockout guard, 422 |
| Soft delete only | `BaseEntity.softDelete` |
| Optimistic locking on update | client-supplied `version`, 409 |

### Plan gating without a cross-module call

`SHIPMENT_IMPORT` and `CUSTOMER_IMPORT` require the `bulkBooking` feature. The check
reads the company's own `feature.*` **settings**, seeded from the plan at company
creation — not the subscription module. Those rows exist precisely so a company-scoped
caller can answer this without reaching across a module boundary. A missing or
non-`true` value **denies**: gating fails closed.

### Lockout protection, and the bug it had

A company that revokes `ROLE_UPDATE` everywhere can never fix its own permissions again;
only support can rescue it. So a change that removes the last active holder is refused.

**The first implementation guarded on the after-state alone**, which refused *every*
grant in a company that had no `ROLE_UPDATE` holder — including the grant that would fix
it. The guard now compares before and after and only fires when the ability is **lost**.
Found by running it, not by the unit tests, which had mocked the holder as present.

## Security

| Actor | Catalogue | Grants |
|---|---|---|
| `SUPER_ADMIN` | full write | **read only** — may inspect, never change what a company's staff can do |
| `COMPANY_ADMIN` | read only | full, own company only |
| Anyone else | 403 | 403 |

Company isolation on grants is the same two layers as roles: the Hibernate filter, plus
`findByIdWithinCompany` on the role — a primary-key load bypasses the filter, so without
it one company could grant permissions on another's role. Another company's role id
returns **404, not 403**.

## API

| Method | Path | Role |
|---|---|---|
| `POST` | `/api/v1/permissions` | `SUPER_ADMIN` |
| `PUT` | `/api/v1/permissions/{id}` | `SUPER_ADMIN`, refused for system permissions |
| `GET` | `/api/v1/permissions/{id}` | `SUPER_ADMIN`, `COMPANY_ADMIN` |
| `GET` | `/api/v1/permissions` | paged, sorted, filtered, searchable |
| `GET` | `/api/v1/permissions/grantable` | all ACTIVE, unpaged — for a permission matrix |
| `DELETE` | `/api/v1/permissions/{id}` | `SUPER_ADMIN`, soft delete |
| `POST` | `/api/v1/roles/{roleId}/permissions` | `COMPANY_ADMIN` — **bulk** assign |
| `GET` | `/api/v1/roles/{roleId}/permissions` | `COMPANY_ADMIN` (own), `SUPER_ADMIN` (any) |
| `DELETE` | `/api/v1/roles/{roleId}/permissions/{permissionId}` | `COMPANY_ADMIN`, idempotent |

Assignment is **bulk by design**: a permission matrix submits the whole set in one
transaction, so a role is never left half-configured. `replaceExisting=true` makes the
role hold exactly the supplied set — what a "save" button means.

The response reports four lists, because "it worked" is not the whole truth:

```json
{ "granted": ["SHIPMENT_READ"], "revoked": [], "skipped": ["SHIPMENT_CREATE"],
  "rejected": ["CUSTOMER_IMPORT"], "effectivePermissions": ["..."] }
```

`rejected` is the field a UI must surface — a permission outside the plan is silently
useless if the response only says how many were saved.

### List parameters

`module` and `action` (both repeatable), `status`, `isSystemPermission`, `resource`,
`planGatedOnly`, `search`, plus paging and sorting. `size` capped at **200**, higher than
elsewhere because a permission matrix legitimately fetches a whole module at once.

### Errors

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation, unknown sort key |
| 403 | `ACCESS_DENIED` | Wrong tier for the operation |
| 404 | `RESOURCE_NOT_FOUND` | Unknown id, unknown permission code, another company's role |
| 409 | `DUPLICATE_RESOURCE` | `(module, action)` already exists |
| 409 | `CONCURRENT_MODIFICATION` | Stale `version` |
| 422 | `BUSINESS_RULE_VIOLATION` | System permission edit/delete, still-granted delete, lockout |

## Database changes — `V6__permission_management.sql`

1. `permissions` — platform-level, no `tenant_id`; unique on `permission_code` and on
   `(module, action)`; indexed by module+order, status and resource.
2. **Seeds all 174 system permissions**, generated from `DefaultPermissionCatalog` so the
   SQL and the Java cannot drift. `DefaultPermissionCatalogTest` asserts the count.
3. `role_permissions` — company-owned; unique on `(tenant_id, role_id, permission_id)`;
   FK to `company_roles` **CASCADE**, FK to `permissions` **RESTRICT** (a permission
   still granted must not be removable).
4. **Carries every existing grant across.** The old constants were coarser: `X_MANAGE`
   became `X_CREATE` + `X_UPDATE` + `X_DELETE`, `X_VIEW` became `X_READ`,
   `SHIPMENT_VIEW` also gained `SHIPMENT_SEARCH`, `SCAN_CREATE` became
   `TRACKING_CREATE` + `TRACKING_READ`, `BULK_BOOKING` became `SHIPMENT_IMPORT`.
   In the dev database 29 old rows became 39 new ones.
5. Drops `company_role_permissions`.

**One old constant was dropped deliberately:** `API_ACCESS`. API access is a plan feature
enforced by rate limiting, not a right a role holds, and no seeded role had it granted.

## Audit

`PERMISSION_CREATED`, `PERMISSION_UPDATED`, `PERMISSION_DELETED` for the catalogue;
`ROLE_PERMISSIONS_ASSIGNED` (with granted/revoked/rejected lists) and
`ROLE_PERMISSION_REVOKED` for grants, recorded against the role.

## Tests

38 new unit tests (257 in the suite): `PermissionTest` (derived code, defaults, read-only
system rows, gating fails closed on missing/false/non-boolean/null flags),
`DefaultPermissionCatalogTest` (174 count as a tripwire against the migration, unique
codes, destructive actions withheld where they matter), `RolePermissionServiceImplTest`
(gating both ways, inactive rejected, skip vs grant, replace-revokes, unknown code 404,
lockout prevented *and* not over-applied, company scoping, idempotent revoke, batched
counts).

## Verified by running it

Against MySQL 8.0.46 on 2026-07-22, with the existing Phase-3 fixtures in place so the
data migration ran for real:

- `V6` applied; `ddl-auto: validate` passed; 174 permissions across 28 modules; 29 old
  grants became 39; `company_role_permissions` dropped.
- Catalogue: `SUPER_ADMIN` 174 rows, `COMPANY_ADMIN` read-only (403 on write), 401
  unauthenticated.
- Create derived `SHIPMENT_APPROVE` with name "Approve Shipments", resource `shipment`,
  displayOrder 138; duplicate `(module, action)` 409.
- System permission update and delete both 422; custom permission update 200.
- Grants: assign 3 → granted; re-assign → skipped; `replaceExisting` revoked the other 4;
  unknown code 404; `SUPER_ADMIN` grant attempt 403.
- **Plan gating both directions**: `CUSTOMER_IMPORT` rejected with no `feature.bulkBooking`
  setting, granted once the setting was added.
- **Delete guard**: granting the custom permission then deleting it → 422 naming the
  count; revoke then delete → 200.
- **Cross-company**: assigning to and revoking from the rival company's role both 404, and
  that role still had 0 grants afterwards.

## Next

- [ ] **User Management** — assign roles to users, honour `isDefault`, and decide what
      happens to holders when a role is deleted.
- [ ] **Authorise on permissions, not JWT role names.** `@PreAuthorize` still checks
      roles, so re-permissioning a role does not yet change what its users can reach.
      This is the module that makes the catalogue actually load-bearing.
- [ ] Cache effective permissions per user; today every check would be a query.
- [ ] Method-security integration slice for the catalogue/grant split.
