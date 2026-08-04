# Module — Master Data (Phase 6)

**Status:** COMPLETE and verified by running it (2026-07-28). **Amended 2026-07-29
(v0.12.0): the six geography lists are now global** — one catalogue shared by every
company, `SUPER_ADMIN`-written, served from `/api/v1/global-masters/**`. `V12` is written
but **not yet applied**. See *Global masters* at the end of this file.
**Package:** `com.courier.modules.master` — a new module, sibling of `company` and `finance`.
**Migration:** `V11__master_data.sql` (12 tables + 13 permission rows).
**Frontend:** `frontend/src/app/features/masters` (UI-12).

---

## What it is

The twelve reference lists a courier company configures before it can book anything:

```
Country -> State -> District -> City -> Area -> Pincode      (the geography hierarchy)

Vehicle Type   Package Type   Service Type   Payment Mode   Weight Slab   Route
```

All company-owned. Two couriers may each define a `BIKE` vehicle type or a `PUNE` city;
neither can see the other's. `tenant_id` is `NOT NULL` and leads every unique key and every
composite index.

---

## The shape that makes twelve lists tractable

Every master row carries the same head:

| Column | Notes |
|---|---|
| `code` | uppercased, **immutable**, unique per company |
| `name` | editable label |
| `description` | optional |
| `status` | `ACTIVE` / `INACTIVE` |
| `display_order` | ascending sort key for pickers |

plus `BaseEntity`'s audit, soft-delete and `version` columns. Anything else belongs to one
list and is declared on its entity.

That shared head is what lets **one** of each of these serve all twelve:

- `MasterDataEntity` — the `@MappedSuperclass`, with `applyInvariants()` `final` and a
  `applySpecificInvariants()` hook, so a subclass cannot skip the shared normalisation by
  forgetting a `super` call.
- `MasterDataRepository<E>` — `@NoRepositoryBean`, queries written with `#{#entityName}`.
- `MasterDataCriteria` + `MasterDataSpecifications` — shared fields as named properties,
  per-list filters in an `equalities` map keyed by **JPA attribute name**.
- `AbstractMasterDataService<E>` — read, search, activate, deactivate and soft delete in
  full; create and update as templates.
- `MasterUniquenessChecker` — the one place native SQL is assembled.
- `MasterSortSupport` — the sort whitelist and the 100-row page cap.
- On the frontend, `master.config.ts` — the definitions; four components render them.

**The public service methods are still written out twelve times**, one line each, because
each carries its own `@PreAuthorize`. That is deliberate: a class-level annotation could
not express "reads for any company user, writes for `COMPANY_ADMIN`", and an annotation on
an inherited method resolves through the proxy in a way decision 16 in `AI_CONTEXT.md` was
written to avoid.

---

## Tables (V11)

| Table | Own columns beyond the head |
|---|---|
| `master_countries` | `iso_code2`, `iso_code3`, `dial_code`, `currency_code` |
| `master_states` | `country_id` **NOT NULL**, `gst_state_code` |
| `master_districts` | `state_id` **NOT NULL** |
| `master_cities` | `district_id` **NOT NULL**, `is_metro`, `city_tier` |
| `master_areas` | `city_id` **NOT NULL** |
| `master_pincodes` | `area_id` **NOT NULL**, `serviceable`, `cod_available`, `prepaid_available`, `pickup_available`, `zone` |
| `master_vehicle_types` | `capacity_kg`, `capacity_cft`, `wheel_count`, `requires_permit` |
| `master_package_types` | `is_document`, `fragile_by_default`, `max_weight_kg`, `default_*_cm` |
| `master_service_types` | `delivery_days`, `is_express`, `cutoff_time`, `priority` |
| `master_payment_modes` | `collect_at_booking`, `collect_at_delivery`, `requires_credit_account`, `is_cash_on_delivery` |
| `master_weight_slabs` | `min_weight`, `max_weight`, `weight_unit` |
| `master_routes` | `booking_branch_id`, `delivery_branch_id`, `distance_km`, `transit_days`, `via` |

**Unique keys.** `(tenant_id, code)` on all twelve. Name uniqueness differs by level: global
per company for the flat catalogues and countries, **within the parent** for state /
district / city / area (two countries may each have a "Western Province"). Pincodes have no
name rule — the pincode itself is the key, and two post offices in one area really can
share a locality name. Routes add `(tenant_id, booking_branch_id, delivery_branch_id)`.

