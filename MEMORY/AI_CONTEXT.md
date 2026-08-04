# AI_CONTEXT

> **Read this file first, before every task.**
> It is the entry point to the project memory. Keep it updated after every change.

---

## Current Version

`0.17.1` — Same session, immediately after 0.17.0: on direct user request, `OUT_SCAN`
folded back into `MANIFEST_CREATED` — "manifest created as outscan created", one
milestone not two. `V20` migration (folds the real `OUT_SCAN` rows 0.17.0's own live
verification produced), `POST /shipment-movement/out-scan` removed, Dispatch now reads
`MANIFEST_CREATED` shipments directly, timeline 7 → 6 steps, `MANIFEST_CREATED`'s
display label is now **"Out Scan Created"** everywhere (badge + timeline).
`ManifestScanCard` → `ManifestCard`, stripped to a read-only heading + LR table (no
scan controls left — the Out Scan page is a worklist now, not an action screen).
Verified live: `V20` applied clean, zero `OUT_SCAN` rows remain, old endpoint 404s, a
manifest dispatched straight from `MANIFEST_CREATED` with no scan step, confirmed
through the Angular console too. Full detail in `CHANGELOG.md` 0.17.1 and
`MEMORY/modules/shipment-movement.md`.

Previously current:

`0.17.0` — **Shipment Movement**, new package `com.courier.modules.manifest`,
migration `V19`, extends `com.courier.modules.shipment`. The task assumed a Manifest
module already existed (its own business flow starts "Create Manifest"); nothing had
built one — confirmed with the user before writing any code, then built the minimal
version underneath this module rather than stopping or faking a bare `manifest_id`
column. `ShipmentStatus` renamed (`MANIFESTED`→`MANIFEST_CREATED`,
`RECEIVED`→`IN_SCAN`, new `OUT_SCAN`); `shipment_status_history` gained `branch_id`/
`manifest_id`/`vehicle_id`; new `manifests`/`vehicles`/`delivery_assignment` tables.
5 endpoints under `/api/v1/shipment-movement` (out-scan/dispatch/in-scan/
out-for-delivery/deliver) + `GET /shipments/{id}/timeline`. Zero permission
migration — `DefaultPermissionCatalog` had already seeded the exact codes needed
(`MANIFEST_DISPATCH`/`MANIFEST_RECEIVE`/`DELIVERY_DISPATCH`/`DELIVERY_DELIVER`/
`TRACKING_CREATE`), reused instead of adding six new ones. A real architecture
decision, not just a convention: both "create" and "dispatch" orchestration live in
`ManifestServiceImpl` (calling into `ShipmentService`), never the reverse — the
natural split would have created a Spring circular-bean dependency between the two
modules' services. Frontend: 6 pages exactly matching the brief; nav's five
aspirational Operations leaves un-tagged and re-split by branch desk, correcting (not
just extending) a prior guessed assumption in `navigation.config.spec.ts`. `mvn test`
650 → 673 (660 pass, 13 pre-existing unrelated master-module failures — confirmed
isolated, not caused by this work); `ng test` 118 → 125 (120 pass, 5 similarly
pre-existing unrelated failures). Verified live over HTTP (full Pune → Latur pipeline,
every refusal exercised) and through the Angular console (a real toast + a shipment
actually closed). Full detail in `MEMORY/modules/shipment-movement.md` and
`CHANGELOG.md` 0.17.0. **Stop here per instruction — do not start Finance or Reports.**

Previously current:

`0.16.9` — Sidebar now marks every nav leaf/section whose route doesn't exist in
`app.routes.ts` with `(Soon)` in its title: the whole Pricing section, Operations'
manifest/receive/sorting/dispatch/delivery, Finance's hub-wallet/settlement/payment/
invoice, and the whole Reports section. Verified live as `pune@gmail.com`. Found in
passing, not fixed: `wallet-transactions` nav leaf points at `/finance/transactions`,
should be `/finance/branch-wallet/transactions` — a real feature with a broken link, left
untagged rather than mislabeled. Full detail in `CHANGELOG.md`.

Previously current:

`0.16.8` — Branch dashboard's wallet balance tile never showed a figure:
`DashboardServiceImpl` never computed `walletBalance` at all (stale javadoc said "no
module behind it yet", though Branch Wallet shipped 2026-07-28). Fixed by wiring
`WalletService.getForBranch(null).getAvailableBalance()` into the summary response,
null for company/platform admins (no own branch). Verified live over HTTP as
`pune@gmail.com` (`BRANCH_MANAGER`): `walletBalance: 924.0`, matching the wallet exactly.
**Along the way, a real schema trap surfaced and was corrected before it compounded:**
`companies` has two UUID columns, `id` (PK) and a separate `company_id` (the actual FK
target everywhere in the schema) — see `CHANGELOG.md` 2026-08-03 for the full story and
what was touched to recover from a wrong first join.

**Same day, immediately after — a second, real bug found live-verifying the first:**
`error.interceptor.ts`'s global 401 handler redirected to `/session-expired` for *any* 401,
including a failed `/auth/login` call itself — so a wrong password looked exactly like an
expired session instead of showing the login page's own inline error. Fixed with the same
`!isAuthCall` guard the interceptor's other 401 branch already had. Full detail in
`CHANGELOG.md`.

Previously current:

`0.16.7` — Company create gained Country/State/District/City dropdowns (against
`/global-masters/**`, resolved to plain names — `Company.country/state/city` are free-text,
not FK) and a logo/favicon field (reusing `CompanyLogo`, URL-only per the user's call to
defer S3). Closed a real, long-standing gap: the header has shown a hard-coded "CS" +
`environment.appName` since UI-03 (2026-07-27) because nothing in the session carried a
company name/logo. Fixed by extending the auth module's own `CompanyDirectoryPort` (its
`CompanyRef` now carries `name`/`logo`) and adding two JWT claims, `cnm`/`clogo`, the same
way `bid`/`hid` were added — so a hard reload doesn't lose it, not just an in-app
navigation. `LoginResponse` and `/auth/me` both carry the same fields. `mvn test` 650/650,
`ng test` 118/118. Also seeded, live via the SUPER_ADMIN API (not code): India, all 36
states/UTs, all 36 Maharashtra districts, 43 cities, 17 areas. **Verified live** — :8081
restarted on the new code, cascade dropdown and header branding both confirmed in the
browser against a real created company. Full detail in `CHANGELOG.md` 0.16.7.

Previously current:

