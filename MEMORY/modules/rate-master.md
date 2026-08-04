# Rate Master

New package `com.courier.modules.rate`, migration `V16`. Company rate cards: one row
prices one weight slab for one **Route + Service Type + Package Type + Payment Mode**
combination. Shipment Booking (not yet built) will call `POST /rates/calculate` to price
a shipment at booking time.

## Shape

`rate_master` (company-owned, like every operational record): `rate_code` (immutable,
unique per company, reserved past soft delete — the same treatment `customers.customer_code`
and every master code get), `rate_name`, `route_id` / `service_type_id` / `package_type_id`
/ `payment_mode_id` (plain UUID columns, **no physical FK** — a different module's tables,
the same cross-module treatment `customer_addresses`' geography ids and
`branches.manager_id` get), `minimum_weight`/`maximum_weight`/`weight_unit` (the slab,
half-open `[min, max)` exactly like `master.domain.WeightSlab`), `base_rate`,
`additional_weight`/`additional_weight_rate` (the overage increment and its price),
`minimum_charge`, four flat surcharges (`fuel_surcharge`/`handling_charge`/`oda_charge`/
`insurance_charge`), `gst_percentage` (`[0, 100]`), `effective_from`/`effective_to`
(`effective_to` nullable — open-ended), `status` (`ACTIVE`/`INACTIVE`).

**`rate.domain.WeightUnit`** is this module's own enum (`KG`/`GRAM`/`POUND`), not an
import of `master.domain.WeightUnit` — same reasoning that class's own javadoc gives:
the cross-feature rule forbids reaching into another feature's domain, and "the unit
this rate is priced in" is a different fact from "the unit a master weight slab is
measured in", even though the constants happen to match today.

## Business rules

1. **Only an active Route may carry an active Rate.** Checked whenever the rate *will
   be* ACTIVE: on create (a new rate always starts ACTIVE), on update (if the rate is
   currently active), and on `activate` (in case the route went inactive while the rate
   sat deactivated). `RateServiceImpl.requireActiveRoute` calls
   `RouteService.getById(routeId)` and reads `Route.isActive()` off the object that
   application-service seam legitimately hands back — not a reach into master's
   repository or entity internals, just reading a field off a value the sanctioned
   interface returns.
2. **No two ACTIVE rates for the same Route + Service Type + Package Type + Payment Mode
   may cover the same weight.** `Rate.overlapsWeightRange` mirrors
   `WeightSlab.overlaps` exactly (half-open, same-unit-only comparison).
   `RateServiceImpl.requireNoOverlap` runs on create, on update (while the rate stays
   active) and on `activate` — the same "deactivate, add an overlapping slab, reactivate"
   loophole `WeightSlabServiceImpl` already closes.
3. **`routeId`/`serviceTypeId`/`packageTypeId`/`paymentModeId` are validated for
   existence** against `com.courier.modules.master`'s own application service
   interfaces (`RouteService`/`ServiceTypeService`/`PackageTypeService`/
   `PaymentModeService`) — a forward cross-feature dependency, not a port, the same
   treatment `CustomerAddressServiceImpl` gives the global geography masters. Existence
   only for the latter three; Route additionally needs the active check above.
4. **No delete.** `RATE_MASTER_DELETE` stays a seeded-but-unused permission code — the
   same pattern `CUSTOMER_DELETE` and `MASTER_DATA_IMPORT` already follow. A rate is
   withdrawn by deactivating it; shipments already booked against it still have to be
   explainable.

## `RouteService.findByBranches` — a small addition to an already-shipped module

The Rate Calculation API is handed a booking branch and a destination branch, not a
route id. `RouteService` (master module) previously exposed only the generic
`MasterDataService<Route, RouteCommand>` seven verbs — no lookup by branch pair, even
though `RouteRepository.findByCompanyIdAndBookingBranchIdAndDeliveryBranchId` already
existed (used internally for the duplicate-pair uniqueness check). Added
`Route findByBranches(UUID bookingBranchId, UUID deliveryBranchId)` to the interface and
implemented it in `RouteServiceImpl` (same `READ = isAuthenticated()` tier as every other
read on that service), throwing `BusinessRuleException` when no route runs in that
direction. This is the smallest change that lets Rate Master consume Route Management
without Rate reaching into Master's repository — the seam decision 41 already
established, extended by one method rather than bypassed.

## Rate Calculation (`POST /rates/calculate`)

Read-only, `isAuthenticated()` — every branch role that books a shipment needs a quote,
not just whoever edits the rate card, which is why `RATE_MASTER_CALCULATE` (new
`PermissionAction`, see below) is granted far more broadly than `RATE_MASTER_CREATE`/
`UPDATE`/`DELETE`.

1. Resolve the route from `(bookingBranchId, deliveryBranchId)` via
   `RouteService.findByBranches`; refuse if inactive.