**None of the unique keys mention `deleted`**, so a soft-deleted row keeps its code
reserved and no new row can inherit the meaning of one that historical shipments quote.

**FKs.** Within the module the hierarchy FKs are real and `RESTRICT`. FKs that would reach
*out* of the module are deliberately absent — `master_routes.booking_branch_id` is
validated through a port, not a constraint, following the same reasoning as
`branches.manager_id`.

---

## Endpoints — 85

`/api/v1/master/{list}` for each of the twelve, each with seven verbs:

```
POST   /api/v1/master/{list}                 create            COMPANY_ADMIN
PUT    /api/v1/master/{list}/{id}            full replacement  COMPANY_ADMIN   (version required)
GET    /api/v1/master/{list}/{id}            read one          any company user
GET    /api/v1/master/{list}                 list              any company user
DELETE /api/v1/master/{list}/{id}            soft delete       COMPANY_ADMIN
PATCH  /api/v1/master/{list}/{id}/activate                     COMPANY_ADMIN
PATCH  /api/v1/master/{list}/{id}/deactivate                   COMPANY_ADMIN
```

plus `POST /api/v1/master/bootstrap` — seeds the standard catalogues, idempotently.

Route segments: `countries`, `states`, `districts`, `cities`, `areas`, `pincodes`,
`vehicle-types`, `package-types`, `service-types`, `payment-modes`, `weight-slabs`,
`routes`.

`SUPER_ADMIN` reads across companies when no company is bound (same pattern as Branch).

---

## Business rules

**Hierarchy.**
- A child's parent must exist **in the same company** — always. `findByIdWithinCompany`
  returns empty for a foreign row, so a spoofed parent id is refused as unknown rather
  than linked.
- The parent must be **active** only when it is being set or changed, and when the child
  is activated. Otherwise correcting a typo in a state whose country was deactivated last
  week would be impossible.
- A parent with live children **cannot be deleted** — 422 naming the count. Refused, not
  cascaded: taking five levels of geography out from one click is not something anyone
  expects until it has happened to their production data.
- Deactivating a parent is allowed and leaves children alone; nothing new may be filed
  under it.

**Pincodes.** The code is digits only (4–10), unlike every other list. Setting
`serviceable = false` folds `codAvailable` / `prepaidAvailable` / `pickupAvailable` down
with it — a pincode nobody delivers to cannot offer cash on delivery. Folding rather than
refusing keeps "stop servicing this" a one-field edit; the audit entry records it.

**Weight slabs.** The band is **half-open, `[min, max)`**. A 1 kg parcel falls in 1–5, not
0–1. No two **active** slabs of the same unit may overlap; adjacent is fine and is what
every real tariff looks like. Enforced in the service because MySQL has no exclusion
constraint — **and on activation too**, or deactivating a slab, adding an overlapping one
and reactivating the first walks straight around the rule.

**Payment modes.** No `PaymentModeType` enum beside the rows: the four canonical modes
*are* rows, and an enum repeating them is a second source of truth that drifts the first
time a company adds `PAID_ONLINE`. What booking branches on is behaviour, so behaviour is
flags, and contradictory combinations are refused with 422 — collecting at both ends, COD
that does not collect at delivery, a billed mode that also takes cash.

**Routes.** Direction matters: Pune→Mumbai and Mumbai→Pune are two rows (equal kilometres,
rarely equal transit days). One route per ordered pair; the two ends must differ; both must
be active branches of the company when set or changed. An existing route survives its
branch being deactivated — the shipments already on it still have to be delivered.

---

## Decisions worth keeping

1. **All twelve are company-owned, including geography.** Country and state look universal,
   but a courier codes and names them the way its own paperwork does, and the alternative —
   a platform-level table every company shares — makes one company's edit everyone's.

2. **A catalogue that must be listed, searched and extended is a table, not an enum**
   (decision 28, again). Vehicle types, package types, service types and payment modes are
   rows. A courier adding an EV three-wheeler should not need a release.