`0.16.6` — Subscription Plan Management, frontend only, new `features/subscription-plans`
(list/create/edit/view, mirrors Role Management's shape). The backend
`SubscriptionPlanController` (SUPER_ADMIN only) has existed since the SUPER_ADMIN module
(2026-07-29); `navigation.config.ts` already carried an aspirational nav leaf for it —
nav ahead of a route, the same "responsibility list is ahead of the code" pattern seen
elsewhere. Surfaced by hitting an empty plan dropdown while creating a company. `PlanForm`
mirrors two backend invariants client-side (TRIAL locks price to 0, ENTERPRISE locks
quotas blank) rather than letting a 422 teach the lesson, the same convention as Rate
Master's weight-slab-overlap preview. Verified live end to end in the browser (no unit
tests added — an honesty gap, unlike every other module's frontend work). Full detail in
`CHANGELOG.md` 0.16.6.

**Same day, immediately before:** the local dev database (`courier_db`) was fully
`TRUNCATE`d on request — all 37 tables including `flyway_schema_history` — wiping every
fixture [[keep-test-data-in-dev-db]] had protected. Fixed the resulting Flyway gap with a
manual baseline row (`type='BASELINE'`, version 18; no CLI or maven plugin installed to
do this for you), confirmed by an actual restart. Reseeded one login,
`super.admin@gmail.com` / `Pass@1234` (SUPER_ADMIN, no company code needed to sign in),
and relabelled/repointed the login page's "System Admin" dev quick-fill button to it. One
`STANDARD_MONTHLY` plan was created via the API to unblock company creation, which is what
led into 0.16.6. **Every dev-login credential documented below this point, and every
company/user/role fixture, no longer exists** — rebuild before relying on any of it.
Full detail in `CHANGELOG.md` 0.16.5b.

Previously current:

`0.16.5` — Sidebar bug: `routerLinkActive` (default non-exact/"subset" matching) and
`NavigationService.matches()` (same flaw, hand-rolled) both highlighted a nav leaf
whenever the URL merely started with its route — so `/rates/calculator` also lit up
"Rate Cards" (route `/rates`). Fixed with exact matching in both places (2026-07-31).
Same shared-prefix collision was latent for Shipment Booking vs Shipment Search too.
Before that, 0.16.4: Country/State/District/City/Area/Pincode (the six geography masters) now read
for SUPER_ADMIN **and** COMPANY_ADMIN, moved in the nav from the `platform` node into
`masters` (2026-07-31). Fixed properly: new `readAccessFor()` in `master.config.ts`
(mirrors `writeAccessFor`) is enforced inside `MasterList`/`MasterView` themselves, since
the shared `masters/:master` route can't tell a geography list from a company catalogue
by itself — the route guard only widens to admit both tiers, the component narrows
per-list. This also fixed a regression 0.16.2 introduced (its blanket masters restriction
had accidentally cut SUPER_ADMIN off from geography too). **Flagged but not fixed:**
`masters/:master/new`/`:id/edit` routes are still hardcoded COMPANY_ADMIN-only, so a
SUPER_ADMIN still can't reach the create/edit form for a country/state row despite
`GLOBAL_MASTER_WRITERS` saying they should be able to — pre-existing, out of scope for a
read-access request, needs the same fix shape as a follow-up. Before that, 0.16.3
continues 0.16.2's frontend nav/route scoping pass: Customers and Finance now
exclude SUPER_ADMIN only (counter desk / branch wallet responsibilities kept intact —
confirmed NOT COMPANY_ADMIN-only, unlike Masters/Branches/Settings), and the aspirational
Pricing nav placeholder (distinct from the real Rate Master module and the real,
frontend-less Pricing Engine backend) is COMPANY_ADMIN only (2026-07-31). 0.16.2, same
day: Rate Master is company+branch only, Company Settings/Branches/Masters are
COMPANY_ADMIN only (stricter than just excluding SUPER_ADMIN — branch-tier roles lost
read access too, by explicit instruction), and Operations excludes SUPER_ADMIN while
keeping COMPANY_ADMIN and branch roles unchanged. **Both passes frontend-only** — backend
`@PreAuthorize`/service gates (including Branches'/Masters' existing `isSuperAdmin()`
cross-company read branches) were deliberately left alone. Before that:
`NoResourceFoundException` (unmatched
routes, e.g. still-unbuilt Hub Management's `/hubs`) fixed to return 404 instead of
falling through to the catch-all 500 handler (0.16.1, also 2026-07-31). Before that:
Shipment Booking, then Pricing Engine, then Rate Master, then Route Management (extends
Master Data's existing Route list), then Customer Management, then (earlier the same
day) SUPER_ADMIN / Platform Console, Branch RBAC, and the
`tenant_id` → `company_id` rename (all 2026-07-30)

**Build status:** `mvn compile` clean · `mvn test` **650 pass of 650**.
Frontend: `ng build` clean · `ng test` **118 pass of 118** (vitest via `@angular/build:unit-test`)

**2026-07-30 (latest) — Shipment Booking, `V17`, new package
`com.courier.modules.shipment`.** The core transaction of the platform, and the actual
intended consumer both `RateService.calculate` and `PricingEngine.calculate` were built
for. Books only after Customer, Serviceability+Route+Pricing (one Pricing Engine call)
and — for a PAID booking — the Branch Wallet have all agreed; orchestrates every
cross-module rule rather than re-deciding any of them. Five tables (`shipments`,
`shipment_items`, `shipment_charges`, `shipment_status_history`, `shipment_documents`),
the full ten-state `ShipmentStatus` graph declared now for Manifest Management to
extend (this module itself only ever writes `BOOKED`/`CANCELLED`), AWB + shipment
number generation (existence-check retry, unique constraint as the backstop — no
`MAX()+1`), optimistic-lock update that re-prices and replaces the charge row, cancel
refused once `DISPATCHED`+. Closes the "Booking debit seam"
`MEMORY/modules/branch-wallet.md` left open: `WalletService.debitForBooking
(BookingDebitCommand)`, `isAuthenticated()` not `COMPANY_ADMIN`-only, debited
**after commit** via a `WalletProvisioningListener`-shaped `AFTER_COMMIT`+
`REQUIRES_NEW` listener — a real, accepted gap if the debit itself fails (the shipment
stays booked, undebited, for manual reconciliation). One new permission,
`SHIPMENT_UPLOAD` (catalogue 222 → 223); RBAC still role-based like every other
module. 8 endpoints incl. `GET /shipments/track/{trackingNumber}` (not a second bare
`/shipments/{x}` route). Frontend: 7 pages incl. a four-step booking wizard (Booking &
Parties → Items & Package → Pricing → Confirm) that calls the Pricing Engine directly
for a live Step 3 preview, reusing Customer/Master/Rate frontend services rather than
duplicating any lookup; 111 frontend tests (98 → 111). Verified live over HTTP (PAID/
TO_PAY bookings, insufficient-balance refusal, wallet debit after commit, cancel +
double-cancel refusal, update + re-price + stale-version 409, document attach, three
business-rule refusals) and through the Angular console end to end, where a real bug —
`canAdvance`/`bookingLabels` were `computed()` signals reading a plain
`FormControl.value` alongside real signals, so the cached result never noticed a
dropdown change and "Continue" stayed disabled forever — was found live and fixed by
converting both to plain methods (OnPush change detection already re-invokes
template-bound methods on every event the component handles). Full detail in
`MEMORY/modules/shipment-booking.md` and `MEMORY/CHANGELOG.md`. **Deliberately not
touched: Manifest Management (do not start next per instruction), Hub Management, the
authorise-on-permissions capstone.**

Previously current:

**2026-07-30 (earlier the same day) — Pricing Engine, no migration, new package
`com.courier.modules.pricing`.** A reusable, stateless Strategy+Factory service that prices
a shipment — built to be called by Shipment Booking, Quotation, the mobile app and any
future integration, and deliberately independent of `modules.shipment` (still unbuilt).
Superset of Rate Master's own `POST /rates/calculate`: adds volumetric weight
(`chargeableWeight = MAX(actual, volumetric)`, from optional Length/Width/Height),
pickup/delivery pincode serviceability, and eight pluggable `ChargeCalculator` Strategies
(Freight/Fuel/Handling/ODA/Insurance/GST/Discount/RoundOff) whose Fuel/ODA/Insurance/
Discount lines and rounding rule are deployment-configurable
(`PricingProperties`/`pricing.*`) — none of which Rate Master's fixed formula has. Two
small seams added to already-shipped modules, the same pattern `RouteService
.findByBranches` set for Rate Master: `RateService.findActiveCandidates` (Rate Master's
own `calculate` refactored to use it, no behaviour change) and
`PincodeService.findByCode` (pincodes are a global master; nothing before this could look
one up by its raw postal code). One endpoint, `POST /api/v1/pricing/calculate`,
`isAuthenticated()`, no new permission codes. 55 new backend unit tests, `mvn test` moves
573 → 627; no frontend, since the module's own Definition of Done does not ask for one.
Verified live over HTTP on a temporary instance against the shared dev database — an
exact-slab quote reproducing Rate Master's own 135.70 verbatim, a volumetric-dominant quote
reproducing the project's own 280.00 overage unit test exactly, serviceability/weight/
service-type refusals, a hand-checked discount, and the endpoint's Swagger registration.
Full detail in `MEMORY/modules/pricing-engine.md` and `MEMORY/CHANGELOG.md` 0.15.0.
**Deliberately not touched:** Shipment Booking (the actual intended consumer), Hub
Management, and the authorise-on-permissions capstone. **Stop here per instruction — do
not start Shipment Booking next.**

**2026-07-30 (earlier the same day) — Rate Master, `V16`, new package `com.courier.modules.rate`.**
Company rate cards: one row prices one weight slab for one Route + Service Type +
Package Type + Payment Mode combination, and `POST /rates/calculate` prices a shipment
without booking it — the seam Shipment Booking will eventually call. 7 endpoints, no
`DELETE` (`RATE_MASTER_DELETE` stays seeded-but-unused, the `CUSTOMER_DELETE` pattern).
Two business rules with teeth, both borrowed from already-shipped modules: only an
active Route may carry an active Rate (checked on create, on update while the rate stays
active, and on activate — closing the same "deactivate, re-add, reactivate" loophole
`WeightSlabServiceImpl` already guards), and no two ACTIVE rates for one combination may
cover the same weight (`Rate.overlapsWeightRange` mirrors `WeightSlab.overlaps`
verbatim). Added one small seam to the already-shipped Route module —
`RouteService.findByBranches` — because the calculator is handed a branch pair, not a
route id, and a new `PermissionAction.CALCULATE` (read-only, granted far more broadly
than `CREATE`/`UPDATE`/`DELETE`, since pricing a shipment is a counter-desk right, not a
back-office one). Catalogue moves 219 → 222. Frontend: full CRUD + a Rate Calculator
(one component behind both a quick-lookup dialog and its own page) + a Weight Slab Grid
that mirrors the overlap rule client-side, shown live in the create/edit form as soon as
all four combination pickers are filled — an admin sees a conflict before saving, not
only after a 422. Verified live over HTTP and through the Angular console, where two
distinct classes of bug surfaced that `mvn test` had not caught: a migration column-name
mismatch (`company_roles.role_code`, not `.code`) and a Java operator-precedence bug in
two error messages (`"a %s b" + "c".formatted(x)` binds `.formatted` to `"c"` only,
so the `%s` in the first literal never got substituted) — both fixed, and the regression
tests now assert the interpolated value actually appears rather than just a substring
that happened to survive either bug. Full detail in `MEMORY/modules/rate-master.md` and
`MEMORY/CHANGELOG.md` 0.14.0. **Deliberately not touched:** Hub Management, Shipment
Booking (the eventual consumer of `/rates/calculate`), and the authorise-on-permissions
capstone (RBAC still checks JWT role tier, not the permission codes granted above).

**2026-07-30 (earlier the same day) — Route Management, `V15`, extends `com.courier.modules.master`'s
existing `Route`.** A brief asked for a standalone Route Management module — its own
`route_master` table, `/routes` endpoints, `ROUTE_VIEW/CREATE/UPDATE/DELETE`
permissions. `master_routes` already covers this domain (booking/delivery branch pair,
distance, transit promise, direction-matters uniqueness), verified live 2026-07-28 as
one of Master Data's twelve lists. Building a second Route concept alongside it would
have left two route tables in one schema, so — asked directly — the user chose to
**extend** the existing `Route` rather than duplicate it: no new table, no new package,
no new permission codes. Added `transit_hours` (`[0, 23]`, the remainder on top of
`transit_days`) and `distance_unit` (new `DistanceUnit` enum, one constant `KM` today,
same reasoning as `WeightUnit`), wired through the entity, DTOs, mapper, service and the
Angular masters screens. Verified live over HTTP (create/update/list/activate/
deactivate all carry both fields; same-branch, duplicate-pair and reverse-direction
rules unchanged) and through the Angular console (`New Route` form, list, detail view).
A frontend format regression (`master-table.spec.ts` expected `"1 d"`, the new
`transitLabel` helper initially produced `"1d"`) was caught by the existing suite before
verification, not after. Full detail in `MEMORY/modules/master-data.md`
§"Route Management (2026-07-30 extension)" and `MEMORY/CHANGELOG.md` 0.13.1.
**Deliberately not touched:** every other master list, permissions, roles.

**2026-07-30 (earlier the same day) — Customer Management, Phase 7, `V14`, package
`com.courier.modules.customer`.** New module, pulled forward ahead of Hub/Rate Master by
explicit request. `Customer` + `CustomerAddress` (the latter with a real FK to
`customers.id` — both tables are this module's own, unlike every cross-module reference
elsewhere in the project). Business rules: mobile unique per company but not reserved
past a soft delete (unlike the code, which is); GST mandatory only for `BUSINESS`; at
most one default-pickup and one default-delivery address per customer, enforced by
clearing the flag on the others rather than rejecting a second `true`; duplicate address
refused by comparing lines + pincode, not the full geography stack; each geography id an
address carries is validated against the **global** masters through
`com.courier.modules.master`'s own service interfaces (a forward cross-feature
dependency, not a port — no cycle to avoid). No permission migration needed:
`CUSTOMER_*`/`ADDRESS_*` were already seeded in `V6`, another instance of the
"responsibility list is ahead of the code" pattern. RBAC follows every other module's
still-role-based convention (the authorise-on-permissions capstone below remains
unwired): `COMPANY_ADMIN`/`BRANCH_MANAGER`/`OPERATOR` create and update, only the first
two can activate/deactivate or delete an address, anyone authenticated reads. Frontend:
list/create/edit/view plus a `MatDialog` address book with a six-level cascading
geography picker; verified live over HTTP (409/422/404/403 paths all confirmed against a
real `SUPER_ADMIN` and `COMPANY_ADMIN` token) and through the Angular console, where the
browser check caught a real bug no test could — the address dialog had no internal
scroll region and its submit button was unreachable past viewport height, fixed with an
explicit `max-height`/`overflow-y` on the dialog root. Full detail in
`MEMORY/modules/customer.md` and `MEMORY/CHANGELOG.md`. **Deliberately not touched:**
Hub, Rate Master, Shipment, and the authorise-on-permissions capstone.

**2026-07-30 (earlier the same day) — physical `tenant_id` → `company_id` rename, plus
`V12`/`V13` finally run against MySQL.** The deferred column rename ("READ THIS FIRST"
below) is done: all 13 migrations, every entity's `@Table` literals, and 5 native-SQL
call sites rewritten together; verified by dropping and rebuilding the local dev database
from the edited migrations. `mvn test` stayed at 523/523. Surfaced and fixed a real,
pre-existing bug in `V12` (its `TENANT_ADMIN` rewrite referenced a column `user_roles`
never had) — the kind of defect the "not yet run against MySQL" status below was
concretely hiding. Full detail, including an operational incident (a mistargeted `pkill`
briefly took down the port-8081 dev instance, restarted with the user's confirmation), is
in `MEMORY/CHANGELOG.md`. Deliberately not touched in this pass: any Customer/Shipment
code (Shipment Booking itself shipped 2026-07-30, later the same day — see
`MEMORY/modules/shipment-booking.md`) and the branch-wallet PAID/TO_PAY debit rule
(nothing existed yet to call it).

**2026-07-30 — Branch RBAC, navigation and menus, and two tripwires that had gone off
silently.** `mvn test` was **red** at the start of this pass: `DefaultRoleCatalog` (the
ninth role, `ACCOUNTS`) and `DefaultPermissionCatalog` (8 codes for manifest/delivery/
menu/wallet rights) had moved ahead of their own tests and, for the permission catalogue,
its migration — the "responsibility list is ahead of the code" pattern this file has
flagged before, this time inside the catalogue itself rather than between the catalogue
and a service. Fixed both, added `V13__branch_operations_permissions.sql` (**V13 is now
used** — Hub Management, next in line, takes **V14**), implemented
`DefaultRoleCatalog.isBranchAssignable` (referenced in a javadoc since `BRANCH_ROLE_CODES`
was written, never implemented), and made branch responsibilities #1/#2 ("create branch
users", "assign menus") actually work: `UserServiceImpl` admits `BRANCH_MANAGER` for
create/update/activate/deactivate/assign-role/remove-role, scoped to the caller's own
branch and to branch-assignable roles. Frontend: `AppRole.ACCOUNTS` was missing entirely;
`navigation.config.ts` now gives `BOOKING_OPERATOR`/`DELIVERY_OPERATOR`/`ACCOUNTS` the nav
leaves their actual `DefaultRoleCatalog` permissions grant. Full detail in
`MEMORY/CHANGELOG.md` and `MEMORY/modules/branch.md` §"A branch manager staffs their own
branch". **Deliberately not touched:** Shipment (booking flow / payment modes are
documented as context, not built) and the JWT authority gap for
`BOOKING_OPERATOR`/`DELIVERY_OPERATOR`/`ACCOUNTS` (no `auth.Role` entry of their own —
that is the existing "authorise on permissions" capstone below, not a Branch-scoped fix).
**Code complete; `V13` itself is now applied** (see the rename entry above) — the
branch-manager staffing scope it enables is still unexercised over HTTP.

---

## READ THIS FIRST — the tenant concept is gone (2026-07-29), and the column followed (2026-07-30)

**A company is the only owner. There is no separate tenant anywhere in the code, and as
of 2026-07-30 the physical database column is `company_id` too — the word "tenant" does
not appear in the schema, only in the historical `TENANT_ADMIN` role-data literal `V12`
rewrites away.**

| Was | Now |
|---|---|
| `TenantContext` | `CompanyContext` |
| `TenantAwareEntity` (`tenantId`) | `CompanyOwnedEntity` (`companyId`) |
| `TenantResolutionFilter` / `TenantEntityListener` / `TenantFilterAspect` | `Company…` |
| Hibernate filter `tenantFilter` | `companyFilter` |
| `TenantViolationException`, `TENANT_VIOLATION`, `TENANT_INACTIVE` | `CompanyIsolationException`, `COMPANY_ISOLATION_VIOLATION`, `COMPANY_INACTIVE` |
| package `com.courier.shared.tenant` | `com.courier.shared.company` |
| header `X-Tenant-ID` | `X-Company-ID` |
| JWT claim `tid` | `cid` (`tid` still **read**, never written — see below) |
| `TenantDirectoryPort` / `CompanyTenantDirectory` / `StandaloneTenantDirectory` | `CompanyDirectoryPort` / `CompanyDirectory` / *deleted* |
| role `TENANT_ADMIN` | *deleted* — it was `COMPANY_ADMIN` under an older name |
| DB column `tenant_id` (every company-owned table) | `company_id` — **done 2026-07-30**, see CHANGELOG |

**One thing did NOT change, and must not be "tidied up" by accident: `Company` still has
two UUIDs** — `id` (the `companies` row key, what every `/api/v1/companies/{id}` URL
carries) and `companyId` (the ownership key stamped on every row it owns, carried in the
JWT). Collapsing them needs its own data migration, is a separate concern from the column
rename, and is still deferred (decision 19). Do not assume
`company.getId().equals(company.getCompanyId())`.

**The `tenant_id` → `company_id` physical rename is done (2026-07-30).** All 13
migrations, every `CompanyOwnedEntity`/`MasterDataEntity` subclass's `@Table` literals,
and 5 native-SQL call sites were rewritten together and verified by dropping and
rebuilding the local dev database from the edited migrations — `mvn test` 523/523,
`ddl-auto: validate` passed, `information_schema.columns` confirms zero `tenant_id` /
28 `company_id` columns. Full detail, including a pre-existing `V12` bug this uncovered
and an operational incident during verification, is in `MEMORY/CHANGELOG.md`'s
2026-07-30 "Physical `tenant_id` → `company_id` rename" entry. **A new company-owned
table now uses a `company_id` column directly** — the old "keep creating `tenant_id`" rule
is retired along with the column it was protecting.

The JWT fallback: `cid` is written, `tid` is still read by
`JwtTokenProvider.companyClaim(...)`, because a refresh token minted before the deploy is
valid for seven days and dropping the fallback signs those users out mid-session. Delete
the fallback once the longest refresh TTL has passed since deployment.

**SUPER_ADMIN / Platform Console, 2026-07-29 — the current module.** See *Current Module*
below and `MEMORY/CHANGELOG.md` for the full entry.

**The Company Admin boundary, 2026-07-29 (later the same day).** `modules/company` was
**modified, not rebuilt**. What changed:

- **A branch creation now makes four things**, not three: branch, login account, that
  account's **company role**, and (after commit) the wallet. The account used to get auth's
  `Role.BRANCH_MANAGER` JWT authority and **no `user_company_roles` row**, so it held a role
  that appeared nowhere in the Roles screen. `BranchRoleProvisioningService` grants the
  company's `BRANCH_MANAGER` role, creating it from `DefaultRoleCatalog` only when the
  company has none — an *ensure*, not a role per branch. Both halves idempotent, inside the
  branch's transaction. See `MEMORY/modules/branch.md` §*Creating a branch creates four
  things*. **Code complete, not yet run against MySQL.**
- **`CompanyAdminBoundaryTest`** — the companion to `SuperAdminBoundaryTest`, asserting the
  refusals (no create/delete company, no subscription management, no cross-company reach),
  the isolation invariants (every company-owned entity repeats `@Filter`; the owner column
  is still `tenant_id`, mapped once), and the fifteen-module responsibility list.
- **Company isolation verified by reading it** — every business table, all 27 company-owned
  entities, the URL rules, the frontend guards. Results and the four deliberate exceptions
  are in `MEMORY/modules/company.md` §*The Company Admin boundary*. **The honest gap:** the
  responsibility list is ahead of the code — Rate Master, Vehicles, Drivers, Customers,
  Customer Addresses, Shipment Orders, Manifests and Reports have seeded permission codes
  and no service behind them.

**Branch onboarding, 2026-07-29:** creating a branch creates **branch + login account +
company role + wallet** from one `POST /branches`. The account and its role are provisioned
in the *same transaction* as the branch (a branch nobody can sign in to is not a branch);
the wallet stays on the existing AFTER_COMMIT listener and is therefore **not in the create
response**. Optional `branchUser` block on the request; absent, the address is derived
`<branch-code>@<company-code>.local`. Unlike a company's first admin, this account is
**ACTIVE with a usable password** — typed by the administrator, or generated and returned
**once** in the create response. The branch+user+wallet path was verified by running it; the
role grant was added afterwards and has not been.

**Master Data verified by running it (2026-07-28):** Flyway applied **V11**, `validate`
passed, all twelve tables created, the permission catalogue moved 174 → 187, and every rule
was exercised over HTTP — hierarchy, delete-with-children refusal, weight-slab overlap,
payment-mode contradictions, route direction and pair uniqueness, sort whitelist, LIKE
escaping, and the RBAC matrix. Then through the Angular console end to end, where two UI
defects were found and fixed. Full results in `MEMORY/modules/master-data.md`. **One gap,
inherited:** the cross-tenant HTTP check still cannot run — `RIVAL_CO` has no active user.

**Branch Wallet verified by running it (2026-07-28):** Flyway applied **V10**, `validate`
passed, every enum column landed as `varchar` (not a native MySQL `enum`), wallets were
provisioned both lazily for a pre-V10 branch and from the `BranchCreated` event, the ledger
chained exactly across four entries, and every refusal returned its intended status. Full
results in `MEMORY/modules/branch-wallet.md`. **One gap:** the cross-tenant HTTP check could
not run — `RIVAL_CO` has no active user and no branch.

Verified by running the app (through V9): Flyway applied V1–**V9**, Hibernate `ddl-auto: validate`
passed (including **two entities on the shared `users` table**), and every Phase-2/3
module was exercised end to end — RBAC, duplicate/reserved-identifier 409s, business
rules, stale-version 409s, sort whitelists, LIKE-wildcard escaping, soft delete, audit
actions; company provisioning; permission grants with plan gating; and User Management
including **cross-tenant isolation** (foreign ids 404 on every verb),
**branch-manager scoped reads**, and Company Settings (get-or-create, section-merge
writes, one row per company, cross-tenant clean).

Two defects that only a real run could surface were fixed in the process: Hibernate
6.5+ rendering `plan_type` as a native MySQL `enum(...)` instead of `VARCHAR(20)`, and
`SELECT COUNT(*) > 0` returning `BIGINT` where the repository declared `boolean`,
which made every create a 500. See `CHANGELOG.md` 0.3.0 *Fixed*.

**Local DB note:** this machine runs Homebrew `mysql@8.0` as `root`, not the
`courier/courier` user in `docker-compose.yml`; there is no Docker and no `.env`. Boot
with `DB_USERNAME=root DB_PASSWORD=... mvn spring-boot:run -Dspring-boot.run.profiles=local`.
Port 8080 is usually occupied by another app — use `SERVER_PORT=8081`.

**Frontend (new):** an Angular 20 admin console lives in `frontend/` (sibling of
`backend/`). Standalone + signals, Angular Material 3, Tailwind, dark collapsible sidebar,
RBAC-driven nav + `roleGuard`/`permissionGuard` routing, JWT with silent refresh,
lazy-loaded features, and **API-only data** (no mocks) through an `ApiService` that
unwraps the `ApiResponse` envelope. Run with `npm start` (proxies `/api` -> `:8081`).
See `frontend/README.md`.

**Frontend auth module** (`features/auth`, 2026-07-27): Login (+ dev quick-fill), Forgot
Password, Reset Password, Unauthorized, Session Expired. Services `AuthService` /
`TokenService` / `SessionService` / `PermissionService` (`hasRole`/`hasPermission`/
`canAccess`); guards `authGuard`/`guestGuard`/`roleGuard`/`permissionGuard`; JWT + error
interceptors (silent refresh, `/session-expired` on failure). `permissions` are consumed
from the API if present but the JWT still carries only role authorities — the "authorise
on permissions" backend step is still deferred, so today role checks carry UI access.

**Frontend admin layout** (`layouts/admin-layout`, UI-03, 2026-07-27): the authenticated
shell. One header toggle → desktop expand / desktop collapse (icon rail) / mobile drawer
(`matchMedia`, backdrop). Reusable header components: `GlobalSearch` (command palette over the
permission-filtered nav — real, no backend), `NotificationMenu` (unread badge + empty state,
`NotificationFeedService` starts empty until a `/notifications` endpoint exists), `UserMenu`.
Footer shows `environment.version` + `envLabel`. Company logo/name in the header currently use
`appName` — no per-tenant company name is in the session/JWT yet (only `tenantId`), so nothing
is faked; a real company-name field swaps in when the backend provides it.

**Frontend dashboard** (`features/dashboard`, UI-05, 2026-07-27): role-based enterprise
dashboard, API-only, no mock. `dashboard.roles.ts` `resolveProfile(roles, {branchId,hubId})`
maps every `AppRole` (+ assigned scope) to one of six layout profiles (PLATFORM / COMPANY /
BRANCH_MANAGER / BRANCH_OPERATOR / HUB_MANAGER / HUB_OPERATOR); `DASHBOARD_LAYOUTS` picks the
KPI tiles, charts, cards and quick actions per profile — **no role literals in the page**.
`DashboardService.load(profile)` forkJoins the not-yet-built `/dashboard/summary` with the real
`/branches`, `/hubs`, `/companies` lists: the summary 404s and every rich figure degrades to
zero/empty, while branch/hub/company **counts + summary lists are live today** from the shipped
list endpoints. Components in `components/`: `ChartCard` (**ng-apexcharts** wrapper, theme-aware,
skeleton + empty state), `ActivityTimeline`, `RecentShipments`, `QuickActions` (shipment actions
have no route yet — toast "available once the shipments module ships"), `BranchSummary`,
`HubSummary`; reuses shared `StatisticCard`/`UiCard`/`UiLoader`/`StatusBadge`. Added deps
`ng-apexcharts` + `apexcharts` (lazy-loaded on the dashboard route; `apexcharts` in
`allowedCommonJsDependencies`). Welcome header uses `environment.appName` as the company name —
the JWT still carries no per-tenant company name (same gap as the header, UI-03).

**Frontend dynamic navigation** (`core/navigation`, UI-04, 2026-07-27): the sidebar's data
layer. `navigation.model.ts` (`NavNode`), `navigation.config.ts` (full menu, each leaf tagged
with a `MODULE_ACTION` permission code + interim `roles` bridge), `navigation.service.ts`
(filters via `PermissionService.canAccess` — **no role literals in components**, prunes empty
parents, `order`-sorts, auto-expands the active group, persists collapse + expand to
`localStorage`, and `setMenuFromApi(flat)` builds a tree from a flat `parentId` list for a
server-driven menu). `Sidebar` consumes the service (a11y: `aria-expanded`/`aria-current`,
keyboard-native). Old `core/config/nav.config.ts` + `core/models/nav.model.ts` were **deleted**.
Reuses the existing `core/auth` `PermissionService` (not duplicated). Because the JWT still
carries only roles (`permissions` empty), the `roles` bridge governs visibility today and flips
to pure permission codes automatically once the backend authorises on permissions — the same
deferred step tracked under *Next Task*.

**Frontend company profile** (`features/company`, UI-06, 2026-07-27): view + edit of a
tenant, API-only, no mock. New routes `companies/:id` (profile) and `companies/:id/edit`
(both `SUPER_ADMIN`, matching the backend `CompanyController` guard); the companies table
now row-clicks into the profile. `company.model.ts` gained `CompanyResponse`/`CompanyProfile`
(full, mirrors backend DTO), `CompanyRequest` (PUT body) and `SubscriptionPlanOption`.
`CompanyService` added `getProfile`, `update` (PUT full-replacement, carries last-read
`version` for 409 optimistic-lock), and `plans()` (active `/subscription-plans` for the
dropdown). Pages: `company-profile.ts` (assembles the section components), `company-profile-edit.ts`
(forkJoins profile + plans, handles 409 by reloading, toasts success). Presentational
components in `components/`: `CompanySummaryCard` (identity banner), `CompanyForm` (reactive,
validators mirror the DTO — GSTIN/PAN/phone/website/currency regex — full-replacement PUT so
it passes through subscription dates + version so nothing is wiped), `CompanyLogo` (logo +
favicon; URL is source of truth, picked file previewed locally only — no upload endpoint yet),
`ContactInformation`, `AddressInformation`. **Field mapping note:** the prompt's "Telephone"
→ backend `alternateMobile`, "Pincode" → `postalCode`; backend has **no** companyType/area
fields, so they were dropped to stay API-honest. Reuses shared `UiCard`/`UiInput`/`UiSelect`/
`UiButton`/`UiLoader`/`StatusBadge`.

**Frontend user management** (`features/users`, UI-07, 2026-07-27): full User Management
module, API-only, no mock. Mirrors the backend's 15 `/users` endpoints. New routes
`users/new` + `users/:id` + `users/:id/edit` (list/view for `ADMINS`, create/edit
`COMPANY_ADMIN`). **Models** (`core/models/user.model.ts`): `AppUser` (list, mirrors
`UserSummaryResponse`), `UserProfile` (full, mirrors `UserResponse`), `CreateUserRequest`,
`UpdateUserRequest`, `UserSearchRequest`, `UserStatus`/`Gender`. **Service**: all reads,
writes, lifecycle (activate/deactivate/lock/unlock), passwords (reset/change), roles
(assign/remove), placement (assignBranch/Hub), plus `roles()`/`branches()`/`hubs()`/
`managers()` lookups for dropdowns off the live list endpoints. **Pages**: `user-list`
(server pagination, sort, debounced search, advanced-filter drawer, CSV export, gated row
actions), `user-create`, `user-edit` (PUT full-replacement, 409 reload), `user-view` (full
profile + gated action bar). **Components** (`components/`): `UserForm` (reactive,
create/edit; identity + password + roles are create-only, edit carries `version`),
`UserTable` (spec columns + permission-gated kebab menu), `UserFilter`, `UserStatusBadge`
(hard-lock trumps status), and four dialogs — `AssignRoleDialog` (chip add/remove, self-
calls API), `AssignBranchDialog`, `AssignHubDialog`, `ResetPasswordDialog`. **Permissions**:
actions gated by `PermissionService.canAccess` with a role fallback (`COMPANY_ADMIN`) OR the
codes `USER_CREATE`/`USER_UPDATE`/`USER_DELETE`/`USER_ASSIGN_ROLE`/`USER_RESET_PASSWORD` —
flips to pure codes once the backend authorises on permissions. Self-action guards
(no self lock/deactivate/delete) mirror the backend. Reuses shared UI + `NotificationService`
+ `DialogService`. Build verified (`ng build` clean).

**Frontend role management** (`features/roles`, UI-08, 2026-07-28): full Role Management
module, API-only, no mock; mirrors the backend `/roles` endpoints. Replaced the earlier
stub (bare list + two-method service). New routes `roles/new` (also the **Clone** flow via
`?cloneFrom=<id>`), `roles/:id` (view) + `roles/:id/edit` (edit); list/view for `ADMINS`,
create/edit `COMPANY_ADMIN`. **Models** (`core/models/role.model.ts`): `CompanyRole` reshaped
to `RoleSummaryResponse` (typed `roleType`/`status`, `permissionCount` — **the list carries a
count, not a permissions array**); added `RoleProfile` (full, mirrors `RoleResponse`),
`CreateRoleRequest`, `UpdateRoleRequest` (carries `version`), `RoleSearchRequest`,
`RoleType`/`RoleStatus`/`ROLE_TYPES`. **Service**: list/get/assignable/create/update/remove +
activate/deactivate. **Pages**: `role-list` (server pagination, sort, debounced search,
filter drawer, CSV export, gated row actions), `role-create` (POST; prefills from the source
role's profile when cloning — code left blank, no backend clone endpoint), `role-edit`
(full-replacement PUT, 409 reload), `role-view` (Details/Audit/Permissions cards + gated
action bar). **Components**: `RoleForm` (reactive; `roleCode` create-only with live
uppercase/underscore preview, read-only in edit, emits `version`), `RoleTable` (Role
Code/Name/Type/Grants/Flags/Status/Actions; kebab hides actions without permission and
against business rules — system/default roles show no Delete, the default no Deactivate),
`RoleFilter`, `RoleStatusBadge`. **Permissions**: gated via `canAccess` with a `COMPANY_ADMIN`
fallback OR-ed with `ROLE_VIEW`/`ROLE_CREATE`/`ROLE_UPDATE`/`ROLE_DELETE`. **Honesty note:**
the spec's list columns "Description" and "Created Date" are dropped — `RoleSummaryResponse`
has neither; both appear on the detail view. Reuses shared UI. Build verified (`ng build` clean).

**Frontend permission management** (`features/permissions`, UI-09, 2026-07-28): full Permission
Management module, API-only, no mock; mirrors the backend `/permissions` catalogue and the
`/roles/{roleId}/permissions` grants. Replaced the earlier stub (bare list + one-method service).
Routes `permissions` (catalogue list), `permissions/assign` (**Role→Permission assignment**,
`COMPANY_ADMIN`), `permissions/:id` (details); list/details for `ADMINS`. `assign` is declared
**before** `:id` so it is not swallowed by the id param. **Models** (`core/models/permission.model.ts`):
reworked `Permission` (adds audit fields, typed `PermissionAction`); added `PermissionGroup`
(client-side module grouping), `RolePermissionResult` (mirrors `RolePermissionResponse` —
granted/revoked/skipped/**rejected**/effectivePermissions), `PermissionAssignmentRequest`
(`permissionCodes` **not ids** + `replaceExisting`), `PermissionSearchRequest`, `PERMISSION_MODULES`
(28), `PERMISSION_ACTIONS` (15), and helpers `groupByModule`/`prettyToken`. **Service**:
list/get/grantable + rolePermissions/assign/revoke. **Pages**: `permission-list` (server pagination,
sort, debounced search, advanced filter drawer, CSV export, spec columns Module/Code/Name/
Description/Status, row-click → details, "Assign to Role" for `COMPANY_ADMIN`), `permission-assign`
(role picker via `RoleService.assignable`, Tree **or** Matrix view toggle, local filter, select/
deselect/expand/collapse-all, dirty tracking + sticky save/reset bar, one bulk `replaceExisting=true`
save, trusts the server `effectivePermissions` and surfaces `rejected` as a distinct error toast),
`permission-view` (Details/Audit cards). **Components** (`components/`): `PermissionTree` (stack of
cards + expand/collapse/select toolbar), `PermissionMatrix` (modules × actions grid, checkboxes at
existing intersections, em-dash elsewhere, own horizontal scroll), `PermissionFilter` (module/action
multiselect, status, kind, plan-gated, resource), `ModulePermissionCard` (one expandable module,
MatCheckbox with module-level indeterminate, plan-gated lock + inactive dimming). **Permissions**:
list gated via `canAccess` (`ADMINS` OR `PERMISSION_VIEW`), assignment (`COMPANY_ADMIN` OR
`PERMISSION_ASSIGN`); nav gained an "Assign Permissions" leaf. **Honesty note:** the client does
**not** pre-compute which permissions a company's plan excludes (no endpoint exposes the plan's
feature flags to the UI) — plan-gated rows stay selectable and the backend's `rejected` list is the
source of truth, surfaced after save. Reuses shared UI + `RoleService`. Build verified (`ng build` clean).

**Frontend branch management** (`features/branch`, UI-10, 2026-07-28): full Branch (Vendor)
Management, API-only, no mock; mirrors the backend `/branches` endpoints. Replaced the earlier
stub (bare list + partial service). Routes `branches/new` (`COMPANY_ADMIN`), `branches/:id`
(view, `MANAGERS`) + `branches/:id/edit` (`COMPANY_ADMIN`+`BRANCH_MANAGER`); list/view for
`MANAGERS`. **Models** (`core/models/branch.model.ts`): reshaped `Branch` to `BranchSummaryResponse`
(list projection — `managerId`, headline flags only), added `BranchResponse` (full),
`CreateBranchRequest`/`UpdateBranchRequest` (update carries `version`, no code/status/manager),
`BranchSearchRequest`, `BRANCH_TYPES`. **Service**: list/get/create/update/remove +
activate/deactivate/assignManager + a `managers()` lookup (active company users, for the picker
and the id→name map). **Pages**: `branch-list` (server pagination, sort, debounced search, filter
drawer, CSV export, gated row actions, manager-name resolution), `branch-create`, `branch-edit`
(full-replacement PUT, 409 reload), `branch-view` (summary banner + Contact/Address/Operations/
Audit cards + gated action bar). **Components**: `BranchForm` (reactive create/edit; `branchCode`
create-only, manager set on create then read-only, MatSlideToggle capability flags, working-days
multiselect ↔ CSV, time inputs), `BranchTable`, `BranchFilter`, `BranchStatusBadge`,
`BranchSummaryCard`, `AssignManagerDialog`. **Permissions**: gated via `canAccess` — create/delete
`COMPANY_ADMIN`|`BRANCH_CREATE`/`BRANCH_DELETE`, update `COMPANY_ADMIN`+`BRANCH_MANAGER`|`BRANCH_UPDATE`.
**Honesty note (big):** the UI-10 spec's "vendor" fields don't all exist on the backend branch —
Owner Name / Contact Person and GST / PAN have **no column** (GST/PAN are company-level), and a
branch has **no hub relation** (hubs are their own module; a user carries `hub_id`). So: "Vendor
Type" → `branchType`, "Area" → `district`, "Pincode" → `postalCode`, "Owner" → the branch
**manager**, and the requested "Assign Hub" dialog is instead an **Assign Manager** dialog (the real
per-branch `assign-manager` endpoint). List "Mobile"/"Hub Count" columns dropped (summary carries
neither); Location + capability chips fill the row. Reuses shared UI + users lookup. Build verified.

**Frontend branch wallet** (`features/branch-wallet`, UI-11, 2026-07-28): full Branch Wallet,
API-only, no mock. Every branch owns one prepaid wallet, created with the branch — so the module
has reads + money ops but **no create/delete**. The `/branch-wallets` backend module does not exist
yet, so this is built against a defined contract and degrades like the dashboard (empty on read
404, API error surfaced on write). Routes (all `roleGuard`): `finance/branch-wallet` (overview),
`finance/branch-wallet/:id` (dashboard), `.../:id/transactions`, `.../:id/recharge`. **Models**
(`core/models/wallet.model.ts`): `Wallet` (list — three balances + branch), `WalletResponse` (full
— adds today credit/debit, last recharge, limits), `WalletTransaction`, the Razorpay triple
`RechargeRequest`/`RechargeOrder`/`RechargeVerification`, `CreditRequest`/`DebitRequest`,
`TransactionSearchRequest`, enums + `prettyToken`/`formatMoney`. **Service** `BranchWalletService`:
list/get/mine/byBranch, transactions, recharge/verifyRecharge, credit, debit. **New core service**
`RazorpayService` — lazy-loads `checkout.razorpay.com/v1/checkout.js` on demand (never bundled), so
there is no hard Razorpay dependency; online recharge does create-order → Checkout → verify, offline
methods (CASH/CHEQUE/BANK_TRANSFER) settle in one call. **Pages**: `wallet-list` (overview grid),
`wallet-dashboard` (hero — summary banner + six balance cards: current/available/hold/today credit/
today debit/last recharge + gated Recharge/Credit/Debit + recent txns), `wallet-transactions` (full
ledger with the spec columns + filter drawer + CSV + per-row receipt), `wallet-recharge` (dedicated
Razorpay flow with a payment-status timeline + receipt). **Components**: `WalletSummaryCard`,
`BalanceCard`, `TransactionTable`, `TransactionFilter`, `RechargeDialog`/`CreditDialog`/`DebitDialog`,
`WalletStatusBadge`, `TransactionTypeBadge`. Receipts are client-generated (`receipt.util.ts`,
self-contained HTML) from the real settled txn until a backend PDF endpoint exists. **Permissions**:
`BRANCH_WALLET_VIEW` / `_TRANSACTION_VIEW` / `_RECHARGE` / `_CREDIT` / `_DEBIT`, each OR-ed with a
role fallback (view = admins+BRANCH_MANAGER+FINANCE_USER; credit/debit = COMPANY_ADMIN). **Honesty
note:** the spec's "Credit Wallet"/"Debit Wallet" *pages* are the `CreditDialog`/`DebitDialog`
modals (the components the spec also lists), launched from the dashboard — no separate routes.
`ng build` clean.

**Frontend master data** (`features/masters`, UI-12, 2026-07-28): the twelve reference lists,
API-only, no mock. **Four components serve all of them**, selected by the `:master` route
parameter: `master-list` (paged table, sort, debounced search, filter drawer, CSV export,
gated row actions, plus a "Seed standard set" button on the five seeded catalogues),
`master-form-page` (create and edit in one — the difference is fetching first, sending the
`version`, and reloading on 409), `master-view` (detail cards grouped as the form groups
them), and `components/`: `MasterTable`, `MasterForm`, `MasterFieldControl`, `MasterFilter`.
Forty-eight hand-written screens differing only in field names would have drifted by the
twelfth, so the differences live in **`master.config.ts`** — columns, field descriptors
(kind, validators mirroring the DTOs, hints, lookup source, group), filters, export columns.
Adding a backend field is a one-line data change. **Models** (`core/models/master.model.ts`):
`MasterRecord` (the shared head) plus the twelve row interfaces. **Service**
(`master-data.service.ts`): generic CRUD against `/master/{path}`, lifecycle verbs, bootstrap,
and picker options cached for the session and dropped on every write (the row just created is
usually the next one picked); `'branches'` is a valid picker source and reads `/branches`,
because a route's two ends are branches, not a master. Routes `masters/:master`,
`masters/:master/new`, `masters/:master/:id`, `masters/:master/:id/edit` — `new` before `:id`.
Nav's aspirational Masters entries were replaced with the twelve real ones; the dead
`/masters/zone` link is gone. `UiInput` gained an optional `errorMessage` override so a field
can show the message its own pattern deserves. **46 frontend tests** on a newly configured
`@angular/build:unit-test` (vitest) target — the project had no test runner before this.
`ng build` clean.

**Backend:** platform admins (`SUPER_ADMIN`/`PLATFORM_ADMIN`) may sign in with **no
company code** — `AuthService.resolvePlatformTenant` derives their home tenant server-side
(see CHANGELOG Unreleased). Local dev logins: `LEGACY_CO` / `asha@legacy.test` (COMPANY_ADMIN)
and `ravi@legacy.test` (SUPER_ADMIN), both `Password@123`; CORS allows `http://localhost:4200`.

---

## Current Module

**Shipment Movement — COMPLETE**, verified live over HTTP and through the Angular
console (v0.17.0, 2026-08-03). New package `com.courier.modules.manifest` (the
minimal Manifest prerequisite this module needed but nothing had built), migration
`V19`, extends `com.courier.modules.shipment`. See *Current Version* above for the
full summary and `MEMORY/modules/shipment-movement.md` for the complete detail.
**Stop here per instruction — do not start Finance or Reports next.**

Previously current:

**Shipment Booking — COMPLETE**, verified live over HTTP and through the Angular
console (v0.16.0, 2026-07-30). New package `com.courier.modules.shipment`, migration
`V17`, 5 tables, 8 endpoints under `/api/v1/shipments`, 7 frontend pages incl. a
four-step booking wizard. Full detail in `MEMORY/modules/shipment-booking.md`.

Previously current:

**Pricing Engine — COMPLETE**, verified live over HTTP against a temporary instance on the
shared dev database (v0.15.0, 2026-07-30). New package `com.courier.modules.pricing`, no
migration, no persistence, one endpoint `POST /api/v1/pricing/calculate`. Full detail in
`MEMORY/modules/pricing-engine.md`.

Previously current:

**Rate Master — COMPLETE**, verified against MySQL and the Angular console (v0.14.0,
2026-07-30). New package `com.courier.modules.rate`, migration `V16`, 7 endpoints under
`/api/v1/rates`. Full detail in `MEMORY/modules/rate-master.md`.

Previously current:

**Route Management — COMPLETE**, verified against MySQL and the Angular console
(v0.13.1, 2026-07-30). Extends Master Data's existing `Route` (booking/delivery branch
pair, distance, transit) rather than duplicating it as a second module — see
`MEMORY/modules/master-data.md` §"Route Management (2026-07-30 extension)" for the full
detail. Migration `V15`, no new endpoints (the existing seven-verb
`/api/v1/master/routes` already covered create/update/read/list/delete/activate/
deactivate), no new permission codes.

