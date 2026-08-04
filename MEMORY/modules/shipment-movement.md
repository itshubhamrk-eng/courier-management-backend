# Shipment Movement

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

Vehicle (com.courier.modules.manifest)
├── vehicleNumber   unique per company, upper-cased on save
├── vehicleTypeId   optional, no physical FK — master.domain.VehicleType
├── capacityKg, status (ACTIVE | INACTIVE), remarks

DeliveryAssignment (com.courier.modules.shipment — new table, current-state not ledger)
├── shipmentId   unique per company — one live assignment per shipment, re-assign updates in place
├── deliveryBranchId, deliveryUserId, assignedAt, status (ASSIGNED | DELIVERED)
└── deliveredAt, receiverName, deliveryRemarks, otp, signatureUrl, photoUrl
    — the proof-of-delivery capture point. otp/signatureUrl/photoUrl are plain
      optional strings: no OTP-generation flow, no signature-pad, no camera capture
      exist anywhere in this project. "API Ready" per the brief's own wording — the
      field is wired straight to the API, same "URL is the source of truth" honesty
      note CompanyLogo/ShipmentDocument already carry.

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
  `DISPATCHED` in the same transaction.
- **IN_SCAN**: shipment must be `DISPATCHED` *and* `receivingBranchId` must equal the
  shipment's own `deliveryBranchId` — "Receiving branch does not match this
  shipment's delivery branch." verified live, distinct from a plain wrong-status
  refusal.
- **OUT_FOR_DELIVERY**: shipment must be `IN_SCAN`; creates or updates its
  `DeliveryAssignment` in the same call.
- **DELIVER**: shipment must be `OUT_FOR_DELIVERY`; `receiverName` is
  `@NotBlank`-validated at the DTO layer (verified: blank value → 400 with the field
  named, not a generic message) and again defensively in
  `DeliveryAssignment.markDelivered`.
- **Cancel** (existing endpoint, unchanged code): refused from `DISPATCHED` onward,
  verified live against an `OUT_FOR_DELIVERY` shipment — "is OUT_FOR_DELIVERY and can
  no longer be cancelled — it has left the branch."

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/manifests` | Create — the prerequisite, not in the brief's own list |
| `GET` | `/api/v1/manifests` / `/{id}` / `/{id}/shipments` | Same prerequisite |
| `POST` | `/api/v1/vehicles` | Create; `GET`/`PATCH .../activate`/`.../deactivate` alongside |
| `POST` | `/api/v1/shipment-movement/out-scan` | `{manifestId, trackingNumbers[]}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/dispatch` | `{manifestId, vehicleId, driverUserId}` |
| `POST` | `/api/v1/shipment-movement/in-scan` | `{receivingBranchId, trackingNumbers[]}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/out-for-delivery` | `{shipmentIds[], deliveryUserId}` → `BulkMovementResponse` |
| `POST` | `/api/v1/shipment-movement/deliver` | `{shipmentId, receiverName, remarks?, otp?, signatureUrl?, photoUrl?}` |
| `GET` | `/api/v1/shipments/{id}/timeline` | 7 named steps, `completed` + `changedAt`/`changedBy` per step — distinct from the existing raw `/history` |

## Frontend (`features/shipment-movement`, `features/manifest`)

API-only, no mock. Six pages exactly matching the brief's Frontend section:
`out-scan.ts` (folds in the Create Manifest prerequisite — Search Manifest / Display
Shipments / Scan Tracking Number / Bulk Scan / Show Scan Count, all present),
`dispatch.ts` (Select Manifest / Assign Vehicle / Assign Driver / Dispatch),
`in-scan.ts` (defaults the receiving branch to the signed-in user's own branch, same
"no picker, my own branch" pattern `shipment-create.ts` set for Booking Branch),
`out-for-delivery.ts` (lists the caller's own branch's `IN_SCAN` worklist, bulk-assigns
a delivery user), `delivery.ts` (Search Shipment + Delivery Form, OTP/signature/photo
as plain optional text fields per the "API Ready" wording), `timeline.ts` (linked from
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
