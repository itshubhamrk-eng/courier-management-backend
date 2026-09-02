# Module — Master Data (Phase 6)

**Status:** COMPLETE and verified by running it (2026-07-28). **Amended 2026-07-29
(v0.12.0): the six geography lists are now global** — one catalogue shared by every
company, `SUPER_ADMIN`-written, served from `/api/v1/global-masters/**`. `V12` **is now
applied** to the real dev DB (confirmed live 2026-09-02, schema at v51 by then — this
memory's own "not yet applied"/"Verification status: Not run" notes further down were
stale by that point; corrected there too). See *Global masters* at the end of this file,
and *Pincode postal auto-fetch + ODA (2026-09-02)* for the latest addition.
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

- ~~**Bulk import.**~~ Built 2026-09-02 — `POST /global-masters/pincodes/bulk-import`, see
  *Pincode bulk-import* below. Range-probe only (no file upload); a synchronous endpoint, no
  async/resumable job status yet — fine for a sample, not yet exercised at full-state scale.
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

**Applied.** Confirmed live 2026-09-02 while verifying the Pincode postal-lookup feature
below: `courier_db` schema was already at v51 (past `V12`) with real global geography rows
present (country/state/district/city/area counts all non-trivial) and pincode create/lookup
against `/api/v1/global-masters/pincodes` working end to end. Which session actually applied
`V12` and ran the merge is not recorded here — this note only corrects the stale "not yet
applied" claim above, not the full history.

---

## Pincode postal auto-fetch + ODA (2026-09-02 extension)

Direct request, inside the existing Pincode Master create/edit flow (no new screen): auto-fetch
the Area once a pincode is typed, plus an "ODA applicable" (Out-of-Delivery-Area) toggle.
Scoped via `AskUserQuestion` first — real India Post directory over local-only matching, and
auto-creating the missing geography chain over leaving Area blank with a hint.

**ODA**: `master_pincodes.oda_applicable` (`V51`, `NOT NULL DEFAULT FALSE`) — independent of
`serviceable`/`codAvailable`/`prepaidAvailable`/`pickupAvailable`; does not fold or get folded
by them. Threaded through the command/DTOs/mapper/entity the same way every other pincode flag
already was.

**Postal lookup**: new `application.port.PincodePostalLookupProvider` (`List<PostOffice>
lookup(String pincode)`), real implementation `infrastructure.IndiaPostPincodeLookupProvider`
calling `api.postalpincode.in` (free, no key) via `RestClient`, `DisabledPincodePostalLookupProvider`
fallback behind `app.master.pincode-lookup.enabled` (default **true** — the one provider flag
in this codebase that defaults on, since there's no vendor credential to be missing). New
`application.GeographyAutoResolver` resolves the matched post office to a real Area,
find-or-creating State/District/City/Area by name within parent — **deliberately calling the
repositories directly, not `CountryService`/`StateService`/etc**, because those are
`SUPER_ADMIN`-only writers (`WRITE = hasRole(SUPER_ADMIN)` on every geography service) and this
must work for the `COMPANY_ADMIN` caller `PincodeServiceImpl.create` already allows to write
Pincode itself — the resolver is reached only from `PincodeService.lookupPostalArea`, which
carries `PincodeServiceImpl`'s own `WRITE` gate, so the bypass is scoped, not a hole. Codes for
auto-created rows are derived from the matched name (`MasterDataEntity.normaliseCode`),
de-duplicated against `(company_id, code)` with a numeric-suffix retry loop — the same shape a
human hitting a collision would produce by hand. New endpoint: `GET
/api/v1/global-masters/pincodes/lookup/{code}` (`PincodeController.lookupArea`), same write
audience as `create` — a match can create master rows, so this is not read-only despite the verb.

**A real bug found and fixed via live verification**: the JDK `HttpClient`'s default
`User-Agent` (`Java-http-client/…`) gets silently connection-reset by `api.postalpincode.in`'s
own front end — reproduced with `curl` (identical request succeeds with any other User-Agent,
even blank) before concluding this wasn't the deployment's own outbound access being blocked.
An HTTP/1.1 pin was tried first (based on the first symptom, "RST_STREAM: Stream cancelled")
and ruled out by further `curl` reproduction (HTTP/2 + a normal UA succeeds fine) — reverted in
favor of the simpler, actually-correct fix: one explicit `User-Agent` header on the request.

**Frontend**: `MasterForm` debounces the Pincode field's `valueChanges` (500ms, create only,
keyed off `def().key === 'pincodes'` rather than a new generic field-descriptor flag — this
behavior is specific to one list, not a shape every master could reuse), calls the lookup
endpoint, auto-selects the resolved Area (injecting `{value, label}` into the cached picker
options if the Area was just created and isn't in the already-fetched active list yet), and
shows a "Matched to Area, City, District, State (1 of N post offices sharing this pincode)."
hint under the Pincode field. `not-found`/`error` states fall back to the pre-existing manual
Area picker — never blocks the form. `master.config.ts` gained one more `boolean` field +
table column for `odaApplicable`, no changes to the four shared master components.

**Verified live** on throwaway `:8082`/`:4200` (`:8100`/`:4200` **is** the real pair, but
neither was running at the time — confirmed free via `lsof` before use, both throwaway
processes stopped after) against real `courier_db`, `V51` applied cleanly: real pincode
`411001` resolved via the actual India Post API to `C D A (O), Pune City East, Pune,
Maharashtra`, auto-creating that State/District/City/Area chain (confirmed in MySQL directly);
a second lookup of the same pincode returned the identical `areaId` (idempotent, no duplicate
rows created); a `BRANCH_MANAGER` token correctly 403'd on the lookup endpoint (`WRITE`-gated
like `create`); a real pincode created through the actual running UI end to end — typed
`411001`, watched the "Matched to…" hint and Area auto-select live, toggled ODA on, saved, and
the detail view showed `ODA APPLICABLE: Yes`. `mvn test` green throughout (no new unit tests —
this module's existing precedent for a provider/resolver pair like this is verify-live, e.g.
`commissionSummary`/`summaryStats`), `tsc --noEmit`/`ng build --configuration production` both
clean, the existing masters `ng test` suite (50 tests) unaffected. Full detail in
`CHANGELOG.md` Unreleased 2026-09-02.