Previously current:

**Customer Management — COMPLETE**, verified against MySQL and the Angular console
(Phase 7, v0.13.0, 2026-07-30). Reusable customer master data, independent of Shipment
Order, with a one-to-many address book. New package `com.courier.modules.customer`,
migration `V14`, 9 endpoints. Full detail in `MEMORY/modules/customer.md`.

Previously current:

**SUPER_ADMIN / Platform Console** — code complete, **not yet run against MySQL**.

The super admin owns the platform and nothing operational. What it can now do that it
could not before:

| Capability | Endpoint |
|---|---|
| Create / update / activate a company | `POST` `PUT` `PATCH …/activate` `/api/v1/companies` |
| **Deactivate** a company | `PATCH /companies/{id}/deactivate` |
| **Assign** a subscription | `POST /companies/{id}/subscription` |
| **Renew** a subscription | `POST /companies/{id}/subscription/renew` |
| **Suspend** a subscription | `POST /companies/{id}/subscription/suspend` |
| **Company statistics** (user / branch / role counts, quota headroom) | `GET /companies/{id}/statistics` |
| **Platform dashboard** (totals, renewals worklist) | `GET /super-admin/dashboard` |
| **Create / list platform operators** | `POST` `GET /super-admin/users` |
| **Global masters** — the shared geography | `/api/v1/global-masters/**` |

