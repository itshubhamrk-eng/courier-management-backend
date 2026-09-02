# Module: branch (Branch Management)

**Status:** DONE and verified against MySQL 8.0.46 (Phase 4, v0.9.0). Extended 2026-08-12
(`V25`): four branch-level charge percentages — `gstPercentage` (default 18),
`commissionOnOtherCharges` (default 20), `commissionOnBasicFreight` (default 10),
`companyServiceChargePercentage` (default 10). Optional-with-default on create, required
on update (full-replacement PUT, same as every other editable field). **Code complete,
not yet run against MySQL** — see `CHANGELOG.md` 0.17.8. Extended 2026-08-13 (`V32`): a
fifth charge, `drsChargePerQty` (default 2.00) — same optional-on-create/required-on-update
treatment as the other four, but a **fixed amount, not a percentage**. Consumed by
Shipment Movement's `deliver()` to compute a per-delivery DRS wallet **credit** (shipped
as a debit on a miscommunication, fixed 2026-08-14) — see
`MEMORY/modules/shipment-movement.md`'s "DRS charge per qty" and
`MEMORY/modules/branch-wallet.md`'s `DRS` sub-transaction type. **Code complete, not yet
run against MySQL** — see `CHANGELOG.md` 0.21.1/0.24.2. Extended 2026-07-29:
creating a branch also creates its **login account**, and then that account's **company
role** (see *Creating a branch creates four things*). The role half is **code complete, not
yet run against MySQL**.
**Extended 2026-07-30:** the eleven responsibilities and four staff roles were made real —
see *What a branch runs* and *A branch manager staffs their own branch* below. No new
endpoint or table for Branch itself; the changes live in `role`, `permission` and `user`
(`MEMORY/modules/role.md`, `MEMORY/modules/user.md`) and in `V13__branch_operations_permissions.sql`.
**Package:** `com.courier.modules.company`.
**Depends on:** `shared`; the `users` table (managers and placements are company users);
auth's `UserProvisioningService` for the branch's own account;
`BranchRoleProvisioningService` for its company role.
**Depended on by:** Hub, Shipment, Manifest, Pickup, Delivery (serviceability + origin)
as they land.

## Purpose

A branch is a physical booking / delivery office of a company. A company has many; each
belongs to one company. This is the first module of Phase 4 — Organization
Structure — and the anchor the operational modules attach to.

## Creating a branch creates four things

One `POST /branches` yields a branch, a user, that user's role and a wallet. They are not
equal partners:

| Thing | Where the rule lives | Transaction |
|---|---|---|
| Branch | `BranchServiceImpl.create` | the request's |
| **User** | `BranchServiceImpl.create`, through auth's `UserProvisioningService` | **the same one** |
| **Branch manager role** | `BranchRoleProvisioningService.ensureBranchManagerRole` | **the same one** |
| Wallet + wallet number | Finance's `WalletProvisioningListener` on `BranchCreated` | separate, AFTER_COMMIT |

The user is in the same transaction because a branch nobody can sign in to is not a branch
anyone asked for — if the account cannot be made, the branch should not exist either. The
role joins it for the same reason: an account holding no role is the half-provisioned state
the single transaction exists to prevent. The wallet stays outside it for the reason it
always did: a Finance failure must not roll back a company's branch, and
`getOrCreateForBranch` repairs a miss on first read. A wallet can be created later from
nothing; an account cannot, because its password is only ever readable in the response to
this one call. **That is why the wallet is not in the create response** — it does not exist
yet when the response is built. Read it from `GET /api/v1/branch-wallet`.

### The default branch manager role

`ensureBranchManagerRole` is an **ensure, not a create**. The company's `BRANCH_MANAGER`
role normally already exists — `CompanyProvisioningService` seeds all eight at company
creation — and this grants it to the new account by writing a `user_company_roles` row. It
creates the role only when the company has none, which happens to a company provisioned
before the catalogue carried it or one whose administrator deleted it, and reactivates it
when it has been withdrawn (a role that grants nothing is not a role a branch can run on).

**A role per branch was the obvious reading and the wrong one.** A hundred branches would
mean a hundred rows in `company_roles` saying the same thing, and re-permissioning "branch
managers" would mean editing all hundred. What has to be true is that the account created
with the branch can manage it on day one and that the role behind it exists.