3. **Master owns `BranchLookupPort`; company supplies the adapter.** The same seam auth
   uses for companies and Finance for wallets. Deliberately *not* a reuse of Finance's
   `BranchDirectoryPort`: importing it would make Master depend on Finance to talk about
   branches, a worse arrow than duplicating a three-field record.

4. **One `MASTER_DATA` permission module, not twelve.** An operator building a role thinks
   "may they edit master data", not "may they edit districts but not cities". `PINCODE` and
   `ROUTE_MASTER` keep the rights they were seeded with in V6 and gained the
   activate/deactivate pair they were missing. Catalogue total: **174 → 187**.

5. **No domain events.** Branch and Wallet publish them because something listens. Nothing
   listens to a city being renamed, and a sealed interface with no consumer is
   infrastructure for its own sake.

6. **No separate summary DTO.** A master row is a dozen short fields; a narrower list
   projection would double the DTOs to save nothing and let the list and detail screens
   drift apart.

7. **Bootstrap is an endpoint, not automatic seeding.** Seeding during company provisioning
   would point `modules/company` at a module it knows nothing about, and would leave every
   company created before this release with empty lists anyway. One explicit, idempotent
   action serves both. It skips codes that already exist, so it can never resurrect a row
   an administrator deliberately removed. **The geography hierarchy is not seeded** — there
   is no set of countries and pincodes that is right for an arbitrary courier.

8. **`MasterUniquenessChecker` assembles SQL, and is the only thing that does.** The unique
   keys do not know about `deleted` and `@SQLRestriction` hides exactly those rows, so the
   check must be native — as `BranchRepository.isCodeTaken` already was. Twelve tables would
   have needed twenty-four near-identical native queries, so it is written once. Table names
   are checked against a closed set and columns against `^[a-z][a-z0-9_]*$`; every *value* is
   a bound parameter. The count is compared in Java because MySQL returns `COUNT(*)` as
   `BIGINT` — the defect from CHANGELOG 0.3.0.

9. **Parent names are resolved once per page, not per row.** `MasterNameResolver` collects a
   page's distinct parent ids and asks once, through the specification rather than
   `findAllById` — a load by primary key is not company-filtered. An id from another company
   is simply absent, so the response shows no name rather than leaking one.

---

## Frontend (UI-12)

`features/masters`, API-only, no mock. **Four components serve all twelve lists**, selected
by the `:master` route parameter:

- `master-list` — paged table, sort, debounced search, filter drawer, CSV export,
  permission-gated row actions, and a "Seed standard set" button on the five seeded
  catalogues.
- `master-form-page` — create and edit in one component (the difference is three lines:
  fetch first, PUT with the version, reload on 409).
- `master-view` — the detail cards, grouped exactly as the form groups them.
- `components/`: `MasterTable`, `MasterForm`, `MasterFieldControl`, `MasterFilter`.

`master.config.ts` is the single source of the differences: columns, field descriptors
(kind, validators mirroring the DTOs, hints, lookup source, group), filters and export
columns. Adding a backend field is a one-line data change.

Routes `masters/:master`, `masters/:master/new`, `masters/:master/:id`,
`masters/:master/:id/edit` — `new` declared before `:id` so the literal is not swallowed,
the same ordering the permissions module needed for `assign`. Reads for
`SUPER_ADMIN`/`COMPANY_ADMIN`/`BRANCH_MANAGER`/`HUB_MANAGER`, writes `COMPANY_ADMIN`.

The sidebar's aspirational Masters entries were replaced with the twelve real ones; the
dead `/masters/zone` link is gone. `UiInput` gained an optional `errorMessage` override so
a field can show the message its own pattern deserves instead of "Invalid value."

---

## Verification (2026-07-28, MySQL 8.0.46, `SERVER_PORT=8082`)

Flyway applied **V11**, `ddl-auto: validate` passed, all twelve tables created, 187 system
permissions in the catalogue (188 rows including one soft-deleted `SHIPMENT_APPROVE` left
by an earlier session).

Exercised end to end over HTTP as `asha@legacy.test` (COMPANY_ADMIN):