What it deliberately **cannot** do: create a branch, a shipment, a customer or a manifest,
or recharge a wallet. Those are a company's own operations. A record a platform operator
created would be indistinguishable in the data from one the company created itself, and
for money that is precisely what a ledger exists to prevent. `SuperAdminBoundaryTest`
asserts this by reading the `@PreAuthorize` expressions directly — it is the one test in
the suite that asserts something does *not* work.

**Company creation now returns a temporary password.** The first administrator is created
`PENDING` with a policy-valid generated password returned **once** in
`provisioning.temporaryPassword`, plus an activation email through the new
`NotificationPort.sendCompanyActivation(...)`. This reverses decision 21 for that account
— see decision 49 below. The port is never given the password.

**The geography masters are global (V12).** Country, state, district, city, area and
pincode are one catalogue shared by every company, owned by
`GlobalMasters.PLATFORM_COMPANY_ID`, written only by `SUPER_ADMIN`, read by anyone signed
in, and served from `/api/v1/global-masters/**`. The other six lists stay company-owned.
See decision 50.

**`V12` and `V13` are now applied** — both ran clean against MySQL on 2026-07-30 as part
of rebuilding the dev database for the `tenant_id` → `company_id` rename (see CHANGELOG).
That rebuild also surfaced and fixed a real bug in `V12`'s `TENANT_ADMIN` rewrite (it
referenced a `tenant_id`/`company_id` column `user_roles` has never had). **Still open:**
the fresh dev database had no cross-company duplicate geography rows or legacy
`TENANT_ADMIN` holders to exercise the merge/rewrite logic against real data, and most of
the SUPER_ADMIN endpoints beyond company creation, `/companies/{id}/statistics` and the
global-masters write/403 check are still unexercised over HTTP (deactivate, the three
subscription acts, `/super-admin/dashboard`, `POST /super-admin/users`).