---

## Pincode bulk-import (2026-09-02, same-day follow-up)

Direct request: "add all maharashtra all pincode with all area." Scoped via
`AskUserQuestion` first — the honest scale check mattered here: full Maharashtra is roughly
45,000 candidate 6-digit codes (India Post's Maharashtra circle spans 400001-445402) for
around 7,000 real ones, and there is no "list every pincode in a state" endpoint anywhere in
the postal directory — only per-pincode lookup, the same one the create form's auto-fetch
already calls. Brute-forcing the full range would be hours of sequential HTTP calls against a
free public API with no documented rate limit, and would permanently seed ~7,000 rows into the
shared dev DB in one sitting. User chose: a representative sample now (major-city blocks, not
the full range), and a real reusable backend endpoint over a one-off script — which also
happens to be the endpoint `MASTER_DATA_IMPORT` has sat in the permission catalogue for with
nothing behind it since Master Data first shipped (see *Still open* above).

**New `POST /api/v1/global-masters/pincodes/bulk-import`**
(`BulkImportPincodesRequest{ranges: [{fromCode, toCode}]}`, plain numeric-range pairs, same
digit width each), same `WRITE` audience as `create`. New `PincodeBulkImportService`
(`application` package, no controller-facing DTO logic of its own) loops each range and, per
candidate code, calls `PincodePostalLookupProvider.lookup` then `GeographyAutoResolver
.resolveArea` — the exact pipeline the single-pincode lookup endpoint already exercises — and
on a match calls `PincodeService.create`.

**Why `create` is called rather than duplicating row-construction**: it is the real,
Spring-proxied bean (`PincodeBulkImportService` holds it as a normal `@RequiredArgsConstructor`
dependency, a genuine cross-bean call, not `this.create(...)` self-invocation) — so every rule
`create` already enforces (`applyInvariants`, the area-must-be-active check, the audit trail)
applies to a bulk-imported row exactly as it does to one typed by hand, and — the part that
actually matters at this scale — `create`'s own `@Transactional` opens and closes a fresh
transaction on *each* call. Looping inside one `@Transactional` method across a range spanning
an hour or more would hold database locks for the run's entire duration; calling out to a
separately-transactional bean per row is what avoids that, with zero new transaction-boundary
code to get wrong.

**Idempotent by construction, not by a pre-check.** A candidate already on file is *not*
looked up with a separate existence query first — it is inferred from `create`'s own
`DuplicateResourceException`, thrown by the exact same `(company_id, code)` uniqueness check
every other Pincode create already goes through. That is what makes the endpoint safe to
re-run over an overlapping or identical range (the resumability an hours-long full-Maharashtra
run would eventually need) without a second code path to keep in sync with the first.

**`PincodeBulkImportService` also carries its own `@PreAuthorize(WRITE)`**, checked before any
network call — not left to `create`'s own check to catch late. Without it, an under-privileged
caller could still trigger thousands of postal-directory lookups (the network-costing part)
even though every resulting `create` call would ultimately 403; the outer gate stops that at
the door.

**Verified live** on the same throwaway `:8082`/`:4200` pair as the parent feature (real
backend/frontend confirmed not running before use via `lsof`, both stopped after) against real
`courier_db`: seven ranges covering Mumbai (400001-400050), Pune (411001-411050), Nagpur
(440001-440035), Nashik (422001-422015), Aurangabad (431001-431010), Kolhapur (416001-416010),
Solapur (413001-413010) — 180 candidates, 152 created (real India Post locality names —
Bazargate, Kalbadevi, B.P. Lane, Malabar Hill, Ambewadi, Asvini, Bharat Nagar, Falkland Road,
Chinchbunder, Dockyard Road, and 142 more — 157 distinct Areas auto-created across the run), 2
already existed (from the parent feature's own live-verification rows), 26 no postal record,
0 failed, ~77 seconds. Re-running two of the same ranges (100 candidates) afterward: 0 created,
85 correctly skipped as already-existing, 0 duplicate rows — confirmed directly in MySQL, not
just from the response tally. A `BRANCH_MANAGER` token correctly 403'd on the endpoint. The
actual Pincodes list page (not just the API) rendered all 158 resulting rows with resolved
Post Office/Area/Serviceable/COD/ODA columns. `mvn test` green throughout — no new unit tests,
same verify-live precedent the parent feature and `commissionSummary`/`summaryStats` already
set for this class of provider/orchestrator code. Full detail in `CHANGELOG.md` Unreleased
2026-09-02.

**Still open**: the full Maharashtra range (or any other state) is not yet run — this session
deliberately stopped at a representative sample per the user's own scope choice. A true
full-state run would want an async/background variant (the current endpoint is synchronous,
blocking the HTTP request for as long as the range takes) and a resumable job-status mechanism,
neither built here since they weren't asked for.

---

## Pincode-Area links, per-area ODA (2026-09-02, same-day follow-up)

Direct request following the bulk-import: "some pincode have multiple city or area name."
The gap this closes: `master_pincodes.area_id` names exactly one Area, but India Post's own
directory routinely lists several real post offices for one pincode (the "1 of N post
offices sharing this pincode" hint the create form already showed, transiently, and
discarded) — and 0.32.0's `oda_applicable` was a single flag per pincode, when whether a
locality is genuinely Out-of-Delivery-Area varies per post office, not per 6-digit code.
Scoped via `AskUserQuestion` first (a read-only alternates list vs. a real schema change);
the user asked specifically for a new table, an area list on the view page, and a per-area
ODA toggle with an amount defaulting 250.

**New `master_pincode_areas` (`V52`)** — company-owned/global exactly like `master_pincodes`/
`master_areas` (a link row is as global as the two rows it connects): `pincode_id`,
`area_id`, `is_primary` (kept in sync by the application layer, not derived), `oda_applicable`,
`oda_amount` (`DECIMAL(10,2)`). `PincodeArea.applyInvariants()` folds `oda_amount` to `null`
when `oda_applicable` is false, and fills a fresh `DEFAULT_ODA_AMOUNT` (`250.00`) the moment
it turns true with no amount already given — the same "toggle first, amount defaults" flow
the request asked for. **Additive, not a replacement**: `master_pincodes.area_id`/
`oda_applicable` are completely unchanged — they still drive the create form's single Area
picker and the list table's ODA column; this new table is the detailed per-area layer
sitting alongside them, reachable from the pincode detail page.

**`PincodeAreaService`** — `list`/`updateOda` are controller-facing (`READ`/`WRITE`, same
audience as every other Pincode operation); `syncAreas(Pincode)` is internal, called from
`PincodeServiceImpl.create`/`update` right after the pincode itself saves. It never throws:
the primary row (`pincode.areaId`, a plain insert, no network) is wrapped in its own
try/catch, and the alternates discovery (re-probing `PincodePostalLookupProvider.lookup`,
then `GeographyAutoResolver.resolveArea` per match, same pipeline the single-pincode
auto-fetch and bulk-import already use) is wrapped in a separate one — a failed or slow
postal-directory call degrades to "no alternates linked yet," never a failed pincode save.
Runs inside the same transaction as the pincode write (a cross-bean call with no `@Transactional`
of its own, so it joins whatever transaction `create`/`update` already opened) — deliberate
for the primary row's atomicity; the accepted cost is that every `create()` call, including
each row of a bulk-import run, now does its own postal-directory lookup for alternates on
top of whatever lookup the caller already did, roughly doubling bulk-import's network calls.
Not optimized away (e.g. by threading pre-fetched matches through `PincodeCommand`) since it
wasn't a demonstrated problem at the scale actually run.

**Why alternates aren't resolved through `CountryService`/etc, again**: same reasoning
`GeographyAutoResolver` itself already documents — those are `SUPER_ADMIN`-only writers, and
this needs to work for the `COMPANY_ADMIN` caller `PincodeServiceImpl.create` already allows.

**New `GET/PATCH /api/v1/global-masters/pincodes/{id}/areas[/{areaLinkId}]`** on
`PincodeController`. The PATCH body (`UpdatePincodeAreaRequest`) has both fields optional and
independent — flip the toggle without touching the amount, or vice versa.

**Frontend**: new `PincodeAreasCard` (`features/masters/components/`), deliberately **not**
part of the shared four-component master architecture — a per-row editable sub-list (name +
city + primary badge + ODA toggle + conditional amount input) isn't a flat field descriptor
`MasterFieldControl` can render generically, the same reasoning that already keeps the
Pincode auto-fetch hint a bespoke bit of `MasterForm` rather than a config-driven field.
`master-view.ts` mounts it only when `def().key === 'pincodes'`, passing the record's own
`id` and the caller's write access. Each toggle/amount edit PATCHes immediately, no separate
save step — matches the module's existing activate/deactivate pattern (immediate action, not
a form submit).

**Verified live** on real `:4200`/`:8100` this time (not a throwaway pair) — backend rebuilt
and restarted for `V52` (schema 51 → 52 applied cleanly), which rotated `JWT_SECRET` again
and invalidated the existing browser session, same gotcha `[[local-dev-environment]]` now
documents; re-logged in fresh. Real pincode `416013` (Kolhapur, 3 post offices upstream per
India Post) created via the API — `GET .../areas` returned Girgaon (`primary: true`) plus
Pachgaon and R K Nagar auto-linked from the same `syncAreas` call that created the pincode
itself, no separate step. PATCH sequence confirmed via curl before touching the UI: toggle
Pachgaon's ODA on with no amount → `250.00`; set a custom `400` → accepted; toggle off →
`odaAmount` cleared to `null`. `BRANCH_MANAGER` correctly read the list (200) but 403'd on
the PATCH. Then the actual running UI: pincode detail page's new "Areas served by this
pincode" card rendered all three rows, clicking Pachgaon's toggle saved via a real PATCH and
revealed the amount input pre-filled `250` — screenshotted mid-interaction, not just
API-checked. `mvn test` green throughout, `tsc --noEmit`/the existing masters `ng test` suite
(50 tests, unaffected)/`ng build --configuration production` all clean — no new unit tests,
same verify-live precedent every provider/orchestrator addition this session has followed.
Full detail in `CHANGELOG.md` Unreleased 2026-09-02.

**Still open**: no manual add/remove of an Area from a pincode's list (only what
`syncAreas` discovers automatically) — not asked for. No backfill for the 158 pincodes
created before this feature existed (only new creates/updates populate the table going
forward) — also not asked for; those rows simply show an empty "Areas served" list until
next edited or re-synced some other way.