- bootstrap seeded 5 + 5 + 4 + 4 + 5 rows; a second run created 0 and skipped all 23
- create normalised `" in dia "` → `INDIA`, uppercased ISO and currency codes
- 409 on a duplicate code, 409 on a duplicate name differing only in case
- the whole hierarchy built country → state → district → city → area → pincode
- parent names resolved on list responses (`MH` → India, `411038` → Kothrud)
- 422 deleting a country with states ("India still has 1 state(s)")
- 422 on an unknown parent id, 400 on a non-digit pincode
- `serviceable=false` folded all three availability flags down
- 409 on a stale version
- weight slabs: 25–50 accepted, 3–7 refused naming `SLAB_1_5KG`, max ≤ min refused
- payment modes: both-ends refused, COD-without-delivery refused
- routes: created, duplicate pair refused, **reverse direction accepted**, same-branch refused
- 400 sorting by `passwordHash`, listing the allowed set
- search for a bare `%` matched 0 rows (LIKE wildcards escaped)
- 401 anonymous, 403 for a `SUPER_ADMIN` write, 200 for a `SUPER_ADMIN` read

Then through the Angular console (dev server proxied at `:4300`): signed in, all twelve
sidebar entries present, Countries / Routes / Weight Slabs lists rendered with resolved
branch names and the seeded catalogue, and a pincode was **created through the UI** —
picker, toggles, toast, redirect to the detail view with the area name resolved.

Two UI defects were found by running it and fixed: the availability toggles started off
(every UI-created pincode would have arrived unserviceable), and text fields printed two
error messages for one problem.

**Not verified:** the cross-company check. `RIVAL_CO` still has no active user — the same
gap the Branch Wallet module recorded.

---

## Route Management (2026-07-30 extension)

A later brief asked for a standalone "Route Management" module — its own `route_master`
table, `/routes` endpoints, `ROUTE_VIEW/CREATE/UPDATE/DELETE` permissions. That is the
same domain `Route` already covers here (booking/delivery branch pair, distance, transit
promise, one row per ordered pair), so building it as a second, parallel concept would
have left two Route tables in one schema. Asked directly, the user chose to **extend**
this `Route` instead of duplicating it — no new table, no new package, no new permission
codes.

**What was added, `V15__route_transit_hours.sql`:**

- **`transit_hours`** (`INT NOT NULL DEFAULT 0`, `[0, 23]`) — a lane's transit promise was
  whole days only; a same-day lane that actually takes six hours had nowhere to record
  that. 24 or more is refused — it belongs in `transitDays`.
- **`distance_unit`** (`VARCHAR(10) NOT NULL DEFAULT 'KM'`) — names the unit `distanceKm`
  always implied. New `DistanceUnit` enum, one constant (`KM`) today, same reasoning as
  `WeightUnit`: a future unit is a new constant, not a migration renaming a column out
  from under every existing row.

Both are additive with defaults — no existing row, query or permission changed shape.
`ROUTE_MASTER_VIEW/CREATE/UPDATE/DELETE/ACTIVATE/DEACTIVATE` (seeded `V6`, activate/
deactivate added here at `V11`) already cover writes and reads; nothing new to grant.

Verified live 2026-07-30 (`SERVER_PORT=8081`, MySQL 8.0.46): `V15` applied clean,
`ddl-auto: validate` passed, create/update/list/activate/deactivate all carry the two new
fields over HTTP, the existing branch-pair/direction/active-branch rules are unchanged,
and the Angular `New Route` form and detail view render both fields (`master.config.ts`'s
Transit column now shows `"1d 8h"` style). Full entry in `CHANGELOG.md` 0.13.1.

---

## Still open

- **Bulk import.** `MASTER_DATA_IMPORT` is in the catalogue because a pincode upload is
  obviously coming; the endpoint is not. A right is cheap to seed and expensive to rename
  after customers hold it.
- **Cross-company runtime check** — needs an active `RIVAL_CO` user.
- Rate Master will consume `weight-slabs`, `cities` (tier) and `pincodes` (zone); Shipment
  Booking will consume `service-types`, `package-types`, `payment-modes` and `routes`;
  Manifest Planning will consume `routes`. None of them exist yet.

---

# Global masters (2026-07-29, v0.12.0) — `V12`, not yet applied

## The split

| Tier | Lists | Writes | Reads | Path |
|---|---|---|---|---|
| **Global** | country, state, district, city, area, pincode | `SUPER_ADMIN` | anyone signed in | `/api/v1/global-masters/**` |
| Company-owned | vehicle type, package type, service type, payment mode, weight slab, route | `COMPANY_ADMIN` | any company user | `/api/v1/master/**` |