Previously completed:

**Master Data** — COMPLETE and verified (v0.11.0), bar the cross-company runtime check.
First module of **Phase 6**, and the first in a **new** package, `com.courier.modules.master`.

The twelve reference lists a company configures before it can book anything: the geography
hierarchy (country → state → district → city → area → pincode) and the operational
catalogues (vehicle type, package type, service type, payment mode, weight slab, route).
All tenant-owned (`V11`). **85 endpoints** under `/api/v1/master/**` — seven per list plus
an idempotent `POST /master/bootstrap`.

The structural idea: every master row shares one head — `code` (uppercased, immutable,
unique per company), `name`, `description`, `status`, `display_order` — so one
`MasterDataEntity`, one `MasterDataRepository<E>`, one criteria/specification pair, one
`AbstractMasterDataService<E>` and, on the frontend, one set of four components serve all
twelve. The differences live in data: entity subclasses on the backend,
`master.config.ts` on the frontend. RBAC: `COMPANY_ADMIN` writes, any authenticated company
user reads (a booking clerk needs the pincode and service-type lists), `SUPER_ADMIN` reads
across companies. See `MEMORY/modules/master-data.md`.

Previously completed:

**Branch Wallet** (v0.10.0) — COMPLETE and verified, bar the cross-tenant runtime check.
First module of **Phase 5 — Finance**, in the package `com.courier.modules.finance`.

Every branch owns exactly one prepaid wallet (`wallets`, V10), created with the branch; the
balance moves only through an append-only ledger (`wallet_transactions`). The central rule is
structural, not documentary: `Wallet` has no balance setter, `WalletServiceImpl.post(...)` is
the only caller of `applyCredit`/`applyDebit` and writes the entry in the same transaction, and
`WalletService` exposes no method that takes a balance. Money paths use a pessimistic row lock,
not optimistic versioning. 7 endpoints under `/api/v1/branch-wallet` (singular — no wallet id in
any URL). Razorpay behind a `PaymentGatewayPort` whose **default** implementation refuses online
recharge; the credited amount always comes from the gateway, never the request body.
See `MEMORY/modules/branch-wallet.md`.

**Branch Management** (v0.9.0) — first module of **Phase 4 — Organization Structure**;
lives *inside* `modules/company`.

`branches` (V9), tenant-owned: physical booking/delivery offices, code+name unique per
company, 5 branch types, capability flags, geo + hours, one manager. 9 endpoints. RBAC:
`COMPANY_ADMIN` manages all; `BRANCH_MANAGER` updates/staffs only the branch they manage;
other users read only their assigned branch; `SUPER_ADMIN` reads across companies.
`assign-users` sets `users.branch_id`. FKs to/from users deferred (dev orphan rows). See
`MEMORY/modules/branch.md`.

**Company Settings** (v0.8.0) — new wide `company_settings_config` (V8), one typed row per
company across 8 tunable sections; distinct from the pre-existing key/value
`company_settings` (V4). Get-or-create, merge-not-blank writes. See
`MEMORY/modules/company-settings.md`.

**User Management** (v0.7.0) — the `users` table became a **shared kernel**: `V7` extended
it with HR/profile columns, and `company.User` (`@Entity "CompanyUser"`) maps the same
table auth's `User` does. `user_company_roles` links users to permissioned roles. 15
endpoints; `COMPANY_ADMIN` manages, branch/hub managers read their own placement.
See `MEMORY/modules/user.md`.

**Permission Management** (v0.6.0) — `permissions` catalogue (174 rows, platform-level)
and `role_permissions` grants (tenant-owned) replaced the permission enum and its element
collection. `SUPER_ADMIN` writes the catalogue; `COMPANY_ADMIN` grants within their plan.
See `MEMORY/modules/permission.md`.

**Role Management** (v0.5.0) — per-company roles inside `modules/company`. `V5` extended
`company_roles` (added `role_type`, `is_default`, a `status` enum) and grew the seeded
catalogue from five roles to eight. `COMPANY_ADMIN` writes, `SUPER_ADMIN` reads. See
`MEMORY/modules/role.md`.

**`modules/company` (Company = Tenant)** — COMPLETE and verified (v0.4.0).

**The company IS the tenant.** This module replaced the planned `modules/tenant`, and as
of 2026-07-29 the word no longer appears anywhere in the code. Branches, hubs, service areas and
rate cards are later phases *inside* `modules/company`.

10 endpoints, 3 tables (`V4`), `SUPER_ADMIN` only. Creating a company runs a full
initialization in one transaction: generated `tenantId`, plan link, 8 default roles with
plan-gated permissions (five at v0.4.0; Role Management grew the catalogue), ~24 default
settings, and a `PENDING` first administrator created
through auth's `UserProvisioningService`. `CompanyTenantDirectory` finally displaces
`StandaloneTenantDirectory`, so slug login works and tenant status is enforced.
See `MEMORY/modules/company.md`.

- **`modules/subscription`** (v0.3.0) — platform-wide plan catalogue: pricing, quotas
  and feature flags. `null` means unlimited. See `MEMORY/modules/subscription.md`.
- **`modules/auth`** (v0.2.0) — login, logout, refresh with rotation + reuse detection,
  forgot/reset/change password, email verification, account lock/unlock, sessions and
  device management, token revocation. Still no `/register`: user creation is
  provisioning, now owned by `UserProvisioningService`. See `MEMORY/modules/auth.md`.

---

## Completed Modules

