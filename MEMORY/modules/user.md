# Module: user (User Management)

**Status:** DONE and verified against MySQL 8.0.46 (Phase 3, v0.7.0).
**Update (Branch RBAC, 2026-07-30):** `BRANCH_MANAGER` can now create and staff their own
branch's users — branch responsibilities #1 and #2 — instead of every write being
`COMPANY_ADMIN`-only. See *Security* and *REST APIs* below, and
`MEMORY/modules/branch.md` §"A branch manager staffs their own branch". **Code complete,
not yet run against MySQL** — verified with `UserServiceImplTest` only.
**Package:** `com.courier.modules.company` — users are administered inside the company
context.
**Depends on:** `shared`; `modules/auth` for `PasswordPolicy` (application-layer reuse).
**Depended on by:** every module that acts on behalf of a user, once authorisation is
switched to permission codes (the remaining Phase-3 follow-up).

## The shared-kernel decision

`modules/auth` created the `users` table in V2 to authenticate people. The spec's User
adds ~25 HR/profile fields. Rather than a second table that would drift from the login
source of truth, **V7 extends `users`** and the table becomes a *shared kernel*:

| Context | Entity | Maps |
|---|---|---|
| auth | `auth.User` (`@Entity "User"`) | login fields + the `Role`-enum element collection + token bookkeeping |
| company | `company.User` (`@Entity "CompanyUser"`, table `users`) | login fields + the HR/profile columns |

Two entities, one table, each modelling the columns its context cares about — a
recognised DDD pattern and the least-plumbing way to honour "extend the users table"
without company reaching into auth's repository. Both map the shared columns (email,
status, `failed_attempts`, `last_login_at`) with identical types, so `ddl-auto: validate`
passes for both.

Two collisions this forced, both resolved:
- **JPA entity name** — company's is `@Entity(name = "CompanyUser")`, so two `User`
  classes do not clash on the default name.
- **Spring Data bean name** — company's repository is `CompanyUserRepository`, so it does
  not clash with auth's `UserRepository` bean.

Both were caught by booting, not by unit tests.

## Entity

```
User (company context, company-owned, table `users`)
── identity ──  employeeCode (unique per company, UPPER, immutable), employeeId
── name ─────  firstName, middleName, lastName, displayName
── contact ──  email (unique per company, lower), username (GLOBALLY unique, lower),
               mobile, alternateMobile, passwordHash (never serialised)
── HR ───────  gender, dateOfBirth, designation, department, joiningDate,
               reportingManagerId (a user of the same company), profileImage, remarks
── placement ─ branchId, hubId          single columns — one branch, one hub
── state ────  status (PENDING|ACTIVE|LOCKED|DISABLED), isLocked, lastLogin (read-only),
               failedLoginCount
```

Plus `BaseEntity` audit/version columns; the API presents the timestamps as
`createdDate`/`updatedDate`.

### UserRole (the many-to-many)

```
UserRole (company-owned, table `user_company_roles`)
├── userId  -> users.id          (FK CASCADE)
├── roleId  -> company_roles.id  (FK RESTRICT)
└── roleCode  denormalised, immutable
```

This is **not** auth's `user_roles`. That table stays as auth's element collection of the
JWT-authority `Role` enum. `user_company_roles` links a user to the company's own
permissioned roles. Keeping them apart is why assigning a role here does not yet change
the JWT — wiring authorisation onto these grants is the deferred follow-up.

### Branch / Hub

The spec listed `UserBranch`/`UserHub` entities, but the rules say a user belongs to *at
most one* branch and one hub. That is a column, not a join table — `branchId`/`hubId` on
the user row enforce "one" structurally. No FK yet: the Branch and Hub modules do not
exist, the same reason the `users.tenant_id` FK is still deferred.

### Two lock mechanisms, kept distinct

- `is_locked` — **admin hard-lock**, set by the lock endpoint, cleared only by unlock. A
  scheduled job never touches it.
- `locked_until` — auth's **automatic** failed-login lock, which lapses on its own.

`isOperational()` requires `ACTIVE` **and** not hard-locked, and mirrors auth's login gate
so the two contexts agree on who may authenticate.

## Business rules

| Rule | Where |
|---|---|
| Email unique per company | service pre-check + `uk_users_tenant_email` |
| Username globally unique | service pre-check + `uk_users_username` |
| Employee code unique per company | service pre-check + `uk_users_tenant_employee_code` |
| One branch / one hub per user | single columns |
| Reporting manager must be a user of the same company, not self | service |
| Password meets the policy | reuses auth's `PasswordPolicy` |
| Cannot lock / deactivate / delete your own account | service self-guard |
| Soft delete only; optimistic locking on update | `BaseEntity`, client `version` |

All three uniqueness checks count **soft-deleted rows** (native count queries, `BIGINT`
compared in Java) — a deleted user keeps their email, username and employee code reserved.