---

## Create-form polish: full preview, auto-fill, Placement/zone dropped, code sort (2026-09-02)

Same-day follow-ups after live-testing the two features above. `PincodeServiceImpl
.lookupPostalArea` resolves every postal match now, not just the primary — reuses the exact
same `GeographyAutoResolver` call `syncAreas` makes at save time, just run once earlier, so
the create form can show the *same* "Areas served" list the detail page shows after saving,
before saving. `areaId` stays a real, required `FormControl` (backend still needs it, and
`Validators.required` is what blocks Create when no postal match exists) but is excluded
from `MasterForm`'s/`MasterView`'s rendered field groups for pincodes specifically — the
user's own choice (`AskUserQuestion`) was to drop manual Area picking from the UI entirely,
not merely hide the card, accepting that a no-match pincode can no longer be created by hand.
`zone` (Delivery zone) removed from the field list outright, everywhere that array feeds.
`MasterDefinition.defaultSort` is new, generic, but only pincodes use it so far.

**Bug found only by retyping the pincode fast, twice, on purpose**: the first post-office
auto-fill wrote `if (!nameControl.value) { setValue(...) }` — reads clean, breaks the moment
a second lookup lands, because the first auto-fill already made the field non-empty forever.
`pristine` (not dirtied by programmatic `setValue`, only by a real keystroke via the forms
directive) is the correct signal and was verified to survive the same retype that broke the
naive version.

Full verification detail in `CHANGELOG.md` Unreleased 2026-09-02.