| Module | Status | Notes |
|---|---|---|
| `shared/domain` — BaseEntity, TenantAwareEntity | DONE | UUID PK, soft delete, optimistic locking |
| `shared/company` — CompanyContext | DONE | ThreadLocal + Hibernate filter + entity listener |
| Unit tests — JWT, CompanyContext, UUIDv7 | DONE | 25 tests, all passing |
| Runtime verification | DONE | V1–V9 applied and validated on MySQL 8.0.46; all endpoints exercised |
| `shared/security` — JWT | DONE | Stateless; principal built from claims, no DB hit |
| `shared/exception` — Global handler | DONE | RFC-ish error codes, no stack traces leaked |
| `shared/api` — ApiResponse wrapper | DONE | Uniform envelope + PageResponse |
| `shared/audit` — Audit logging | DONE | JPA auditing + `audit_logs` table + async writer |
| `shared/config` — Redis / Flyway / Swagger | DONE | Redis JSON serializer, Flyway baseline, OpenAPI 3 |
| Docker | DONE | Multi-stage Dockerfile + compose (app, MySQL 8, Redis) |
| `modules/auth` | **DONE** | 8 endpoints, 7 tables, DB-backed tokens, Redis denylist |
| `modules/subscription` | **DONE** | 7 endpoints, 1 table (`V3`), SUPER_ADMIN only |
| `modules/company` — Company aggregate | **DONE** | 10 endpoints, 3 tables (`V4`), the tenant root |
| `modules/company` — Role Management | **DONE** | 8 endpoints, extends `company_roles` (`V5`), 8 seeded roles |
| `modules/company` — Permission Management | **DONE** | 8 endpoints, `permissions`+`role_permissions` (`V6`), 174 rights |
| `modules/company` — User Management | **DONE** | 15 endpoints, extends `users` + `user_company_roles` (`V7`), shared kernel |
| `modules/company` — Company Settings | **DONE** | 8 endpoints, wide `company_settings_config` (`V8`), one row/company |
| `modules/company` — Branch Management | **DONE** | 9 endpoints, `branches` (`V9`), Phase 4 org structure |
| `modules/finance` — Branch Wallet | **DONE** | 7 endpoints, `wallets` + `wallet_transactions` (`V10`), Phase 5 |
| `modules/master` — Master Data | **DONE** | 85 endpoints, 12 tables (`V11`), Phase 6; one shared head serves all twelve |
| `modules/master` — Route Management | **DONE** | No new endpoint. `master_routes` (`V15`) gains `transit_hours`, `distance_unit` |
| `modules/master` — Global Masters | **DONE** | The six geography lists flipped platform-level (`V12`), `/global-masters/**` |
| `modules/company` — SUPER_ADMIN console | **DONE** | +8 endpoints (deactivate, 3 subscription, statistics, platform dashboard, 2 platform-user), `V12` |
| `modules/company` — Company Admin boundary | **DONE** | No new endpoint or migration. Branch creation grants the `BRANCH_MANAGER` company role; `CompanyAdminBoundaryTest` asserts the refusals and the isolation invariants |
| `modules/tenant` | **DELETED** | A company *is* the tenant; the package and its doc are gone |
| `modules/customer` — Customer Management | **DONE** | 9 endpoints, `customers` + `customer_addresses` (`V14`), Phase 7, new package |
| `modules/rate` — Rate Master | **DONE** | 7 endpoints, `rate_master` (`V16`), Phase 4, new package |
| `modules/pricing` — Pricing Engine | **DONE** | 1 endpoint, no migration, no persistence, new package; Strategy+Factory, reusable by Shipment/Quotation/API |
| `modules/shipment` — Shipment Booking | **DONE** | 8 endpoints, 5 tables (`V17`), new package; the core transaction, orchestrates Customer/Pricing/Wallet |
| `modules/company` — hubs | NOT STARTED | Later phase of the same module |
| Manifest Management | NOT STARTED | — |

---

## Next Task