## User lifecycle

```
create ──► PENDING ──reset-password──► ACTIVE ◄──activate── DISABLED
 (no pwd)                              (pwd set)     │           ▲
                                                     └deactivate─┘
create (pwd) ──► ACTIVE                     any ──lock──► LOCKED ──unlock──► ACTIVE
```

- Create **without** a password → PENDING with an unusable random hash; an admin
  reset-password activates it. Create **with** a password → ACTIVE.
- Deactivate → DISABLED (auth's non-login state), reversible with activate.
- Lock → hard LOCKED; unlock clears the lock and the failed-login counter.
- Delete → soft delete + deactivate, so a restored row is not silently live.

## Security

| Actor | Reach |
|---|---|
| `COMPANY_ADMIN` | full management, own company only |
| `SUPER_ADMIN` | read across all companies, no writes |
| `BRANCH_MANAGER` | create, update, activate/deactivate, assign/remove role — **their own branch only**; read only their own branch |
| `HUB_MANAGER` | read only users at **their own** hub |

Per-method `@PreAuthorize` on `UserServiceImpl`; the branch/hub scope is applied in code,
because no URL rule can say "their branch". A manager's scope comes from their own user
row, never the request; a manager with no placement sees nobody. Out-of-scope-but-visible
reads return **404, not 403** — telling a manager "this user exists but is not at your
branch" leaks headcount; a manage attempt on a colleague of the *same company* but a
foreign branch is **403** (`requireManageableByCaller`), the same "reachable but not
yours to manage" answer Branch itself gives (`MEMORY/modules/branch.md` §Security).
`SecurityConfig` only requires authentication on `/api/v1/users/**`.

**A branch manager's write is narrower than an admin's in three ways**, all enforced in
`UserServiceImpl`, not just at the gate:
1. **Placement is pinned to their own branch.** `create`/`update` force `branchId` to the
   caller's own branch (a different branch, or any `hubId`, is 403) — a branch manager
   cannot staff another branch or place someone at a hub.
2. **Only branch-assignable roles.** `assignRole` (and the role list on `create`) checks
   `DefaultRoleCatalog.isBranchAssignable` — `BRANCH_MANAGER`, `BOOKING_OPERATOR`,
   `DELIVERY_OPERATOR`, `ACCOUNTS`, or any of the company's own custom roles; never
   `COMPANY_ADMIN`, `HUB_MANAGER`, `FINANCE_USER`, `CUSTOMER_SERVICE` or `VIEWER`.
3. **`delete`, `lock`, `unlock`, `resetPassword`, `assignBranch` and `assignHub` stay
   `COMPANY_ADMIN`-only.** Locking a colleague out, resetting their password or moving
   them to another branch is not "staffing a counter" — see `UserServiceImpl`'s class
   javadoc for the line drawn.

Company isolation is the project's two layers: the Hibernate filter, plus
`findByIdWithinCompany` on every single-row load (a primary-key load bypasses the filter).

## REST APIs

| Method | Path | Who |
|---|---|---|
| `POST` | `/api/v1/users` | `COMPANY_ADMIN` (any) / `BRANCH_MANAGER` (their own branch, branch-assignable roles only) |
| `PUT` | `/api/v1/users/{id}` | `COMPANY_ADMIN` (any) / `BRANCH_MANAGER` (own branch) |
| `GET` | `/api/v1/users/{id}` | admins + scoped managers |
| `GET` | `/api/v1/users` | admins + scoped managers; paged/sorted/filtered |
| `DELETE` | `/api/v1/users/{id}` | `COMPANY_ADMIN`, soft delete |
| `PATCH` | `…/activate` `…/deactivate` | `COMPANY_ADMIN` (any) / `BRANCH_MANAGER` (own branch) |
| `PATCH` | `…/lock` `…/unlock` | `COMPANY_ADMIN` |
| `PATCH` | `…/reset-password` | `COMPANY_ADMIN` (no current password) |
| `PATCH` | `…/change-password` | self-service (current password required) |
| `POST` | `…/roles` · `DELETE` `…/roles/{roleId}` | `COMPANY_ADMIN` (any role) / `BRANCH_MANAGER` (own branch, branch-assignable roles only) |
| `PATCH` | `…/branch` · `…/hub` | `COMPANY_ADMIN` (null id clears) |

`change-password` is a 15th endpoint beyond the spec's 14, giving `ChangePasswordRequest`
a home for self-service change; the admin path is `reset-password`.

List sort whitelist: `employeeCode`, `firstName`, `lastName`, `displayName`, `email`,
`username`, `designation`, `department`, `status`, `joiningDate`, `createdDate`,
`updatedDate`. `size` capped at 100. `search` covers name, email, username, employee code
and mobile, wildcard-escaped. `roleCode` filters via an EXISTS subquery on
`user_company_roles`.

### Errors

400 validation / bad sort · 401 unauthenticated · 403 wrong tier · 404 unknown,
foreign, or out-of-scope id · 409 duplicate email/username/employee code · 409 stale
version · 422 self-action, wrong current password, inactive role, invalid manager.

## Database changes — `V7__user_management.sql`

- ALTER `users` ADD the HR/profile columns, `username`, `mobile`/`alternate_mobile`,
  `branch_id`/`hub_id`, `is_locked`, `gender` (default `UNSPECIFIED`).
- New unique keys: `uk_users_username` (global), `uk_users_tenant_employee_code`.
- New indexes: `idx_users_tenant_branch`, `idx_users_tenant_hub`.
- New table `user_company_roles`: FK to `users` CASCADE, to `company_roles` RESTRICT,
  unique `(tenant_id, user_id, role_id)`.
- **Not done**: the `users.tenant_id -> companies.tenant_id` FK (still blocked by the
  orphan `ops@acme.test` row), and branch/hub/manager FKs (those modules do not exist).

## Events

`UserEvent` is sealed, consumed `@TransactionalEventListener(AFTER_COMMIT)`:
`UserCreated`, `UserUpdated`, `UserActivated`, `UserDeactivated`, `UserLocked`,
`UserUnlocked`, `PasswordReset`, `RoleAssigned`, `RoleRemoved`, `BranchAssigned`,
`HubAssigned`. Today they log; the listener is where token revocation on lock/deactivate
and welcome emails will attach.

## Audit

`USER_CREATED` (auth's, reused), `USER_UPDATED` (changed fields only), `USER_ACTIVATED`,
`USER_DEACTIVATED`, `USER_LOCKED`, `USER_UNLOCKED`, `USER_PASSWORD_RESET`,
`USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`, `USER_BRANCH_ASSIGNED`, `USER_HUB_ASSIGNED`,
`DELETED`.

## Tests

35 new unit tests (292 in the suite): `UserTest` (normalisation, names, lifecycle,
operational-mirror, self-manager, soft delete) and `UserServiceImplTest` (create
password/status/default-role, three uniqueness rules, inactive-role rejection, stale
version, company-scoped load, self-guards on lock/deactivate/delete, idempotent
role/lifecycle ops, reset re-enabling PENDING, self-only change-password, batched role
counts).

**Branch RBAC, 2026-07-30:** 8 further cases in `UserServiceImplTest` (28 total): a branch
manager creates a user for their own branch and is refused for another branch or a hub
placement; update/activate/deactivate refuse a foreign-branch user and accept their own;
`assignRole` accepts a branch-staff role and the company's own custom role, and refuses
`COMPANY_ADMIN`.

## Verified by running it

Against MySQL 8.0.46 on 2026-07-23, using the existing Phase-3 company fixtures:

- `V7` applied; `ddl-auto: validate` passed for **both** entities on `users`.
- 401 anon; `SUPER_ADMIN` write 403; `COMPANY_ADMIN` create 201.
- Create without a password → PENDING + default role `BOOKING_OPERATOR`,
  `passwordGenerated:true`; with a password → ACTIVE. Email/username lowercased,
  employee code uppercased.
- Duplicate email, username and employee code each 409; codes stay reserved after a
  soft delete (recreate 409).
- Assign/remove role idempotent; branch and hub assignment set and clear the columns;
  activate/deactivate/lock/unlock move status correctly; reset re-enabled a PENDING user;
  self-deactivate 422.
- **Cross-company**: a foreign user id returned 404 on GET, PUT, DELETE and role-assign;
  not present in the other company's search; untouched afterwards. Super admin saw all 4
  users across companies.
- **Branch-manager scoping**: a manager saw only their branch's users (2 of 4), 200 for a
  same-branch user, 404 for a different-branch user, 403 on write.
- Bad sort 400; stale version 409; all lifecycle audit actions written.

## Next

- [ ] Exercise the branch-manager write scoping over HTTP (own-branch create/update/
      activate/deactivate/assign-role, foreign-branch 403, hub placement 403,
      `COMPANY_ADMIN` assign refused) — code complete, not yet run against MySQL.
- [ ] **Switch `@PreAuthorize` to permission codes.** Users now hold company roles, roles
      hold permissions — resolve a user's effective codes (`RolePermissionService`) and
      authorise on them. This makes the whole Phase-3 catalogue load-bearing.
- [ ] Reflect company-role assignments into the JWT (or replace the auth enum roles), so a
      newly assigned role changes what the token can do.
- [ ] Token revocation on lock / deactivate / delete, via the event listener.
- [ ] `users.tenant_id -> companies.tenant_id` FK once the orphan row is reconciled.
- [ ] Bulk user import (the spec flagged it "future ready").