**Two different things are called a role here, and they are not the same.** Auth's
`Role.BRANCH_MANAGER` is the JWT authority every `@PreAuthorize` reads today, set by
`UserProvisioningService`. The `user_company_roles` grant is the permissioned role the
company manages — it governs nothing yet and governs everything once authorisation moves
onto permissions. Setting only the first is what previously left the branch account holding
a role that appeared nowhere in the Roles screen.

Both halves are idempotent: a branch code reused after a soft delete, or a retried create,
must not produce a second role or a duplicate grant. `uk_user_company_roles_user_role` is
the backstop for a race. Permissions on a recreated role are filtered by the company's
seeded `feature.*` settings, not the subscription module (decision 30) — the same source
`RolePermissionServiceImpl` reads, so a role created here can never hold a right the Roles
screen would refuse to grant.

The create response reports which role was granted, in `branchUser.roleId` /
`branchUser.roleCode`. The frontend credentials dialog prints that rather than the literal
string "Branch Manager", so a company that renames the role sees its own name.

**The `branchUser` block is optional, the user is not.** Given an email, that address is used
as typed, and a duplicate fails the whole create with 409 — silently signing the
administrator in as `latur-2@…` is worse than the error. Absent, the address is derived as
`<branch-code>@<company-code>.local` (`.local` so it can never be mistaken for a mailbox that
receives verification mail) and suffixed `-2`, `-3`… on collision, since a derived address
carries no intent to preserve.

The account is `BRANCH_MANAGER`, is placed at the branch (`users.branch_id`) and becomes the
branch's manager **unless** a `managerId` was supplied — an explicit choice is never
overwritten; the new account is then staff.