2. Confirm serviceType/packageType/paymentMode exist.
3. Load every ACTIVE rate for that exact combination, filtered to ones whose
   `effectiveFrom`/`effectiveTo` window covers the booking date (default: today).
4. Match the weight:
   - **Exact slab** (`rate.covers(actualWeight)`): freight = `baseRate`.
   - **Below every candidate's minimum**: floor to the lowest slab's `baseRate`,
     chargeable weight = that slab's minimum.
   - **At or above every candidate's maximum**: bill the highest slab's `baseRate` plus
     `ceil(overage / additionalWeight) * additionalWeightRate`; chargeable weight rounds
     up to the next whole increment.
   - **A weight that falls in a gap between two non-adjacent slabs**: refused —
     "there is a gap between the configured slabs", naming the actual weight. This is a
     real, distinct failure mode from "no rate at all": the combination exists, an
     administrator just hasn't covered every band.
5. `freight = max(computed freight, minimumCharge)`.
6. `subtotal = freight + fuelSurcharge + handlingCharge + odaCharge + insuranceCharge`;
   `gstAmount = subtotal * gstPercentage / 100`; `totalAmount = subtotal + gstAmount`.
   All money rounded to 2 decimals, `HALF_UP`.

**A bug only live HTTP testing caught, twice.** Two of the calculator's refusal messages
used `"literal %s text" + "more text".formatted(args)` — Java operator precedence binds
`.formatted(...)` to the *second* string literal only, so the interpolation silently did
nothing and the response read `"No active rate is effective on %s for this route..."`
with a literal `%s`. `mvn test` never caught it because the original assertions only
checked `hasMessageContaining("gap")` / `hasMessageContaining("No active rate is
effective")` — text that happened to sit in the *unformatted* half of the message. Found
by actually calling the endpoint with curl during verification, fixed by wrapping the
whole concatenation in parentheses before `.formatted(...)`, and the regression tests now
assert the actual interpolated value appears (`hasMessageContaining("3.000")`,
`hasMessageContaining("2026-06-01")`) and that no bare `%s` survives.

## Permissions

`RATE_MASTER` already existed as a `PermissionModule` (seeded `V6`: `CREATE`/`READ`/
`UPDATE`/`DELETE`/`SEARCH`/`IMPORT`/`EXPORT`/`APPROVE` — `APPROVE` remains seeded and
unused, no approval workflow is built). `V16` adds three rows:

- `RATE_MASTER_ACTIVATE` / `RATE_MASTER_DEACTIVATE` — the lifecycle pair every other
  master-shaped module already had and this one was missing.
- `RATE_MASTER_CALCULATE` — a **new `PermissionAction`** (pricing a shipment is
  read-only, but not the same right as reading the whole rate card, so a booking desk can
  hold one without the other; classified non-mutating alongside `READ`/`SEARCH`/`EXPORT`/
  `PRINT`/`DOWNLOAD`).

Catalogue moves 219 → 222. `COMPANY_ADMIN` needs no explicit grant (its set derives from
the whole catalogue). `FINANCE_USER` (already had `CREATE`/`READ`/`UPDATE`/`DELETE`) gets
the new three explicitly. `BRANCH_MANAGER` and `BOOKING_OPERATOR` (already had `READ`)
gain `CALCULATE` only — pricing happens at the counter, editing the rate card does not.

**`@PreAuthorize` tier mirrors `RouteServiceImpl`/`WeightSlabServiceImpl` exactly**,
*not* the permission catalogue above: `WRITE = hasRole(COMPANY_ADMIN)` for
create/update/activate/deactivate, `READ = isAuthenticated()` for read and calculate.
The permission-catalogue grants to `FINANCE_USER`/`BRANCH_MANAGER`/`BOOKING_OPERATOR` are
therefore another instance of "the responsibility list is ahead of the code" — real once
the authorise-on-permissions capstone ships, inert today.

## Frontend (`features/rate-master`)

Full module, API-only, no mock. Routes `rates`, `rates/new` (`COMPANY_ADMIN`),
`rates/:id`, `rates/:id/edit` (`COMPANY_ADMIN`), `rates/calculator` — `new` and
`calculator` declared before `:id`. Nav: a new top-level "Rate Master" group (order 2.7,
between Customers and Masters) with two leaves, "Rate Cards" and "Calculator", neither
roles-bridged (backend reads and calculates on `isAuthenticated()`).

- **`rate.service.ts`**: CRUD + activate/deactivate + `calculate` + `siblings(routeId,
  serviceTypeId, packageTypeId, paymentModeId)` (every rate sharing one combination, for
  the Weight Slab Grid).