**Shipment Booking just shipped (`V17`, new package `com.courier.modules.shipment`) —
stop here, per instruction. Do not start Manifest Management next.** Whoever resumes
should re-read this file and `MEMORY/modules/shipment-booking.md` first. When work does
resume, the candidate is Manifest Management (next migration is `V18`) — grouping
`BOOKED` shipments into a manifest and transitioning them through
`READY_FOR_MANIFEST`/`MANIFESTED`/`DISPATCHED`, the state-machine edges
`ShipmentStatus` already declares but this module never writes. Hub Management is the
other open candidate (`users.hub_id` already exists, `HUB_MANAGER` mirrors
`BRANCH_MANAGER` — reuse Branch as the template) and has no ordering dependency on
Manifest either way. The authorise-on-permissions capstone is also still open and gets
more overdue with every module that ships against role checks instead — Shipment
Booking's own endpoints are role-gated (`hasAnyRole(COMPANY_ADMIN, BRANCH_MANAGER,
OPERATOR)`), the same shape every module before it has shipped with.

**A genuine weight-slab gap no longer exists in the dev fixtures for the `RATE-PNQ-BOM-STD`
family** — `RATE-UI-TEST` (added during Rate Master's own Angular-console verification)
now fills what `GAP-LOW`/`GAP-HIGH` were built to leave empty. Anyone who wants to exercise
`FreightCalculator`'s "Weight Slab Not Found" refusal over live HTTP (rather than the unit
test that already covers it) needs to either deactivate `RATE-UI-TEST` first or add a new
combination with a deliberate gap.

**`V12` and `V13` are now applied** (2026-07-30, on a freshly rebuilt dev database, as part
of the `tenant_id` → `company_id` rename — see CHANGELOG). Both ran clean. **Still open:**
the fresh database has no cross-company duplicate geography rows and no legacy
`TENANT_ADMIN` holders, so the scenarios below were never actually exercised against real
data — only proven not to error on an empty one. Whoever seeds realistic duplicate/legacy
data next should still check:

1. **The geography merge.** Duplicate codes are deleted outright — not soft deleted, since
   a soft-deleted loser would keep its code reserved and defeat the exercise. Children are
   repointed at the survivor first, parents before children. Check the row counts before
   and after and spot-check that no `state.country_id` dangles.
2. **The name-collision rename.** Two codes for one name become `Maharashtra (MAHA)`. Check
   nothing was renamed that should not have been.
3. **`TENANT_ADMIN` → `COMPANY_ADMIN`** in `user_roles`. If any row survives, its owner
   cannot sign in — the enum constant no longer exists. (The `INSERT`/`DELETE` pair itself
   was buggy until 2026-07-30 — it referenced a column `user_roles` never had — and is now
   fixed and confirmed to run without error; still not exercised against an actual
   `TENANT_ADMIN` row.)
4. **The 24 permission rows.** `DefaultPermissionCatalogTest` asserts 211; confirm the
   table agrees.

Then exercise the new endpoints over HTTP: deactivate, the three subscription acts,
`/companies/{id}/statistics`, `/super-admin/dashboard`, `POST /super-admin/users`, and the
global masters under `/api/v1/global-masters/**` — including the negative cases, which are
the point of the module. A `COMPANY_ADMIN` must get 403 on every global-master write and on
every subscription endpoint.

**`V13`'s row count is confirmed**: `permissions` holds exactly 219 rows post-rebuild
(2026-07-30), matching `DefaultPermissionCatalogTest`. **Still open:** exercise the
branch-manager staffing scope over HTTP — a branch manager creating/updating/activating/
deactivating a user of their own branch (200), of a foreign branch (403), a hub placement
(403), assigning a branch-staff or custom role (200) vs. `COMPANY_ADMIN` (403). See
`MEMORY/modules/branch.md` §"A branch manager staffs their own branch".

**In the same run, exercise the branch-role grant** (2026-07-29, unverified): `POST
/branches` and confirm the response carries `branchUser.roleCode = BRANCH_MANAGER`, that a
`user_company_roles` row exists for the new account, and that a **second** branch in the
same company produces a second grant against the **same** role id — not a second role. Then
delete the company's `BRANCH_MANAGER` role and create a branch again: it must be recreated
from the catalogue with plan-gated permissions, and `SHIPMENT_IMPORT` must be absent when
`feature.BULK_BOOKING` is false.

**Then, still open and now more embarrassing:** provision an active `RIVAL_CO` admin so the
**cross-company checks** for Branch Wallet, Master Data, the global-master guard *and* the
`CompanyAdminBoundaryTest` refusals can actually run over HTTP. It has been the one item on
every module's verification list with nothing to attack from — and it is now the only thing
standing between the isolation invariants being asserted in Java and being demonstrated
against real data.

**Next module — Hub Management** (takes **`V17`** now — `V12` through `V16` are used, the
last by Rate Master; see *Current Module*), almost certainly the same shape as Branch: a
`hubs` table in `modules/company`, `users.hub_id` already exists, and `HUB_MANAGER`
scoping mirrors `BRANCH_MANAGER`. Reuse the Branch module wholesale as the template
(`MEMORY/modules/branch.md`). After that, **Shipment Booking** is the real next
milestone — it is the module `POST /rates/calculate` was built to be called from, and
Rate Master's own verification note flags that no `BRANCH_MANAGER`/`BOOKING_OPERATOR`
token has exercised it yet.

**Still outstanding from Phase 3 — the capstone, do not lose it:** *wire authorisation
onto permissions.* The machinery is all built and inert — users hold company roles
(`user_company_roles`), roles hold permissions (`role_permissions`) — but `@PreAuthorize`
still checks the JWT's `Role`-enum authority names, so re-permissioning a role or
reassigning a user changes nothing about what they can reach. This release made it worse
in one specific way worth knowing: there are now **211** permission codes and 24 of them
describe platform-operator rights that nothing consults, so the catalogue looks more
authoritative than it is.

```
company root (done) -> roles (done) -> permissions (done) -> users (done)
   -> super admin console (done) -> global masters (done)
   -> AUTHORISE ON PERMISSIONS  <-- next
   -> branches (done) + hubs -> master data (done) -> rate cards -> shipment
```

**Read before starting:**

1. No migration needed for the core of this — it is wiring, not schema. If a permission
   cache table is wanted, it takes **`V13`**.
2. Resolve a user's effective permission codes from their assigned company roles:
   `RolePermissionService.resolveEffectiveCodes(roleIds)`, where the role ids come from
   `user_company_roles`. Cache per request at least; a per-user cache (Redis) is the
   likely shape.
3. Put the codes into the security context — either as extra authorities on the JWT
   principal (recomputed at login/refresh) or resolved per request. Then change
   `@PreAuthorize` expressions from `hasRole('COMPANY_ADMIN')` to
   `hasAuthority('USER_CREATE')` and friends across the company and master modules.
4. **The platform tier must survive that change.** `SuperAdminBoundaryTest` reads the
   `@PreAuthorize` strings, so it will fail loudly if the expressions move to permission
   codes — update it deliberately rather than deleting it, and keep
   `DefaultRoleCatalog`'s platform-only exclusion (decision 55) in step.
5. Decide the JWT story: the auth `Role` enum authorities and the company permission
   authorities coexist for now; a clean end-state derives the JWT from the user's
   company roles. Touching auth's token issuance is the main risk — scope it deliberately.
6. Also still open: what happens to a role's holders when the role is deleted (today
   nothing reassigns them), and the `users.company_id -> companies.company_id` FK, still
   not added (see below).
7. **Every company-owned repository needs a cross-company leak test.** The pattern is in
   `RoleServiceImplTest` / `RolePermissionServiceImplTest` / `StateServiceImplTest` plus
   the manual checks in `MEMORY/modules/permission.md`.

**The physical `tenant_id` → `company_id` rename is done (2026-07-30) — see CHANGELOG.**
`Company.id`/`Company.companyId` staying two separate UUIDs was a deliberately separate
decision (19) and remains open on its own; it was never part of the column rename.

Still outstanding and small: the FK `users.company_id -> companies.company_id`, not yet
added (the dev database's fixtures were reseeded from scratch during the rename and no
longer carry the old orphan row this was originally deferred for, but the FK itself still
needs adding deliberately rather than as a side effect of another migration); **bulk
import** for master data (`MASTER_DATA_IMPORT` is seeded, the endpoint is not); and the
JWT `tid` fallback in `JwtTokenProvider`, which can be deleted once the longest refresh TTL has
passed since this deploy.

Before starting, read `MEMORY/modules/permission.md`, `MEMORY/modules/role.md` and
`MEMORY/modules/company.md`, then follow the Rules below.

---

## Architecture

- **Clean Architecture** — each feature is layered `api -> application -> domain <- infrastructure`.
  Dependencies point inward. `domain` has no Spring/JPA-framework imports leaking outward.
- **Package by Feature** — `com.courier.modules.<feature>`, not package-by-layer.
- **Multi-Tenancy** — Shared Database + Shared Schema, discriminated by a `tenant_id`
  column on every tenant-owned table. Enforced by a Hibernate `@Filter`, not by
  hand-written `where` clauses. See `MEMORY/adr/ADR-001.md`.
- **UUID Primary Keys** — `BINARY(16)` in MySQL, application-generated (UUIDv7-ish
  time-ordered) to keep InnoDB index locality.
- **Soft Delete** — `deleted` / `deleted_at` / `deleted_by`; `@SQLRestriction` filters
  reads globally. Hard delete is never exposed through the API.
- **Audit Logging** — `created_at/by`, `updated_at/by` on every entity via JPA auditing,
  plus an append-only `audit_logs` table for security-relevant events.
- **REST APIs** — `/api/v1/**`, uniform `ApiResponse<T>` envelope.

### Package Map

Repository root holds `MEMORY/` (project-wide), `backend/` (this Maven project)
and `docker/` (local stack). A `frontend/` can be added alongside `backend/`
without disturbing either.

```
backend/src/main/java/com/courier
├── CourierApplication
├── shared
│   ├── domain      BaseEntity, TenantAwareEntity, TimeOrderedUuid
│   ├── api         ApiResponse, PageResponse, RequestIdHolder
│   ├── exception   ErrorCode, ApiException + subtypes, GlobalExceptionHandler
│   ├── company     CompanyContext, CompanyResolutionFilter, CompanyEntityListener,
│   │               CompanyFilterAspect
│   ├── security    JwtTokenProvider, JwtAuthenticationFilter, JwtProperties,
│   │               AuthenticatedUser, Roles, SecurityUtils
│   ├── audit       AuditLog, AuditLogRepository, AuditAction,
│   │               AuditService, AuditLogWriter
│   └── config      SecurityConfig, RedisConfig, JpaAuditingConfig, OpenApiConfig,
│                   FlywayConfig, AsyncConfig, JacksonConfig, CorsProperties,
│                   RequestIdFilter
└── modules
    ├── auth          (done)  api / application / domain / infrastructure
    │                 Also owns SuperAdminAccountService (platform-tier accounts) and
    │                 UserProvisioningService (admin / branch user / super admin).
    ├── subscription  (done)  api / application / domain — platform-level, no tenant_id
    ├── company       (tenant root + roles + permissions + users + settings + branches done; hubs/rates later)
    │                 api / application / domain / infrastructure
    │                 Company = the tenant. company_roles + company_settings are
    │                 tenant-owned; CompanyTenantDirectory serves auth.
    │                 CompanyBranchDirectory serves finance,
    │                 CompanyMasterBranchDirectory serves master.
    │                 CompanyDashboardService is the SUPER_ADMIN console's read model;
    │                 SuperAdminController is the platform console.
    ├── finance       (branch wallet done)
    │                 api / application / domain / infrastructure
    │                 One prepaid wallet per branch + an append-only ledger.
    │                 Owns BranchDirectoryPort and PaymentGatewayPort (Razorpay).
    ├── master        (done)  api / application / domain / infrastructure
    │                 The twelve reference lists. One shared head (code/name/status/
    │                 order) lets one abstract service and one repository serve all
    │                 twelve. Owns BranchLookupPort; company supplies the adapter.
    ├── rate          (done)  api / application / domain
    │                 Company rate cards: one weight slab per Route + Service Type +
    │                 Package Type + Payment Mode. Depends forward on master's
    │                 RouteService/ServiceTypeService/PackageTypeService/
    │                 PaymentModeService — a cross-feature dependency, not a port.
    └── shipment      (not started)

modules/tenant no longer exists — a company IS the tenant. shared/tenant is now
shared/company. See "READ THIS FIRST" at the top of this file.

frontend/  Angular 20 admin console (separate build; API client only).
```

---

## Tech Stack

| Concern | Choice | Version |
|---|---|---|
| Language | Java | 21 (records, sealed, pattern matching, virtual threads ON) |
| Framework | Spring Boot | 3.4.1 |
| Security | Spring Security + JJWT | 6.x / 0.12.6 |
| Persistence | Spring Data JPA / Hibernate | 6.6.x |
| Database | MySQL | 8.4 |
| Migrations | Flyway | flyway-mysql |
| Cache / token store | Redis | 7.x (Lettuce) |
| Docs | springdoc-openapi | 2.7.0 |
| Build | Maven | 3.9+ |
| Container | Docker + Compose | multi-stage, distroless-ish JRE 21 |

---

## Recent Decisions

1. **ADR-001 — Shared Database, Shared Schema multi-tenancy.** Discriminator column
   + Hibernate filter. Chosen for cost and operational simplicity at expected scale.
2. **JWT carries `tenant_id`.** The tenant is resolved from the verified token, *not*
   from a client-supplied header. `X-Tenant-ID` is honoured **only** for `PLATFORM_ADMIN`.
   This closes the obvious cross-tenant escalation hole.
3. **No `UserDetailsService` in the foundation.** The filter builds an
   `AuthenticatedUser` straight from token claims, so `shared` does not depend on the
   (not-yet-written) `auth` module. Stateless, one fewer DB round trip per request.
4. **Time-ordered UUIDs** stored as `BINARY(16)` rather than `CHAR(36)` — 16 bytes vs 36,
   and monotonic prefixes avoid random-insert B-tree fragmentation.
5. **Virtual threads enabled** (`spring.threads.virtual.enabled=true`). Workload is
   IO-bound (MySQL + Redis); this is close to free throughput on Java 21.
6. **Soft delete via `@SQLRestriction`**, not `@Where` (deprecated in Hibernate 6.3+).
7. **`AuditLogWriter` is a separate bean from `AuditService`.** `@Async`/`@Transactional`
   are proxy-based; a self-invocation would have run the audit write synchronously
   inside the caller's transaction, defeating both annotations.
8. **Backend lives in `backend/`**, not at the repository root, so a `frontend/`
   can be added later without restructuring.
9. **JWT TTLs are validated in `JwtTokenProvider.init()`, not by annotations.**
   Bean Validation has no `@Positive` validator for `Duration` — it fails at
   startup with `HV000030`. Found by actually booting the app.
10. **Auth does not depend on the tenant module.** `TenantDirectoryPort` is the seam;
    `StandaloneTenantDirectory` serves until Phase 1 replaces it. This let auth ship
    first without pre-empting the `tenants` table. `LoginRequest` carries `tenantId`
    (UUID) rather than `tenantSlug` until then.
11. **Auth tokens live in MySQL; Redis holds only the access-token denylist.**
    Device and session management need durable, queryable rows. Redis is therefore
    optional: if it is down the denylist fails *open* (logged at ERROR), bounded by
    the 15-minute access TTL, while refresh revocation still holds in MySQL.
12. **`shared` gained one SPI, not a dependency.** `AccessTokenRevocationChecker`
    lives in `shared/security` with a no-op `@ConditionalOnMissingBean` default;
    auth supplies the Redis implementation. `shared` still imports nothing from
    `modules`.
13. **`SUPER_ADMIN` is a new top tier, not a rename of `PLATFORM_ADMIN`.** Super admin
    owns the platform (pricing, quotas); platform admin acts *on behalf of* tenants
    (lifecycle, impersonation). `TenantResolutionFilter` treats super admin as
    tenant-unbound and deliberately does **not** honour `X-Tenant-ID` for it.
14. **`null` means unlimited on every plan quota**, over a `-1` sentinel. A forgotten
    guard around a sentinel makes `current < -1` read as "over quota" and silently
    blocks everything; a forgotten null check throws and is caught in test. Compare
    through `SubscriptionPlan.withinLimit(limit, current)`.
15. **Migration versions follow build order, not plan order.** Auth `V2`, subscription
    `V3`, tenant `V4`. Flyway is forward-only with out-of-order disabled, so a
    "reserved" version could never be filled in later.
16. **`@PreAuthorize` goes on the service *implementation*, class-level.** On an
    interface it depends on the proxy strategy; on the impl it always applies. The
    role string is folded from the `Roles` constant at compile time, so a rename
    cannot leave a stale literal inside SpEL the compiler never checks.
17. **Optimistic locking takes the version from the client.** `@Version` alone only
    catches a conflict inside one transaction; the real hazard is two admins editing
    across two requests. `PUT` therefore requires the `version` last read.
18. **A company *is* a tenant, and the word "tenant" is gone from the code.** The product
    says "company", branches sit under a company, so the ownership root is `Company`.
    `modules/tenant` was folded into `modules/company` at v0.4.0; the 2026-07-29 rename
    finished the job across `shared/`, the JWT, the headers and the frontend. **The
    `tenant_id` column name survives on purpose** — see "READ THIS FIRST" at the top.
19. **`id` and `tenantId` are separate UUIDs on a company.** `id` is the row key;
    `tenantId` is the tenancy key stamped on every tenant-owned row and carried in the
    JWT. A leaked company id is then not a tenant id, and restructuring the table never
    forces a tenancy-key migration.
20. **Company roles are a table, not a second enum.** Five system roles are seeded per
    company with permissions filtered by the plan's feature flags, so a role can never
    start with a right the subscription excludes, and a company can recombine them later
    without a migration.
21. **The first admin's password is unusable by design.** 32 random bytes, hashed and
    discarded — never returned, logged or emailed. The account is `PENDING` and reached
    through email verification plus a password reset, so a leaked creation response
    grants nothing.
22. **Cross-module writes go through an application service, never a repository.**
    `modules/company` creates its administrator via auth's `UserProvisioningService`;
    it never touches the `users` table.
23. **Company events are in-process and sealed.** Published with
    `ApplicationEventPublisher`, consumed `AFTER_COMMIT` so nothing reacts to a
    rolled-back company. Sealed so a new event type cannot be silently ignored. An
    outbox with no consumer would be infrastructure for its own sake.
24. **Roles are per company and extend `company_roles`, not a new table.** Two tables
    meaning "a role" would drift within a release. The entity stays `CompanyRole`
    because `auth.Role` (JWT authorities) already owns that name.
25. **`roleType` is a functional grouping, not "system vs custom".** `isSystemRole`
    already records that; a second field meaning the same thing eventually contradicts
    the first. Same reasoning replaced the boolean `is_active` with a `status` enum.
26. **Reads and writes can have different audiences.** `RoleServiceImpl` uses per-method
    `@PreAuthorize`: `SUPER_ADMIN` may read any company's roles while investigating, but
    only `COMPANY_ADMIN` may change what their own staff can do. No URL rule can express
    that, so `SecurityConfig` only requires authentication on `/api/v1/roles/**`.
27. **A tenant id in a query string is overridden, never honoured.** For a
    `COMPANY_ADMIN` the search criteria are pinned to their own company, so a spoofed
    `tenantId` returns their own rows rather than someone else's.
28. **A catalogue that must be listed, searched and extended is a table, not an enum.**
    `Permission` was 30 enum constants; it is now 174 rows across 28 modules. The code
    is derived as `MODULE_ACTION` and immutable. The seeded set is generated from
    `DefaultPermissionCatalog` and a test asserts the migration's row count matches, so
    the two cannot drift.
29. **The grant is its own entity** (`role_permissions`), not an element collection, so
    "who granted this, and when" is answerable. `permission_code` is denormalised onto
    it because every authorisation check needs the code, not the id — one indexed read
    instead of a join on the hot path. Safe only because the code is immutable.
30. **Plan gating reads the company's seeded `feature.*` settings, not the subscription
    module.** Those rows exist so a tenant-scoped caller can check plan features without
    crossing a module boundary. A missing/false/non-boolean value denies — fails closed.
31. **A "prevent losing X" guard must compare before and after, not just check the
    after-state.** The lockout guard first refused every grant in a company that already
    lacked `ROLE_UPDATE` — including the grant that would fix it. Guarding on the
    after-state alone blocks exactly the situation that needs fixing. Caught by running
    it, not by unit tests that had mocked the pre-condition true.
32. **The `users` table is a shared kernel: two entities, one table.** `auth.User`
    authenticates; `company.User` (`@Entity "CompanyUser"`, `CompanyUserRepository`)
    administers. Each maps the columns its context owns; both map the shared ones with
    identical types so `validate` passes. Chosen over a second table (drift) or company
    importing auth's repository (dependency-arrow violation). The entity-name and
    Spring-Data bean-name collisions this causes must be resolved with distinct names —
    both surfaced only on boot, never in unit tests.
33. **A user's company roles (`user_company_roles`) are separate from the JWT authority
    roles (`user_roles`).** Assigning a company role does not yet change the token; that
    is the deferred "authorise on permissions" step. Keep the two straight — one is what
    the company says you may do, the other is what the token currently asserts.
34. **Two "settings" tables, kept apart.** `company_settings` (V4, key/value) holds
    plan-derived facts read by permission gating; `company_settings_config` (V8, wide,
    one row/company) holds admin-tunable behaviour. Entities `CompanySetting` (singular,
    kv) vs `CompanySettings` (plural, wide). Do not merge them — the kv table is
    load-bearing for gating, the wide one is the admin console's config.
35. **A wallet balance is never assigned, only moved.** `Wallet` has no balance setter;
    `applyCredit`/`applyDebit` are the only mutators, `WalletServiceImpl.post(...)` is their
    only caller and writes the ledger entry in the same transaction, and `WalletService`
    exposes no method taking a balance. Three layers, so no single lapse can let the balance
    and the ledger disagree. A "just fix this one wallet" setter is how they diverge.
36. **Money uses a pessimistic row lock, not `@Version`.** Two bookings against one branch
    must serialise. Optimistic locking would make one lose and show a customer a failed
    payment for a race they had no part in, and a retry loop over money is worse than a short
    `SELECT … FOR UPDATE`.
37. **The recharge amount comes from the gateway, never the client.** A valid signature proves
    the confirmation is genuine; it says nothing about the amount sent alongside it. So
    settlement calls `fetchPayment` and credits what the gateway reports — and order creation
    is its own endpoint, because the amount has to be fixed server-side before the browser is
    involved. The two-step flow is the security property, not ceremony.
38. **`wallet_transactions.payment_reference` is globally unique — the one key not scoped by
    `tenant_id`.** One merchant account serves the whole platform, so a payment id is globally
    unique at the gateway; a per-tenant key would let company B claim company A's payment. The
    duplicate check is native for the same reason: the Hibernate filter would confine it to the
    caller's own company, which is exactly the check that must not happen.
39. **No `PENDING` ledger row when a gateway order opens.** An entry is a record of money that
    moved; a row for a checkout the user may simply close makes every statement and every
    `balanceBefore/After` a guess. Intent goes to the audit trail instead.
40. **An unconfigured payment gateway refuses, it does not pretend.** The default
    `PaymentGatewayPort` bean throws 422 on every online recharge. A "skip verification when
    there's no secret" flag credits wallets for payments nobody made, and always reaches
    production. Manual credit by a named admin is the fallback.
41. **Finance owns `BranchDirectoryPort`; company supplies the adapter.** Same seam auth uses
    for tenants. Finance never holds a `Branch` — it gets a flat `BranchRef` — so the
    dependency arrow points at Finance's own abstraction rather than another feature's
    entities. Master Data repeats the pattern with its own `BranchLookupPort` rather than
    importing Finance's: reusing it would make Master depend on Finance to talk about
    branches, a worse arrow than duplicating a three-field record.
42. **Twelve master lists share one head, so one implementation serves them all.** `code`,
    `name`, `description`, `status`, `display_order` on every row means one
    `MappedSuperclass`, one `@NoRepositoryBean` repository (`#{#entityName}`), one criteria
    and specification pair, one abstract service, and four Angular components driven by a
    definition file. Forty-eight hand-written screens differing only in field names would
    have drifted apart by the twelfth. What is *not* shared: the public service methods,
    which are re-declared per list so each carries its own `@PreAuthorize` next to the
    method it guards — decision 16's reasoning applied to inheritance.
43. **A master code is immutable and its uniqueness check must see soft-deleted rows.**
    Operational records quote the code, so the update DTO has no field for it — nothing is
    silently dropped. The unique keys do not mention `deleted`, and `@SQLRestriction` hides
    exactly those rows, so the check is native (`MasterUniquenessChecker`) — the same fix
    `BranchRepository.isCodeTaken` already needed. Written once for twelve tables instead
    of twenty-four near-identical queries, with the table name checked against a closed set
    and every value bound.
44. **A weight slab is `[min, max)`, and the overlap rule runs on activation too.** Two
    active slabs both claiming 2 kg would price two identical shipments differently
    depending on row order — a customer complaint months later, not an error. MySQL has no
    exclusion constraint, so the rule lives in the service; checking only on save would let
    "deactivate, insert overlapping, reactivate" walk around it.
45. **A parent with children is not deletable, and a child cannot be created under an
    inactive parent — but editing one may proceed.** Cascading a delete down five levels of
    geography from one click is not something anyone expects until it has happened. And
    requiring an active parent merely to fix a typo would make a deactivated country freeze
    everything beneath it.
46. **Seeding master catalogues is an explicit, idempotent endpoint.** Doing it during
    company provisioning would point `modules/company` at a module it knows nothing about,
    and would leave every existing company empty anyway. Skipping codes that already exist
    means it can never resurrect a row an administrator deliberately removed. The geography
    hierarchy is not seeded at all — no set of countries and pincodes is right for an
    arbitrary courier.
47. **A branch's user is transactional with the branch; its wallet is not.** Both are
    "created with the branch", but a wallet can be conjured later from nothing
    (`getOrCreateForBranch`) while an account cannot — its generated password is readable
    only in the response to the call that made it. So the user shares the branch's
    transaction and the wallet keeps its AFTER_COMMIT listener. Which failures may roll back
    the branch is the design question, not where the code happens to sit.
48. **The branch account breaks decision 21 on purpose, and pays for it.** A branch counter
    has no mailbox, and the admin who opened the branch hands the login over in person. So the
    account is ACTIVE, pre-verified, and its password is returned once. The price is a
    credential in an API response — which is why it is never logged or audited, never
    readable again, and is shown in a dialog that says so.
49. **Decision 21 is now reversed for the company admin too, and 48 is the precedent.**
    The unusable password made the activation email the *sole* way into a brand-new
    company: a bounced or filtered message left the customer with an account nobody could
    enter and a super admin with nothing to hand them. So `provisionAdmin` generates a
    policy-valid temporary password and returns it once. What keeps this safe is not the
    password's secrecy alone but that the account is still `PENDING` — the password opens
    nothing until the activation link is followed, so the two factors must both arrive.
    **The `NotificationPort` is deliberately never given the password**: an email puts a
    plaintext credential in a mailbox, a mail server and every backup of both, at an
    address nobody has yet proved they own.
50. **The geography masters are global; the rest stay per company.** Per-company geography
    was defensible on paper — a company could name its cities the way its paperwork does —
    and wrong in practice: `PUNE` meant a different row in every company, so no rate card,
    serviceability check or report could be compared across two of them, and every new
    company started with an empty map of the country it operates in. Vehicle types and
    payment modes genuinely do differ per company and stay where they are.
51. **A global master row is owned by a reserved company id, not by nothing.** The tables
    keep `tenant_id`; global rows carry `GlobalMasters.PLATFORM_COMPANY_ID`. Two
    properties fall out for free: `(tenant_id, code)` becomes a *global* unique on code
    with no schema change, and the Hibernate filter stays switched on, so a code path that
    forgets to bind the platform id returns **nothing** rather than everything. The
    alternative — a second entity hierarchy with no owner column — would have duplicated
    the shared head, repository, specification and service that decision 42 exists to
    protect, because Java has one superclass. The constant is deliberately not a valid
    time-ordered UUID, so it can never collide with a generated `companyId`.
52. **Assigning, renewing and suspending a subscription are three endpoints, not fields on
    a `PUT`.** A full-replacement update can already change the plan id, but it says
    nothing about billing dates and audits as "company updated" — which makes "when did
    Acme move up to ENTERPRISE, and who approved it" unanswerable. Three endpoints, three
    audit actions, one event type.
53. **A renewal extends from the later of the current end and today.** Paying a week early
    must not forfeit the week already bought; paying a month late must not bill for the
    month the customer could not use. That single rule is why renewal is an operation
    rather than a settable `subscriptionEndDate`, and it is why the request carries no
    start date at all — it is not the caller's to choose.
54. **`INACTIVE` and `SUSPENDED` are different things, so deactivate is its own verb.**
    Both stop authentication, but only one is an accusation, and support quotes the
    difference back to the customer. Suspension therefore demands a reason and
    deactivation does not: demanding a justification for routine housekeeping only teaches
    operators to type "n/a".
55. **`COMPANY_ADMIN`'s permission set is derived, so the platform tier must be excluded
    explicitly.** `DefaultRoleCatalog` builds that role from the whole catalogue — which is
    what keeps a new feature usable without a migration, and is exactly why adding
    `SUBSCRIPTION_*`, `GLOBAL_MASTER_*` and `SUPER_ADMIN_USER_*` would silently have handed
    every company admin the ability to renew their own subscription. The exclusion is
    written in modules and actions, not codes, so a right added to a platform-only module
    is excluded the day it is added rather than the day someone notices.
56. **A boundary needs a test that asserts the "not".** Every other test in the suite
    asserts something works; `SuperAdminBoundaryTest` reads the `@PreAuthorize` expressions
    and asserts a super admin cannot create a branch or move a wallet. A guard is removed
    by loosening one annotation, and nothing else in the suite would notice. It also
    caught the real case: wallet recharge was a bare `isAuthenticated()`, and a super admin
    was kept out only by not happening to have a branch.
57. **"Creating a branch creates a default branch manager role" is an *ensure*, not a
    create.** A role per branch would put one row in `company_roles` for every office a
    courier opens, and re-permissioning "branch managers" would then mean editing a hundred
    rows that all say the same thing. The property that actually has to hold is that the
    account created with the branch can manage it on day one and that the role behind it
    exists — so `BranchRoleProvisioningService` grants the company's existing
    `BRANCH_MANAGER` and creates it from the catalogue only when there is none. It joins the
    branch's transaction, unlike the wallet, because an account holding no role is exactly
    the half-provisioned state that transaction exists to prevent, whereas a wallet can be
    conjured later from nothing.
58. **A boundary needs the test that asserts the "not" from *both* sides.**
    `SuperAdminBoundaryTest` keeps the platform out of a company's operations;
    `CompanyAdminBoundaryTest` keeps a company out of the platform's, and states the
    responsibility list in the positive direction so an omission is as loud as an excess.
    Its refusal helper reads the method guard **or** the class guard, whichever reaches the
    method: a class-level refusal is broken either by loosening the class annotation or by
    adding a looser one to a single method, and a test that checked only one would miss half
    the ways it goes wrong. Its isolation half is the same argument applied to Hibernate —
    `@Filter` is not inherited from a `@MappedSuperclass`, so an entity that forgets to
    repeat it is not slightly less filtered, it is unfiltered, and nothing else in the suite
    would notice.
59. **A shipped module's application interface may grow a method for a later module's
    sake, without becoming that module's dependency.** Rate Master needed to resolve a
    route from a booking/delivery branch pair, not an id; rather than reach into
    `RouteRepository` (a domain-layer import across a feature boundary, the thing the
    cross-feature rule exists to prevent), `RouteService` gained one new method,
    `findByBranches`, at the same `isAuthenticated()` tier every other read on it already
    has. The seam Master already exposes to other features (decision 41's pattern) is
    extended by the consumer's actual need, not routed around.
60. **A read that prices something is not the same right as reading the thing itself.**
    `RATE_MASTER_CALCULATE` is a new `PermissionAction`, not a reuse of `READ`: a
    booking-desk role that may quote a shipment's freight should not thereby see the
    whole rate card, and a back-office role that edits rates does not need to be granted
    anything extra to also calculate with them (`COMPANY_ADMIN`'s derived set already
    covers it). Classified alongside `READ`/`SEARCH`/`EXPORT`/`PRINT`/`DOWNLOAD` as
    non-mutating, because it changes nothing.
61. **A message built as `"a %s b" + "c".formatted(x)` is a bug the compiler cannot
    see.** Java's `+` binds looser than method calls, so `.formatted(x)` applies only to
    the second string literal; if that literal has no `%s` of its own, the call
    "succeeds" (extra arguments are silently ignored) and the first literal's `%s`
    reaches the caller unsubstituted. Two of Rate Master's refusal messages had exactly
    this shape, and passed `mvn test` because both existing assertions checked substrings
    that happened to live in the untouched half of the message. The fix is
    `("a %s b" + "c").formatted(x)` — parenthesise before calling — and the lesson for
    every future multi-line message built with `+` and `.formatted()` together is to
    wrap the whole concatenation first, and to have at least one test assert the
    *interpolated value* appears, not just a neighbouring word that would survive either
    way.

---

## Rules (do not skip)

Before implementing anything:

1. Read `MEMORY/AI_CONTEXT.md` (this file).
2. Read the related module document in `MEMORY/modules/`.
3. Update documentation if the plan changed.
4. Generate code.
5. Update `MEMORY/CHANGELOG.md`.
6. Update *Current Module* / *Completed Modules* / *Next Task* here.

The `MEMORY/` folder is the project's source of truth. If code and MEMORY disagree,
MEMORY is wrong and must be corrected — never silently drift.

---

## Invariants (never violate)

- Every company-owned table has a non-null owner column, named **`company_id`** in SQL
  (renamed from `tenant_id` 2026-07-30) and mapped as `companyId` in Java.
- No repository method may bypass the company filter without an explicit
  `@SkipCompanyFilter`-style justification and a code comment.
- **A `SUPER_ADMIN` never creates a company's operational records** — no branch, shipment,
  customer, manifest or wallet movement. A record a platform operator created is
  indistinguishable in the data from one the company created itself.
- **A generated password is returned exactly once, in the response to the call that
  created the account**, and is never logged, audited or emailed.
- No entity is ever hard-deleted through the service layer.
- No secret (JWT signing key, DB password) is committed — everything via env var.
- Every write endpoint emits an audit event.
- Authentication failures are indistinguishable to the caller: unknown email and
  wrong password both return `401 INVALID_CREDENTIALS`, and BCrypt runs either way.
- `forgot-password` always returns 200, regardless of whether the account exists.