Password: the administrator's if they typed one, otherwise generated (14 chars, no
0/O/1/l/I — it is read off a screen and typed by someone else). Either way it goes through
`PasswordPolicy`. A generated one is returned **once** in `branchUser.temporaryPassword` and
appears in no log, no audit detail and no later read. This is a deliberate departure from
decision 21 (a company's first admin gets an unusable password): a branch is opened by an
admin who hands the credentials over in person, and there is no mailbox at a branch counter
to receive a verification link. The cost is real — a credential exists in one API response —
and it is why the frontend shows it in a dialog that says it cannot be shown again.

## What a branch runs

The eleven things a branch does, in the order a shipment moves through them, and the
permission code(s) behind each:

| # | Responsibility | Permission code(s) |
|---|---|---|
| 1 | Create branch users | `USER_CREATE` |
| 2 | Assign menus | `MENU_READ`, `MENU_ASSIGN` — today this *is* assigning a company role: a role's permissions decide the menus its holder sees, and there is no separate Menu module. See *A branch manager staffs their own branch* below. |
| 3 | Recharge wallet | `WALLET_RECHARGE` |
| 4 | Book shipment | `SHIPMENT_CREATE`, plus the customer half — see *Booking flow* below |
| 5 | Create manifest | `MANIFEST_CREATE` |
| 6 | Assign vehicle | `MANIFEST_ASSIGN`, `VEHICLE_ASSIGN` |
| 7 | Dispatch manifest | `MANIFEST_DISPATCH` |
| 8 | Receive manifest | `MANIFEST_RECEIVE` |
| 9 | Out for delivery | `DELIVERY_DISPATCH` |
| 10 | Deliver shipment | `DELIVERY_DELIVER` |
| 11 | Reports | `REPORT_READ` and friends |

`DefaultRoleCatalog.BRANCH_MANAGER` holds every one of these codes (see its javadoc,
which lists the eleven in the same order). **Four roles split the same eleven across a
branch's staff** — a manager does not do all of it personally:

| Role | Runs | Never |
|---|---|---|
| `BRANCH_MANAGER` | all eleven | — |
| `BOOKING_OPERATOR` | #4 (book), reads #5/#11 | manifest, delivery, wallet writes |
| `DELIVERY_OPERATOR` | #8, #9, #10 | booking, pricing, wallet |
| `ACCOUNTS` | #3 (recharge), reads #5/#11, plus payments/invoices (not one of the eleven, but the same desk) | booking, the road |

### Booking flow

Search the customer by mobile; reuse if found, create if not; then create the shipment.
`BOOKING_OPERATOR` holds `CUSTOMER_READ`/`SEARCH`/`CREATE`/`UPDATE` for exactly this —
`CUSTOMER_DELETE` is deliberately absent (see `MEMORY/modules/role.md`). This is Shipment's
own flow to implement; Branch only needs the permission shape ready for it, which is why
this section exists before Shipment does.

### Payment modes

`PAID` debits the booking branch's wallet immediately. `TO_PAY` does not debit the booking
branch; the *delivery* branch's wallet is debited on `DELIVERY_DELIVER` — the reason
`DELIVERY_DELIVER` is its own permission code rather than folded into `DELIVERY_UPDATE`,
same as `MEMORY/modules/branch-wallet.md`'s money-path reasoning. `COD` and `TBB` are
API-ready (payment mode master data exists) but have no booking flow behind them yet —
Shipment's job.

## A branch manager staffs their own branch

Responsibilities #1 and #2 were **documented but inert** until 2026-07-30: `DefaultRoleCatalog`
already listed `USER_CREATE`/`MENU_ASSIGN` on `BRANCH_MANAGER`'s permission set, but every
write on `UserController` was `@PreAuthorize(WRITERS)` with `WRITERS = COMPANY_ADMIN` only
— a branch manager's JWT authority never satisfied it, so the permission a role document
promised was not a permission the code would honour. Three things closed the gap, none of
which touch Shipment:

1. **`UserServiceImpl.create`/`update`/`activate`/`deactivate`/`assignRole`/`removeRole`**
   now admit `BRANCH_MANAGER` too (`BRANCH_WRITERS`), scoped in code to the caller's own
   branch by `requireManageableByCaller` — a colleague of a foreign branch in the same
   company is **403**, mirroring Branch's own "reachable but not yours to manage" answer.
   `delete`, `lock`, `unlock`, `resetPassword`, `assignBranch` and `assignHub` stay
   `COMPANY_ADMIN`-only: locking someone out or moving them to another branch is not
   staffing a counter. See `MEMORY/modules/user.md`.
2. **`DefaultRoleCatalog.isBranchAssignable`** — referenced in this class's own javadoc
   since the `BRANCH_ROLE_CODES` set was written, but never implemented until now — gates
   *which* roles a branch manager may hand out: the four in the table above, or any of the
   company's own custom (non-system) roles; never `COMPANY_ADMIN`, `HUB_MANAGER`,
   `FINANCE_USER`, `CUSTOMER_SERVICE` or `VIEWER`. See `MEMORY/modules/role.md`.
3. **`V13__branch_operations_permissions.sql`** seeds the 8 permission codes responsibilities
   #2, #6, #7, #8, #9, #10 and #3 needed — `MENU_READ`, `MENU_ASSIGN`, `MANIFEST_ASSIGN`,
   `MANIFEST_DISPATCH`, `MANIFEST_RECEIVE`, `DELIVERY_DISPATCH`, `DELIVERY_DELIVER`,
   `WALLET_RECHARGE`. `DefaultPermissionCatalog` already declared them (219 codes); the
   migration, and `DefaultPermissionCatalogTest`'s tripwire (211), had not caught up —
   `mvn test` was red before this pass. Existing companies are not back-filled (same rule
   V11 set for Master Data): new companies pick the codes up automatically because
   `DefaultRoleCatalog` derives each seeded role's permissions from the catalogue.

**Code complete, not yet run against MySQL** — like the rest of this file's 2026-07-29
extension, `V13` has never touched a real database. See `MEMORY/AI_CONTEXT.md` Next Task.

## Entity relationships

```
Company
  └── Branch *          company-owned; branchCode/branchName unique per company
        ├── managerId  -> a User of the same company (one manager per branch)
        └── (Users placed here via users.branch_id)
```

- **Branch → Company**: every branch carries `company_id`; there is no separate company FK
  because a company owns its own rows.
- **Branch → manager (User)**: the `managerId` column; validated in the service to be a
  user of the same company. No DB FK yet (see below).
- **User → Branch**: `users.branch_id` (from V7). "Assign users" sets it. No DB FK yet.

**FKs deferred, deliberately** — following the project pattern of not adding a
cross-entity FK until the data is stable:
- `users.branch_id -> branches.id`: the dev database already holds user rows with random
  test `branch_id`s, so the constraint would fail on boot. The service validates
  placements instead.
- `branches.manager_id -> users.id`: validated in the service; the FK is left off for the
  same reason the `users.company_id` FK still is.

## Entity

`Branch` extends `CompanyOwnedEntity` (`@Filter`, `@SQLRestriction("deleted = false")`).

- **Identity** — `branchCode` (unique per company, UPPER, spaces→underscores, editable —
  no other table FKs it, everything references the branch by id), `branchName` (unique
  per company, case-insensitive).
- **Classification** — `branchType` (`HEAD_OFFICE | REGIONAL_OFFICE | BOOKING_BRANCH |
  DELIVERY_BRANCH | BOOKING_DELIVERY_BRANCH`), `status` (`ACTIVE | INACTIVE`).
- **Contact** — email (lowercased), mobile, alternateMobile, managerId.
- **Address** — line1/2, country, state, city, district, taluka, postalCode,
  latitude/longitude (`DECIMAL(9,6)`, range-checked).
- **Hours** — openingTime, closingTime (closing must be after opening),
  workingDays (uppercase CSV of `MON..SUN`, de-duplicated and validated).
- **Capability flags** — allowBooking, allowDelivery, allowPickup, allowManifest,
  allowCashCollection (default true), allowWallet (default false). These are the operative
  switches; `branchType` is descriptive intent.
- Plus `BaseEntity` audit/version columns.

## Business rules

| Rule | Where |
|---|---|
| Branch code unique within company | service pre-check + `uk_branches_company_code` |
| Branch name unique within company (case-insensitive) | service pre-check |
| One manager per branch | single `managerId` column |
| Manager must be a company user | service validation |
| Closing after opening; valid coordinates & working days | `Branch.applyInvariants` |
| Soft delete only; optimistic locking | `BaseEntity`, client `version` |

Uniqueness counts **soft-deleted** rows (native count, compared in Java) — a deleted
branch keeps its code and name reserved.

> Note: the earlier company.md planning mentioned "exactly one HEAD_OFFICE per company".
> The Phase-4 spec did not list it as a rule, so it is **not** enforced here; add it later
> if the product requires it.

## Security

| Actor | Reach |
|---|---|
| `COMPANY_ADMIN` | manage every branch in the company |
| `BRANCH_MANAGER` | update + assign users to the branch they manage (`managerId` = them) |
| `SUPER_ADMIN` | read across companies |
| Any other company user | read only the branch they are placed at (`user.branchId`) |

Per-method `@PreAuthorize` gates the tier; the finer "your branch" is enforced in code
from the caller's own user row and the branch's `managerId`. **Two answers, on purpose:**
- Reads out of scope → **404** (a hidden resource; don't reveal it exists).
- Manage out of scope but same company → **403** (`requireManageable`): the caller reached
  a branch of their own company they simply may not manage — a permission answer.

Create/delete/activate/deactivate/assign-manager are `COMPANY_ADMIN` only. A non-admin's
list is pinned to their visible branch ids (the one they manage + their placement); an
empty set matches nothing, which is the right answer for a user assigned to none.

Company isolation is the usual two layers: the Hibernate filter + `findByIdWithinCompany`.

## REST APIs

| Method | Path | Who |
|---|---|---|
| `POST` | `/api/v1/branches` | `COMPANY_ADMIN` — creates branch + user + wallet |
| `PUT` | `/api/v1/branches/{id}` | `COMPANY_ADMIN` (any) / `BRANCH_MANAGER` (own) |
| `GET` | `/api/v1/branches/{id}` | admins; others their visible branch |
| `GET` | `/api/v1/branches` | admins (all); others scoped; paged/sorted/filtered |
| `DELETE` | `/api/v1/branches/{id}` | `COMPANY_ADMIN`, soft delete |
| `PATCH` | `…/activate` `…/deactivate` | `COMPANY_ADMIN`, idempotent |
| `PATCH` | `…/assign-manager` | `COMPANY_ADMIN` (null clears) |
| `PATCH` | `…/assign-users` | `COMPANY_ADMIN` (any) / `BRANCH_MANAGER` (own) |

`assign-users` returns `{assigned, skipped, rejected}` — placed, already-there, and ids
that are not users of the company. Sort whitelist: branchCode, branchName, branchType,
status, city, state, postalCode, createdDate, updatedDate. `size` capped 100. `search`
covers code, name, email, city, postal code (wildcard-escaped).

### Errors
400 validation / bad sort · 401 unauthenticated · 403 non-admin write / manage out of
scope · 404 unknown or out-of-scope id · 409 duplicate code/name, stale version · 422
bad hours/coordinates/working-day, non-company manager, no bound company.

## Events

`BranchEvent` (sealed, `AFTER_COMMIT`): `BranchCreated`, `BranchUpdated`,
`BranchActivated`, `BranchDeactivated`, `ManagerAssigned`. Today they log; the listener is
where serviceability-cache eviction attaches when the shipment module lands.

## Database changes — `V9__branches.sql`

New `branches` table: identity, classification, contact, address, coordinates
(`DECIMAL(9,6)` with range CHECKs), hours, six capability flags, `BaseEntity` columns.
`UNIQUE(company_id, branch_code)` and `UNIQUE(company_id, branch_name)`; indexes on
`(company_id, status)`, `(company_id, branch_type)`, `(company_id, manager_id)`, and
`(company_id, postal_code)` (the coming serviceability lookup). No cross-entity FKs (see
Entity relationships).

## Audit

`BRANCH_CREATED`, `BRANCH_UPDATED` (changed fields only), `BRANCH_ACTIVATED`,
`BRANCH_DEACTIVATED`, `BRANCH_MANAGER_ASSIGNED`, `BRANCH_USERS_ASSIGNED`, and `DELETED`.

## Tests

20 new unit tests (323 in the suite): `BranchTest` (normalisation, working-day
validation/dedup, hours, coordinate ranges, type required, lifecycle, soft delete) and
`BranchServiceImplTest` (create + duplicates + bad manager + no-company, update + stale
version, **branch-manager updates own only (403 for another)**, idempotent lifecycle,
soft delete, assign-manager validation, assign-users placed/skipped/rejected, 404 on a
foreign id).

## Verified by running it

Against MySQL 8.0.46 on 2026-07-23, using the Phase-3 company fixtures:
- `V9` applied; `validate` passed.
- Create normalised code/name/email, working days uppercased+deduped, defaults applied;
  duplicate code and name 409; super-admin create 403; bad latitude/working-day 400;
  closing≤opening 422.
- Assign-manager set a real user, rejected a fake one (422); assign-users placed 2,
  rejected 1 fake, and a re-assign skipped the already-placed user.
- **Branch-manager scoping**: asha (manager of PUNE_MAIN) saw only that branch (1 of 2),
  200 on it, 404 on the other, 403 updating the other, 403 creating; `COMPANY_ADMIN`
  update with the right version 200.
- **Cross-company**: a rival admin got 404 on GET/PUT/DELETE of a Legacy branch and no
  search leak.
- Bad sort 400; soft delete → 200/404, code reserved (409 on recreate); all six branch
  audit actions written.

## Verified by running it — the user path (2026-07-29)

Against MySQL 8.0.46, `LEGACY_CO`, backend on :8081:

- **No `branchUser` block** → branch `NASHIK`, user `nashik@legacy-co.local`, generated
  password returned once, wallet `WLT2607H9NYNYJR`. **With an email and name** → `SOLAPUR`,
  `solapur@legacy.test`. **With a chosen password** → `NAGPUR`, and
  `temporaryPassword` absent from the response, as intended.
- **The password policy applies to the typed password**: `Nagpur#Str0ng1` for
  `nagpur@legacy.test` was refused — *"Password must not contain your email address"* — and
  **no branch row was left behind** (`SELECT COUNT(*) … 'NAGPUR'` → 0). Same for a duplicate
  address (`DUPLICATE_RESOURCE`, 0 rows). The transaction is genuinely atomic.
- Every created account: ACTIVE, `email_verified=1`, `BRANCH_MANAGER`, `branch_id` set and
  named as the branch's manager. **Logging in as `nagpur@legacy.test` works**, and that
  session reads its own wallet with no `branchId`.
- Frontend: the *Branch User* card renders on the create form and `ng build` is clean; the
  Material select would not open under browser automation, so the click-through submit was
  **not** exercised end to end — the flow above was driven over HTTP.

## Next

- [ ] Run `V13` against MySQL and exercise the branch-manager staffing scope over HTTP —
      own-branch create/update/activate/deactivate/assign-role, foreign-branch 403, hub
      placement 403, `COMPANY_ADMIN`-role-assignment refused, custom-role assignment
      allowed. See *A branch manager staffs their own branch* above.
- [ ] Hub Management (Phase 4 continues) — likely the same shape, and it should almost
      certainly provision its hub user the same way.
- [x] ~~The branch user gets no `user_company_roles` row~~ — **fixed 2026-07-29** by
      `BranchRoleProvisioningService`, in the branch's own transaction. The account now
      holds both the JWT authority and the company role, so it will carry real permissions
      the day authorisation moves onto them. **Not yet exercised over HTTP** — the run below
      predates it.
- [ ] Nothing forces a password change on first login (`users` has no such column, and V12
      is spoken for). The generated password is therefore permanent until someone resets it.
- [ ] `users.branch_id` and `branches.manager_id` FKs once the dev orphan rows are
      reconciled.
- [x] ~~Serviceability (pincode → branch)~~ — **done 2026-09-02**, see *Pincode Branch
      Mapping* below. Rate cards still open.
- [ ] Enforce one HEAD_OFFICE per company if the product requires it.

## Pincode Branch Mapping (2026-09-02)

Which branch owns delivery/service for a pincode. Direct request: "new menu as pincode
branch mapping ... map branch to multiple pincode." A branch serves many pincodes; a
pincode is served by **exactly one** branch per company (real-world routing would be
ambiguous otherwise) — scoped via `AskUserQuestion` before writing anything, since this is
a real, hard-to-reverse-later business rule, not an obvious default.

New table `branch_pincode_mapping` (`V53`), `UNIQUE (company_id, pincode_id)` **alone**
(not the `(branch_id, pincode_id)` pair) is what enforces the one-branch rule. Deliberately
**company-owned for real** — unlike `master_pincode_areas` (V52, see `MEMORY/AI_CONTEXT.md`
0.32.2), this table binds the caller's own real `CompanyContext`, never
`GlobalMasters.PLATFORM_COMPANY_ID`, because a Branch genuinely belongs to one company.
`Pincode` itself is still the global master, so `BranchPincodeMappingService` (new, in
`com.courier.modules.company.application`) crosses into the platform binding only for the
duration of a pincode lookup/display (`CompanyContext.runAs`), the mirror image of how
`PincodeAreaService` reaches for `Branch` via `BranchLookupPort` from the master side.

Three endpoints nested onto the existing `BranchController`: `GET/POST/DELETE
/branches/{id}/pincodes` — same nested-action shape as `assign-manager`/`assign-users`.
`POST` is a **batch** add (the brief's own "map to multiple pincode"): pincodes already on
this branch come back in `alreadyMapped`, not re-applied; a pincode already owned by a
*different* branch comes back in `conflicts` (naming that branch) — **never silently
moved**, removing it from the other branch first is a separate, deliberate call. Role-based
gating, same posture as every module since Ticket Support (no new `PermissionModule`
catalogue rows): `COMPANY_ADMIN`-only writes; reads reuse `BranchService.getById`'s own
branch-visibility rule, so a `BRANCH_MANAGER` sees only their own branch's mapping (a
foreign branch id 404s, same as `GET /branches/{id}` itself).

Frontend: new nav leaf "Pincode Branch Mapping" under Masters (`COMPANY_ADMIN`-only,
`/masters/pincode-branch-mapping`, declared ahead of the generic `masters/:master` route).
New standalone page `features/branch/branch-pincode-mapping.ts` — a branch dropdown
(`MasterDataService.branchDirectory()`), debounced pincode search rendered as a tick-list
so several can be queued and added in one request, and a mapped-pincodes table with a
per-row Remove. Not folded into `branch-view.ts`'s own detail page — the user explicitly
asked for a *new menu*, confirmed via `AskUserQuestion`.

**Verified live** on throwaway `:8082` (`:8100`/`:4200` untouched) against real
`courier_db`, `V53` applied: add/already-mapped/conflict/remove all confirmed with real
branches and pincodes; `BRANCH_MANAGER` correctly 404'd (visibility, foreign branch) and
403'd (write). See `CHANGELOG.md` Unreleased 2026-09-02 for the full write-up, including a
real concurrent-session race caught live (two sessions independently mapping the same
first-listed pincode to the same branch within the same minute — the `alreadyMapped`
path handling it correctly, not a bug). 6 new `BranchPincodeMappingServiceTest` cases,
`mvn test` 876 → 882.