Per-company geography was defensible on paper — a company could name its cities the way its
own paperwork does — and wrong in practice. `PUNE` meant a different row in every company,
so no rate card, serviceability check or report could be compared across two of them, and
every new company started with an empty map of the country it operates in. Vehicle types
and payment modes genuinely *do* differ per company, and stay where they are.

## How a "global" row is still company-owned

The six entities remain `MasterDataEntity` subclasses, the tables keep their `tenant_id`
column, and every global row carries the reserved owner
`GlobalMasters.PLATFORM_COMPANY_ID` = `00000000-0000-0000-0000-000000000001`.

Three things fall out, and they are the reason for the design rather than a consequence of
it:

1. **`(tenant_id, code)` was already unique**, so one owner makes it a *global* unique on
   code with no schema change at all.
2. **The Hibernate filter stays on.** A code path that forgets to bind the platform id
   returns **nothing** — a visible bug — rather than **everything**, which is a leak.
3. **The constant is deliberately not a valid time-ordered UUID**, so it can never collide
   with a generated `companyId` and is recognisable on sight in a row nobody expected.

The alternative — a second entity hierarchy with no owner column — would have duplicated
the shared head, the `@NoRepositoryBean` repository, the criteria/specification pair and
the abstract service that let one implementation serve all twelve lists, because Java has
one superclass. That machinery is decision 42, and it is worth more than the column.

## What changed in the code

- `AbstractMasterDataService.global()` — a hook, false by default. It is a **method** so a
  subclass declares it next to the `@PreAuthorize` that goes with it: a list that answers
  true must also require `SUPER_ADMIN` to write, and having both in one file is what stops
  the two drifting.
- `AbstractMasterDataService.withOwner(...)` wraps create, update, activate, deactivate and
  delete. **Binding matters on the write path as much as the read** —
  `CompanyEntityListener` stamps the owner from `CompanyContext` on persist, so a global row
  created under a super admin's own binding would silently belong to their home company and
  be invisible to everyone else.
- `doGetById` / `doSearch` pin the platform id for a global list, so a booking clerk and a
  super admin see exactly the same rows.
- `MasterNameResolver.globalNamesById(...)` — resolving a geography parent against the
  caller's own company would find nothing and render a state with no country, which is the
  kind of blank nobody investigates. Two methods, so a caller has to say which kind of
  parent it holds.
- Nine `GLOBAL_MASTER_*` permission codes, excluded from `COMPANY_ADMIN` by
  `DefaultRoleCatalog`.

## What `V12` does to existing rows

Parents before children, so a child is repointed at its parent's survivor before that
parent's duplicates go:

1. **Dedupe by code.** The survivor is the oldest live row — `deleted ASC` first, so a
   soft-deleted row is never chosen over a live one carrying the same code. Losers are
   **deleted outright**, not soft deleted: they are duplicates being merged away, and a
   soft-deleted loser would keep its code reserved and defeat the whole exercise.
2. **Resolve name collisions.** Two codes for one name (`MH` and `MAHA`, both
   "Maharashtra") now collide on `uk_master_*_name`. The loser is **renamed**, not deleted
   — a duplicate *code* is unambiguously the same place recorded twice, but a duplicate
   *name* under two codes may be two different places someone named carelessly, and
   deleting one would take an operator's data with it. It becomes `Maharashtra (MAHA)` for
   a super admin to merge by hand.
3. **Re-own the survivors** to the platform constant.

The mapping tables are real tables (`_v12_map_*`), dropped at the end, not `TEMPORARY`
ones: MySQL cannot open a temporary table twice in one statement, and every repoint joins
its map.

## Frontend

`master.config.ts` gained a `global` flag and `writeAccessFor(def)`, which returns the
writer roles and permission codes for one list. The same four components still serve all
twelve; the tier is data, so a list that flips changes in one place. The geography leaves
moved out of the `Masters` menu into `Platform` — left where they were, a `COMPANY_ADMIN`
could have clicked them and been unable to save, which is a trap.

## Verification status

**Not run.** `V12` has never touched MySQL. The merge is the destructive part and is the
first thing to check: row counts before and after, and no dangling `state.country_id`.
