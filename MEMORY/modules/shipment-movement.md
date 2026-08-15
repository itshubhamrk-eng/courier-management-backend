# Shipment Movement

**v0.17.7 update (read this first — Out For Delivery UX rework):** on direct user
request, `OutForDelivery` now picks the Delivery User *first* (nothing else renders
until that select has a value), then shows a checkbox `<table>` of this branch's
IN_SCAN shipments — the old multi-select-shipments `app-select` is gone. Selection is a
`Set<string>` signal, not a form array. Submit button renamed "Bulk Assign" → "Generate
DRS". v0.17.5's `printDrs()` is untouched underneath — only the selection UI feeding
`assign()` changed. Full detail in `CHANGELOG.md` 0.17.7.

**v0.17.5 update (read this first — Print DRS on Out For Delivery):** on direct user
request ("Allocate order to delivery boy and allocated order list should be print"),
`OutForDelivery` gained a **Print DRS** action, same client-side pattern as THC's own
Print (`window.open` + `document.write` + `window.print()`, no PDF service, no new
endpoint). The allocation itself was already there — `assignOutForDelivery` — this only
adds a printable Delivery Run Sheet once a bulk assign succeeds, for the shipments that
actually got assigned (not the whole selection — a partial-failure batch only prints
the successes, matched back by `shipmentNumber` since that's what `MovementOutcome
.reference` carries here, not the tracking number the UI column label might suggest).
Full detail in `CHANGELOG.md` 0.17.5.

**v0.17.4 update (read this first — Dispatch renamed too, plus two new features):** on
direct user request, "Dispatch" → "Trip Hire Challan (THC)" — `dispatch.ts` →
`trip-hire-challan.ts`, route `/movement/dispatch` → `/movement/trip-hire-challan`, same
mechanical treatment 0.17.3 gave Out Scan → Loading Sheet (`DISPATCHED` status and
`dispatch()` internals untouched, only the page label). Two real additions in the same
pass: **Print THC** (`window.open` + `document.write` + `window.print()`, no PDF
service) once a manifest is dispatched; and **remove a shipment from a still-CREATED
manifest** on the Loading Sheet page — new `ShipmentStatus` edge `MANIFEST_CREATED` ->
`BOOKED`, `ShipmentService.detachFromManifest`, `ManifestService.removeShipment`,
`DELETE /api/v1/manifests/{id}/shipments/{shipmentId}`. `ManifestCard` owns the removal
mutation itself (`showRemoveAction` input, `removed` output), wired true only from
Loading Sheet. Verified live end-to-end. Full detail in `CHANGELOG.md` 0.17.4.

**v0.17.3 update (read this first too — corrects the v0.17.1 note below):** on direct
user request, the page itself was renamed — it did *not* keep its name after all.
`out-scan.ts` → `loading-sheet.ts`, component `OutScan` → `LoadingSheet`, route
`/movement/out-scan` → `/movement/loading-sheet`, nav title/breadcrumb/tour all now say
**"Loading Sheet"**, and the `MANIFEST_CREATED` display label is now **"Loading Sheet
Created"** (was "Out Scan Created") everywhere — status badge, timeline, backend
`TIMELINE_LABELS`. No behavior change, no DB/enum change — `OUT_SCAN` the status value
was already gone (v0.17.1); this only renamed the surviving UI/label text. Full detail
in `CHANGELOG.md` 0.17.3. Everywhere below this point that says "Out Scan" is
describing the page/label as it was named through v0.17.2 — mentally substitute
"Loading Sheet" for current-state UI text; the status/behavior narrative is unaffected.

**Status:** DONE (v0.17.0, 2026-08-03; updated v0.17.1, same day). New package
`com.courier.modules.manifest` (the minimal Manifest prerequisite, built in this same
pass — see "Manifest didn't exist" below), migrations `V19`+`V20`, extends
`com.courier.modules.shipment`. Verified live over HTTP and through the Angular console.

**v0.17.1 update (read this first — supersedes the OUT_SCAN details below):** on
direct user request, `OUT_SCAN` is no longer a separate `ShipmentStatus` — creating a
manifest (`MANIFEST_CREATED`) already counts as "out scan created", one milestone not
two. There are now **four** movement steps, not five: Dispatch → In Scan → Out For
Delivery → Deliver. `POST /shipment-movement/out-scan` is gone; Dispatch's
precondition reads `MANIFEST_CREATED` shipments directly. The timeline is 6 steps, and
`MANIFEST_CREATED` displays as **"Out Scan Created"** everywhere (status badge +
timeline label) rather than as its own distinct scanned state. `ManifestScanCard`
(frontend) is now `ManifestCard` — a read-only heading + LR table, no scan controls;
the Out Scan *page* kept its name and its Create Manifest form, but is a worklist now,
not an action screen. `V20` folded the real `OUT_SCAN` rows this module's own 0.17.0
live verification had produced back into `MANIFEST_CREATED`. Full detail in
`CHANGELOG.md` 0.17.1. Everything below this point describes the **original, 0.17.0**
shape (Out Scan as its own state/action) — read it for the parts that didn't change
(Manifest/Vehicle/DeliveryAssignment shape, permission reuse, the one-directional
module dependency, Dispatch/In Scan/Out For Delivery/Deliver business rules), but
mentally substitute "Out Scan" → "creating the manifest" wherever it describes a scan
action, and drop `OUT_SCAN` from the status graph.

## Manifest didn't exist — built the minimal version underneath this module

The brief assumed a Manifest already existed: "Booking Branch → Create Manifest →
Assign Vehicle → OUT_SCAN → DISPATCH → Delivery Branch → IN_SCAN/RECEIVE →
OUT_FOR_DELIVERY → DELIVERED", and its own frontend spec's Dispatch page says "Select
Manifest" as if one were already sitting there to pick. Nothing in this codebase built
one — `MEMORY/modules/shipment-booking.md` says explicitly "do not start Manifest
Management next", the backlog's next item was Hub/Serviceability, and there is no
`MEMORY/modules/manifest.md`, no `com.courier.modules.manifest` package, no
`manifests` table anywhere before this pass. Confirmed with the user directly before
writing any code: build just enough Manifest to make Shipment Movement real, rather
than stopping or faking a bare `manifest_id` column with nothing behind it.

What "minimal" means concretely: a `Manifest` (number, booking/delivery branch,
vehicle, driver, status) you can create with a batch of `BOOKED` shipments and later
dispatch, plus a `Vehicle` fleet table just detailed enough to populate Dispatch's
picker (registration number, optional `master.domain.VehicleType`, capacity). No
standalone Manifest CRUD frontend, no vehicle lifecycle screens beyond create/list —
those weren't asked for and the brief's own Frontend section names exactly six pages,
none of them "Manage Manifests" or "Manage Vehicles".

**Driver is not a new entity.** `DefaultPermissionCatalog` already seeds a `DRIVER`
permission module (`DRIVER_CREATE/READ/UPDATE/DELETE/ASSIGN/...`) and `auth.Role` has
carried a `DRIVER` JWT role since early in the project, but neither is backed by a
company-role or a distinct "driver" table — `DefaultRoleCatalog`'s eight company roles
don't include one. Rather than build a fourth new entity in an already-large pass, a
manifest's `driverUserId` is simply any real user of the company (validated via
`company.application.UserService.getById`, no role restriction enforced). Documented
honestly, not swept under a TODO: a company today could hand `driverUserId` to a
`COMPANY_ADMIN`'s own id and nothing would refuse it.

## Permissions — reused the existing catalogue instead of adding six new codes

The brief listed six new permission codes (`SHIPMENT_OUT_SCAN`, `SHIPMENT_DISPATCH`,
`SHIPMENT_IN_SCAN`, `SHIPMENT_OUT_FOR_DELIVERY`, `SHIPMENT_DELIVER`,
`SHIPMENT_TIMELINE_VIEW`). Reading `DefaultPermissionCatalog` before writing a
migration for them found it had *already* seeded exactly this shape, ahead of any
service behind it — the same "responsibility list is ahead of the code" pattern this
project has hit on every prior module:

| Brief's code | Reused instead | Why |
|---|---|---|
| `SHIPMENT_OUT_SCAN` | `TRACKING_CREATE` | Catalogue comment: "Scans are appended, never edited" |
| `SHIPMENT_DISPATCH` | `MANIFEST_DISPATCH` | Catalogue comment: "DISPATCH... the vehicle going onto the manifest" |
| `SHIPMENT_IN_SCAN` | `MANIFEST_RECEIVE` | Catalogue comment: "take an inbound manifest in and account for what arrived" — exact match |
| `SHIPMENT_OUT_FOR_DELIVERY` | `DELIVERY_DISPATCH` | Catalogue comment: "DISPATCH is 'out for delivery' — the run leaves the branch" |
| `SHIPMENT_DELIVER` | `DELIVERY_DELIVER` | Direct match |
| `SHIPMENT_TIMELINE_VIEW` | `SHIPMENT_READ` | No dedicated timeline action exists; read tier already covers it |

Zero permission migration needed. RBAC is still role-based everywhere in this project
ahead of the "authorise on permissions" capstone (`hasAnyRole`, not permission codes),
so this reuse changes no runtime behaviour either way — it only keeps the catalogue
from growing two competing vocabularies for the same five actions.

## ShipmentStatus — renamed, not just extended

V19 renamed three existing enum constants and added one new one:

| Was | Now |
|---|---|
| `MANIFESTED` | `MANIFEST_CREATED` |
| `RECEIVED` | `IN_SCAN` |
| `RETURN_INITIATED` | *(removed — direct edge to `RETURNED`)* |
| *(new)* | `OUT_SCAN` |

Safe as a bare rename because nothing had ever written the old values — Shipment
Booking's own verification notes "nothing yet transitions a shipment past BOOKED".
V19 still carries two defensive `UPDATE`s in case a database somehow does have one of
the old values, but they're a no-op on this project's dev database. `CANCELLABLE` grew
to include `OUT_SCAN`: a shipment scanned onto a manifest that hasn't dispatched yet
is still physically at the booking branch.

```
BOOKED ──> MANIFEST_CREATED ──> OUT_SCAN ──> DISPATCHED ──> IN_SCAN ──┬──> OUT_FOR_DELIVERY ──> DELIVERED
   │              │                  │                                └──> RETURNED
   └──────────────┴──────────────────┴──> CANCELLED (BOOKED/MANIFEST_CREATED/OUT_SCAN only)
```
`READY_FOR_MANIFEST` stays declared, unwritten by this module (a pre-manifest "ready"
queue is a plausible future step, not this one — BOOKED → MANIFEST_CREATED is a direct
edge too).

## Shape

```
Manifest (com.courier.modules.manifest, CompanyOwnedEntity)
├── manifestNumber   MFT-yyMMdd-XXXX, unique per company, immutable
├── bookingBranchId, deliveryBranchId   no physical FK — cross-module ids, validated
│                                        against the shipments attached to it, not
│                                        independently against Branch
├── vehicleId, driverUserId   null until dispatch
├── status   CREATED | DISPATCHED | COMPLETED (COMPLETED is declared, never written —
│            no "close the manifest" step in this module's own scope)
└── dispatchedAt, completedAt, remarks

Vehicle (com.courier.modules.manifest) — grew from a minimal fleet-picker record into a
full fleet entity in 0.25.0 (2026-08-14); see that CHANGELOG entry for the full story.
0.25.1 (same day) added a management UI — `features/manifest/vehicle-list.ts`, nav leaf
under Masters (`COMPANY_AND_BRANCH`, an exception to Masters' usual COMPANY_ADMIN-only
gate — see `nav-scoping-2026-07-31.md`). 0.25.2 (2026-08-15, direct feedback the dialog
form "is not proper") replaced the create/edit dialog with routed pages —
`components/vehicle-form.ts` + `vehicle-create.ts`/`vehicle-edit.ts`, mirroring
`branch-form.ts`/`branch-create.ts`/`branch-edit.ts`'s own shape.
├── vehicleNumber   unique per company, upper-cased on save
├── vehicleType   enum BIKE|SCOOTER|AUTO|VAN|PICKUP|TRUCK|TEMPO|OTHER — NOT the same
│                  thing as master.domain.VehicleType (a separate, company-editable
│                  catalogue table Rate Master uses); the two are unrelated, no FK
├── make, model, fuelType (PETROL|DIESEL|CNG|EV|OTHER)
├── capacityKg, currentOdometer
├── purchaseDate, registrationDate, insuranceExpiry, pucExpiry, fitnessExpiry,
│   permitExpiry — statutory dates, no expiry-alert job reads them (not built)
├── status   AVAILABLE | IN_USE | MAINTENANCE | INACTIVE — operational state, replaces
│            the old ACTIVE|INACTIVE dichotomy
├── branchId   base branch, no physical FK
├── active   boolean, separate from status — the enable/disable toggle every other
│            module's activate/deactivate uses; isActive() reads THIS, not status, so
│            Dispatch's "vehicle must be active" check (ManifestServiceImpl.dispatch)
│            needed no code change when status grew from 2 to 4 values
└── remarks

DeliveryAssignment (com.courier.modules.shipment — new table, current-state not ledger)
├── shipmentId   unique per company — one live assignment per shipment, re-assign updates in place
├── deliveryBranchId, deliveryUserId, assignedAt, status (ASSIGNED | DELIVERED)
└── deliveredAt, receiverName, deliveryRemarks, otp
    — the proof-of-delivery capture point. otp is still a plain optional string: no
      OTP-generation flow exists anywhere in this project. deliver() still takes
      signatureUrl/photoUrl as caller-supplied URL strings — 0.17.9 (2026-08-12) made
      those come from a real upload, POST /shipment-movement/{shipmentId}/pod-upload
      (multipart) via FileStoragePort/S3FileStorage — but as of 0.23.0 (2026-08-14) the
      URLs themselves are no longer columns on this row: markDelivered() dropped both
      parameters, and deliver() writes them as ShipmentAsset rows (assetType POD, kind
      SIGNATURE/PHOTO) instead, in the same shipment_assets table a new booking-time
      image upload also uses (assetType BOOKING). See CHANGELOG.md 0.23.0 and
      shipment-booking.md's own section on this table.

shipment_status_history (existing table, V17) gained three columns:
├── branch_id     which branch this transition happened at (null for BOOKED/CANCELLED)
├── manifest_id   which manifest (set from MANIFEST_CREATED onward)
└── vehicle_id    which vehicle carried it (set only on the DISPATCHED entry)

shipments (existing table) gained one column:
└── manifest_id   no physical FK — cross-module, validated in the service layer
```

## Orchestration — one-directional dependency to dodge a Spring circular bean

Both "create a manifest" (needs to mutate shipments: `attachToManifest`) and
"dispatch a manifest" (needs to mutate shipments: `transitionToDispatched`) are
`ManifestServiceImpl` methods that call into `ShipmentService`. The reverse never
happens — `ShipmentServiceImpl` has no dependency on `ManifestService` at all. This
was a deliberate fix, not the first draft: the natural split (put dispatch
orchestration in `ShipmentMovementServiceImpl`, since the REST endpoint is
`/shipment-movement/dispatch`) would have made `ShipmentServiceImpl` depend on
`ManifestService` for the vehicle/driver assignment while `ManifestServiceImpl`
depends on `ShipmentService` for the attach loop — two Spring beans each waiting on
the other's constructor, a real `BeanCurrentlyInCreationException` at boot, not just an
architectural smell. Resolved by keeping *all* orchestration in `ManifestServiceImpl`
(`create`, `dispatch`) and adding the shipment-side mutations it needs
(`attachToManifest`, `findOutScanShipments`, `transitionToDispatched`) as plain
`ShipmentService` methods with no manifest awareness of their own. `outScan`/`inScan`/
`assignOutForDelivery`/`deliver`/`timeline` need no manifest dependency at all — a
shipment only ever needs its own `manifestId`, never the `Manifest` object.

`ShipmentMovementController` (new, `/api/v1/shipment-movement`) is the seam that
*does* call both services — a controller composing two application services is fine;
only service-to-service edges can deadlock Spring's bean graph.

## Business rules (all verified live)

- **OUT_SCAN**: shipment must be `MANIFEST_CREATED` and belong to the given manifest;
  bulk request reports **per-item** success/failure rather than all-or-nothing (the
  same shape `BranchService.assignUsers`'s `assigned/skipped/rejected` already uses).
  A second scan of the same tracking number fails with "Cannot be scanned — currently
  OUT_SCAN."
- **DISPATCH**: refuses a manifest already dispatched, refuses one with zero
  `OUT_SCAN` shipments ("Manifest ... has no OUT_SCAN shipment to dispatch."), refuses
  an inactive vehicle, 404s an unknown vehicle/driver id. On success: manifest →
  `DISPATCHED` with vehicle+driver+timestamp, every `OUT_SCAN` shipment on it →
  `DISPATCHED` in the same transaction. **(2026-08-14)** This is also where branch
  commission is now earned — `ShipmentServiceImpl.transitionToDispatched` publishes
  `ShipmentEvent.DispatchCommissionEarned` per shipment (PAID + booking branch's
  `instantCommission` on), moved off booking time on direct user request. See
  `branch-wallet.md`'s "Branch commission moved from booking-time to Trip Challan
  (dispatch) time".
- **IN_SCAN**: shipment must be `DISPATCHED` *and* `receivingBranchId` must equal the
  shipment's own `deliveryBranchId` — "Receiving branch does not match this
  shipment's delivery branch." verified live, distinct from a plain wrong-status
  refusal.
- **OUT_FOR_DELIVERY**: shipment must be `IN_SCAN`; creates or updates its
  `DeliveryAssignment` in the same call.
- **DELIVER**: shipment must be `OUT_FOR_DELIVERY`; `receiverName` is
  `@NotBlank`-validated at the DTO layer (verified: blank value → 400 with the field
  named, not a generic message) and again defensively in
  `DeliveryAssignment.markDelivered`. As of 0.23.0, a non-blank `signatureUrl`/
  `photoUrl` is recorded as a `ShipmentAsset` row (`POD`/`SIGNATURE`,`POD`/`PHOTO`) in
  the same transaction, not written onto `DeliveryAssignment` itself any more.
- **Cancel** (existing endpoint, unchanged code): refused from `DISPATCHED` onward,
  verified live against an `OUT_FOR_DELIVERY` shipment — "is OUT_FOR_DELIVERY and can
  no longer be cancelled — it has left the branch."

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/manifests` | Create — the prerequisite, not in the brief's own list |
| `GET` | `/api/v1/manifests` / `/{id}` / `/{id}/shipments` | Same prerequisite |
| `POST` | `/api/v1/vehicles` | Create; `PUT`/`GET`/`PATCH .../activate`/`.../deactivate` alongside |
| `POST` | `/api/v1/shipment-movement/out-scan` | `{manifestId, trackingNumbers[]}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/dispatch` | `{manifestId, vehicleId, driverUserId}` |
| `POST` | `/api/v1/shipment-movement/in-scan` | `{receivingBranchId, trackingNumbers[]}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/out-for-delivery` | `{shipmentIds[], deliveryUserId}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/deliver` | `{shipmentId, receiverName, remarks?, otp?, signatureUrl?, photoUrl?}` |
| `POST` | `/api/v1/shipment-movement/{shipmentId}/pod-upload` | multipart `file` + `kind` (`PHOTO`\|`SIGNATURE`) → `{url}`, 0.17.9 |
| `GET` | `/api/v1/shipments/{id}/timeline` | 7 named steps, `completed` + `changedAt`/`changedBy` per step — distinct from the existing raw `/history` |

## Frontend (`features/shipment-movement`, `features/manifest`)

API-only, no mock. Six pages exactly matching the brief's Frontend section:
`out-scan.ts` (folds in the Create Manifest prerequisite — Search Manifest / Display
Shipments / Scan Tracking Number / Bulk Scan / Show Scan Count, all present),
`dispatch.ts` (Select Manifest / Assign Vehicle / Assign Driver / Dispatch),
`in-scan.ts` (defaults the receiving branch to the signed-in user's own branch, same
"no picker, my own branch" pattern `shipment-create.ts` set for Booking Branch),
`out-for-delivery.ts` (lists the caller's own branch's `IN_SCAN` worklist, bulk-assigns
a delivery user), `delivery.ts` (Search Shipment + Delivery Form; OTP still a plain
optional text field, Signature/Photo now a real file-upload button — 0.17.9 — that
posts to `pod-upload` and fills the same URL field it always had), `timeline.ts` (linked from
`shipment-view`'s action bar next to Charges/History/Documents).

**Nav**: the five aspirational `Operations` leaves (`manifest`, `receive`, `dispatch`,
`delivery`, and a new `out-for-delivery`) lost their `(Soon)` tag and now point at the
real routes — `manifest`/`dispatch` regrouped under the *booking*-branch desk
(`OPS_BOOKING`), `receive`/`out-for-delivery`/`delivery` under the *delivery*-branch
desk (`OPS_DELIVERY_DESK`). This corrects, not merely extends, `navigation.config.spec
.ts`'s prior pinned assumption that the whole aspirational block was delivery-desk
work — a guess made before this module existed, now replaced with the real business
flow's own branch split (Out Scan/Dispatch happen before the run leaves the booking
branch; In Scan/Out For Delivery/Deliver happen after it arrives). `sorting` has no
module behind it and stays `(Soon)`.

**7 new backend unit tests classes worth of coverage** (98 → ~121 relevant, exact
count in CHANGELOG): `ManifestServiceImplTest`, `VehicleServiceImplTest`,
`ShipmentMovementServiceImplTest` (out-scan/in-scan/out-for-delivery/deliver/timeline/
attachToManifest, one test per business rule above), plus `ShipmentStatusTest`/
`ShipmentServiceImplTest` updated for the renamed enum. **7 new frontend tests**
(`ShipmentMovementService`, `ManifestService` — HTTP-contract style, mirrors
`shipment.service.spec.ts`).

## Verified live (2026-08-03, MySQL 8.0.46, `SERVER_PORT=8081`)

Full pipeline over HTTP as `pune@gmail.com` (`BRANCH_MANAGER`, Pune, `COMPANY-C1`) on
two real `BOOKED` fixtures, Pune → Latur: manifest created (`MANIFEST_CREATED`
transition + `manifestId` stamped on both, confirmed via `GET /manifests/{id}
/shipments`); out-scan of both plus one bogus tracking number (2 succeed, 1 fails with
"No such tracking number."); a repeat out-scan of an already-scanned shipment refused
("Cannot be scanned — currently OUT_SCAN."); vehicle created (`mh12ab1234` normalised
to `MH12AB1234`); dispatch with an unknown vehicle id 404s, dispatch for real moves
manifest + both shipments to `DISPATCHED` (`shipmentCount: 2`), a second dispatch
attempt refused ("has already been dispatched."); in-scan at the wrong branch (Pune
instead of Latur) refused with the exact branch-mismatch message, in-scan at the
correct branch (Latur) succeeds for both; out-for-delivery assigns both to the signed-
in user; deliver with a blank receiver name 400s with the field named, deliver for
real moves the shipment to `DELIVERED`; `GET /timeline` shows all seven named steps
completed in order with real timestamps; `GET /history` shows `branchId` flipping
Pune→Latur at the `IN_SCAN` entry and `vehicleId` present only on the `DISPATCHED`
entry; cancel of an `OUT_FOR_DELIVERY` shipment correctly refused.

Through the Angular console (same login, fresh `ng serve` restart required — a stale
already-running dev server did not pick up the new routes/nav on its own and needed a
hard restart, not just a file save): Out Scan found the manifest by number and showed
"2 of 2 scanned" with correct status badges (`Out For Delivery` amber, `Delivered`
green); the Timeline page rendered all seven steps with distinct icons and real
timestamps; the Delivery page found an `OUT_FOR_DELIVERY` shipment by tracking number,
pre-filled the receiver name, and closing it produced a live toast and reset the form;
Dispatch/In Scan/Out For Delivery all rendered cleanly, the last correctly showing an
empty "Nothing waiting" state once both fixtures had already passed through it. No
console errors during the session.

## Follow-up

- 2026-08-05: `deliver()` now publishes `ShipmentEvent.CodCollectedAtDelivery` (delivery
  branch, shipment's `ShipmentCharge.netAmount`) whenever `paymentMode
  .isCollectAtDelivery()` — closes the "COD delivery debit seam" this module left open
  (finance's `COD` `SubTransactionType` existed but nothing ever fired it). Handled by
  `finance`-owned `ShipmentDeliveryWalletListener`. Not yet verified live — see
  `MEMORY/modules/branch-wallet.md`'s Next list.
- 2026-08-13 (v0.20.8): DRS Report added — `ShipmentService.listDrs`/`getDrsDetail`, two
  new `GET /shipment-movement/drs`/`/drs/detail` endpoints, `features/reports/
  drs-report.ts`/`drs-detail.ts`. A DRS "run" for reporting is delivery user + delivery
  branch + calendar day, grouped in Java from `DeliveryAssignment` rows — there is still
  no persisted DRS/batch entity; this reads the same rows `printDrs()` (0.17.5, above)
  already relied on, just after the fact and grouped. Verified live. Full detail in
  `CHANGELOG.md` 0.20.8.
- 2026-08-13 (v0.20.9), same day: every DRS now gets a real, unique, printable number —
  direct request "every drs should have a uniq number as DRS000001". `V31` migration adds
  `delivery_assignment.drs_number` (nullable) + `company_drs_sequences` (one row per
  company, same `LAST_INSERT_ID(expr)` upsert idiom as `company_shipment_sequences`/
  `branch_shipment_sequences`). `ShipmentServiceImpl.nextDrsNumber` generates one number
  per bulk `assignOutForDelivery` call ("Generate DRS") — `"DRS" + 6-digit serial`, e.g.
  `DRS000001` — stamped on every `DeliveryAssignment` row that call touches (new or
  reassigned). **The grouping key for a "run" is still delivery user + delivery branch +
  calendar day, unchanged** — the number is an attribute on top of that grouping, not a new
  identity for it, so two separate "Generate DRS" clicks for the same user/branch on the
  same day still report as one run (pre-existing behavior, not touched); `DrsSummary`/
  `DrsDetail` surface the most recent `drsNumber` in the group (null for runs made entirely
  of pre-V31 rows). `BulkMovementResult`/`BulkMovementResponse` gained a `drsNumber` field
  (null for `inScan`, the only other caller). Frontend: DRS Report table and DRS Detail page
  both show the number; Out For Delivery's Result card and Print DRS sheet show it too,
  sourced straight off the `assignOutForDelivery` response rather than a second fetch. `mvn
  test` green (`ShipmentServiceImplTest`/`ShipmentMovementServiceImplTest`), `tsc --noEmit`
  clean. **Not verified live** — no local MySQL session this task; `V31` not yet applied.
  Full detail in `CHANGELOG.md` 0.20.9.
- 2026-08-13 (v0.21.1): **DRS charge per item quantity** — direct request "when shipment
  order delivered through DRS then 2 rs should be debited for every qty ... set branch
  level while creating branch same as gst and commission". `deliver()` now also computes
  `drsCharge = delivery branch's own branches.drs_charge_per_qty (V32, default 2.00) *
  total item quantity` (summed from `ShipmentItemRepository
  .findAllByShipmentIdWithinCompany`) and, when positive, publishes a new
  `ShipmentEvent.DrsChargeApplicable` — unlike `CodCollectedAtDelivery`, on **every**
  delivery, not gated by payment mode. Handled by a second method on the existing
  `ShipmentDeliveryWalletListener`, calling new `WalletService.debitForDrsCharge`
  (`DRS` `SubTransactionType`). See `MEMORY/modules/branch-wallet.md`'s "DRS charge credit
  seam" and `MEMORY/modules/branch.md`. `mvn test` 712 → 713. **Not verified live** — no
  local MySQL session this task; `V32` not yet applied. Full detail in `CHANGELOG.md`
  0.21.1.
- 2026-08-14 (v0.24.2): **direction bug fixed** — 0.21.1 above shipped `DRS` as a debit on
  a miscommunication; it was always meant to be a commission *credited* to the delivery
  branch. `SubTransactionType.DRS` flipped `Direction.DEBIT` → `Direction.CREDIT`,
  `WalletService.debitForDrsCharge` renamed `creditForDrsCharge`
  (`DrsChargeCreditCommand`), posts `TransactionType.CR`. Amount formula (`drsChargePerQty
  * qty`) unchanged. Full detail in `CHANGELOG.md` 0.24.2.
- 2026-08-14 (v0.23.1, corrected same day in v0.23.2): THC gained its own
  shipment-removal checkbox list (reuses `ManifestService.removeShipment`, no new
  endpoint — previously Loading-Sheet-only via `ManifestCard`'s `showRemoveAction`) and
  an optional operator-entered **Departure Time** (`Manifest.departureTime`, `V34`,
  falls back to `dispatchedAt` when blank). v0.23.2, on direct request, made the
  checkbox removal deferred rather than instant: unchecking only drops the row
  client-side (`pendingRemovals`), no confirm popup, no immediate `removeShipment`
  call — the actual removals fire from `dispatch()` itself, right before the dispatch
  POST. Full detail in `CHANGELOG.md` 0.23.1/0.23.2. **Not yet applied**: `V34`.
- 2026-08-14 (v0.23.3): In Scan's `receiveManifest` no longer bulk-receives every
  pending shipment on a manifest blind — it opens a checkbox checklist
  (`receivingManifest`/`selectedTrackingNumbers`, all checked by default) so a
  short-received shipment (not physically on the vehicle) can be unchecked before the
  `inScan` POST, which only carries the checked trackingNumbers. Same
  checklist-before-bulk-action shape THC's own checklist (0.23.1/0.23.2) and Out For
  Delivery's (v0.17.7) already use. Full detail in `CHANGELOG.md` 0.23.3.

## Not exercised

- A `BOOKING_OPERATOR`/`DELIVERY_OPERATOR`-scoped JWT token (the pre-existing "company
  role, not JWT authority" gap this project has flagged since Branch RBAC — movement
  RBAC is role-based like every other module, same gap, not new here)
- The `RIVAL_CO` cross-company check (inherited gap)
- Concurrent out-scan/dispatch under real load
- `ManifestStatus.COMPLETED` — declared, nothing in this module's scope writes it
- A returned/failed-delivery path — `RETURNED` is a declared, reachable transition
  from `IN_SCAN`/`OUT_FOR_DELIVERY` but no endpoint in this module writes it (not in
  the brief's REST API list)

## Deliberately not touched

Finance, Reports (stop here per instruction). Hub Management. The authorise-on-
permissions capstone. A dedicated Manifest/Vehicle management UI beyond what Dispatch
and Out Scan need inline.