- **`RateForm`**: reactive create/edit. `rateCode` create-only (immutable, shown
  read-only in edit). Two form-level validators mirror the server: `weightRangeValid`
  (max > min) and `effectiveRangeValid` (to >= from). As soon as all four combination
  pickers are filled, it calls `RateService.siblings(...)` and renders `WeightSlabGrid`
  inline — an admin sees the lane's existing slabs *before* saving, not only after a 422.
  Route/Service Type/Package Type/Payment Mode options are loaded once by the host page
  (`@features/masters/master-data.service` — `options('routes'|'service-types'|
  'package-types'|'payment-modes')`) and passed down, the same pattern `RateList`/
  `RateView` use to resolve id → name for display.
- **`WeightSlabGrid`**: presentational, sorted by minimum weight, flags any *active* row
  that overlaps another active row in the same list — a client-side mirror of
  `RateServiceImpl.requireNoOverlap`, computed purely from the rows it is handed (it does
  **not** simulate the in-progress unsaved row against the loaded siblings — only
  existing slabs are checked against each other).
- **`RateCalculatorForm`**: the one component behind both the "Calculate Rate" dialog
  (`RateCalculatorDialog`, launched from the rate list toolbar) and the full
  `rates/calculator` page — built once, not duplicated, since the spec asked for both a
  page and a dialog component. Loads its own branch/service-type/package-type/
  payment-mode options directly from `MasterDataService` (self-contained, since it drops
  into two different host shells).
- **Honesty note**: the spec's business rule "Booking date must fall within
  effectiveFrom/effectiveTo" has no explicit "booking date" field in the Rate Calculation
  API's input list (only Booking Branch, Destination Branch, Service Type, Package Type,
  Payment Mode, Actual Weight). Added an optional `bookingDate` to both the backend
  command and the frontend form, defaulting to today when blank — the rule is real and
  needed a field to check it against, and defaulting rather than requiring it keeps the
  documented input list working unmodified.

**16 new frontend tests** (`rate.service.spec.ts` — 5, `rate-form.spec.ts` — 7,
`weight-slab-grid.spec.ts` — 4). Suite moves 82 → 98. `ng build` clean, `ng test` 98/98.

## Verified by running it (2026-07-30, MySQL 8.0.46, second instance `SERVER_PORT=8082`,
second Angular dev server `--port 4300`, both against the shared dev database, the
user's own 8081/4200 instances untouched)

A migration bug surfaced immediately on first boot: `V16`'s role-permission backfill
joined `company_roles r ON grant_map.role_code = r.code` — the column is `role_code`, not
`code` (confirmed against `V4__company.sql`). Fixed, and the partial DDL from the failed
attempt (the `rate_master` table itself, created by MySQL's implicit-commit-on-DDL before
the failing statement) was dropped and the failed `flyway_schema_history` row deleted
before retrying — a clean re-run, not a patched-over one.

Over HTTP as `asha@legacy.test` (`COMPANY_ADMIN`, `LEGACY_CO`, after `POST
/master/bootstrap` seeded service types/package types/payment modes for the company):
create (both an exact-slab and an adjacent-slab rate), overlapping slab refused (422,
naming both rates and the range), duplicate rate code refused (409), an unknown service
type refused (422), a rate against a deactivated route refused (422, route reactivated
after), list/get, activate/deactivate idempotent round-trip, and `POST /rates/calculate`
for an exact match, an overage match (12 kg against a `[5,10)` slab: `180 + ceil((12-10)/
0.5)*25 = 180 + 100 = 280`, matching the unit test), and a gap between two deliberately
non-adjacent slabs (`[0,2)` and `[5,10)`, weight `3` — refused, "gap between the
configured slabs"). RBAC: `SUPER_ADMIN` refused creating a company's rate (403,
same "platform never touches a company's operational records" invariant every other
module asserts), anonymous refused (401). Backend suite **573/573** (was 544).

Then through the Angular console: Rate Master nav group (Rate Cards / Calculator) renders
for `COMPANY_ADMIN`; the list resolves route/service-type/package-type/payment-mode
names via the master pickers; the view page's "Other Weight Slabs" card highlights the
current rate and shows its one sibling with no false overlap flag; the Rate Calculator
page quoted 2.5 kg against `RATE-PNQ-BOM-STD` as freight 100.00 + fuel 10.00 + handling
5.00 = 115.00, GST 18% = 20.70, **total 135.70**, matching the earlier curl call
verbatim; the "Calculate Rate" dialog opens and functions identically (same dark
`MatDialogModule` chrome the already-shipped `AddressFormDialog` uses — confirmed
pre-existing app-wide, not a regression); and the New Rate form's live overlap toast
fired correctly when a deliberately-overlapping slab (`[2,6)` against the existing
`[5,10)` `GAP-HIGH`) was submitted, then succeeded once corrected to `[2,5)`.

**Not exercised:** a `BRANCH_MANAGER`/`BOOKING_OPERATOR`-scoped token calling
`/rates/calculate` (no such user exists in the dev fixtures yet — the same long-standing
gap every module's verification note already records, alongside the still-missing
`RIVAL_CO` cross-company check).
