# E-Way Bill Management

**Status:** DONE (v0.30.0, 2026-08-20). New package `com.courier.modules.ewaybill`,
migration `V47`. Integrated into Shipment Booking (`com.courier.modules.shipment`), not a
standalone unrelated module — see "Integration point" below for why.

## Purpose

Business rule the whole module exists for: a shipment whose **invoice value exceeds the
company's own configurable threshold** (`CompanySettings.ewayBillMandatoryValue`, default
`50000.00`) may not have its AWB generated until it carries a `VALIDATED` E-Way Bill. At or
under the threshold, an E-Way Bill is optional — a shipment may still carry one (any
status), or none at all. The threshold is never hardcoded in application code; it is read
fresh from Company Settings on every check, so a company can tune it without a deploy.

## Shape

```
EwayBill (own table, CompanyOwnedEntity, own lifecycle — not a Shipment sub-table)
├── shipmentId          real FK to shipments(id), ON DELETE RESTRICT
├── ewayBillNumber       nullable until issued; UNIQUE(company_id, eway_bill_number)
├── invoiceNumber, invoiceDate, invoiceValue     required
├── documentType         INVOICE | BILL_OF_SUPPLY | DELIVERY_CHALLAN | OTHERS
├── documentNumber, documentDate
├── transporterId        free text — no Transporter/Vendor entity exists yet in this
│                        codebase (VENDOR permission module is seeded, nothing implements
│                        it — the same "responsibility list ahead of the code" pattern)
├── vehicleNumber, distance
├── validFrom, validUntil
├── status               NOT_REQUIRED | REQUIRED | PENDING | UPLOADED | VALIDATED |
│                        INVALID | EXPIRED | CANCELLED — EwayBillStatus.canTransitionTo
│                        is the one source of truth; CANCELLED is terminal
├── documentUrl          no new file-storage backend — reuses shipment's own
│                        FileStoragePort/S3 seam (keyPrefix "eway-bill"), not duplicated
└── remarks

shipments (V47 additions, not a new table)
├── invoiceValue         entered at booking time — the number the mandatory check reads
└── ewayBillRequired     frozen at booking time from invoiceValue vs. the threshold in
                         effect that moment — never recomputed against a later threshold
                         change, so an already-booked shipment's requirement never drifts

company_settings_config (V47 addition)
└── ewayBillMandatoryValue   DECIMAL(19,4) DEFAULT 50000.0000 — new "E-Way Bill" section,
                             `PATCH /company-settings/eway-bill`, same merge-not-blank
                             pattern every other section already follows
```

No unique `(company_id, shipment_id)` — a shipment may carry more than one row over its
life (a `CANCELLED` one re-issued). `EwayBillServiceImpl.currentFor` (reads) takes the
newest non-cancelled row, falling back to the newest row overall if every one is
cancelled; `upsertForShipment` (writes) never reuses a cancelled row — a write past
cancellation always issues a fresh one. **A real bug this asymmetry caught in its own
unit test** (`upsertReissuesAfterCancellation`): the first draft used the read helper's
own cancelled-fallback for writes too, which tried to resurrect a `CANCELLED` row straight
to `VALIDATED` and threw `EwayBillStatus`'s own illegal-transition guard. Fixed by giving
`upsertForShipment` its own non-cancelled-only lookup.

## Integration point — why this lives inside Shipment Booking's own transaction

The brief's flow diagram places "Check E-Way Bill Requirement" and "Validate E-Way Bill"
**before** Pricing and AWB Generation, inside the same booking flow — but this codebase
mints the AWB synchronously inside `ShipmentServiceImpl.create()`, a single
`@Transactional` method, not a separate later step. There is no "AWB Generation" action to
intercept after the fact.

So the gate is enforced **inline, inside `create()`/`update()`**, before anything is
persisted:

1. `CreateShipmentCommand`/`UpdateShipmentCommand` gained `invoiceValue` and an optional
   `EwayBillDataCommand ewayBill` (reused across three call sites — standalone create/
   update and this inline booking path — see `EwayBillDataCommand`'s own doc).
2. `EwayBillService.isRequired(invoiceValue)` — `invoiceValue > mandatoryThreshold()`.
3. `EwayBillService.enforceBookingRequirement(invoiceValue, ewayBill)` — no-op if not
   required; otherwise throws `BusinessRuleException` with **exactly** the brief's own
   wording (`"E-Way Bill is mandatory because invoice value exceeds ₹50,000."`, threshold
   interpolated) if `ewayBill` is missing, or the provider's own reason appended if present
   but invalid. Thrown **before** the shipment is built or an AWB number is minted — the
   whole transaction never starts writing, so "do not generate AWB" is a real backend
   guarantee, not a frontend-trusted checkbox.
4. Only after the shipment is saved (has an id) does `EwayBillService.upsertForShipment`
   create/update the `EwayBill` row — `VALIDATED` if the provider accepted it, `INVALID`
   otherwise (recorded, not blocking, when the E-Way Bill was optional in the first place).

`update()` (`PUT /shipments/{id}`, full replacement while still `BOOKED`) runs the exact
same two-step gate — an edit that raises `invoiceValue` past the threshold must not leave
the shipment `BOOKED` without a validated E-Way Bill either, even though no new AWB is
minted on update.

**Standalone lifecycle** (`POST/PUT/GET /eway-bills`, `.../validate`, `.../upload`,
`.../cancel`) exists alongside the inline path for managing an E-Way Bill *after*
booking — amending vehicle/validity, re-validating, attaching the document, cancelling and
re-issuing. `EwayBillController`'s own class doc is explicit that the booking-time gate is
enforced in `ShipmentController`, not here.

## EwayBillProvider — the "future ready" seam

`EwayBillProvider.validate(ValidationRequest) -> ValidationOutcome` is the seam between
"confirm an E-Way Bill's data is good" and whichever authority actually confirms one.
Today's only implementation, `LocalEwayBillProvider`, does **local field/format checks
only** — 12-digit number shape, non-blank invoice number/date, positive invoice value,
validity not already expired — never a call to the government e-way bill portal, per the
brief's own "do not implement an external API unless already configured." A real
government/GST-network integration becomes a second `EwayBillProvider` implementation
swapped in by configuration, with zero change to `EwayBillServiceImpl` or Shipment
Booking. Auto-generate/auto-extend/cancel/expiry-alerts/vehicle-update/transporter-update
are all reachable through this same seam once a real provider exists — none were built
here, since none are configured in this deployment (the brief's own instruction).

## Permissions

`PermissionModule.EWAY_BILL` (135, between `SHIPMENT` 130 and `TRACKING` 140), 8 rights:
`CREATE`/`READ`/`UPDATE`/`SEARCH`/`EXPORT`/`UPLOAD`/`VALIDATE`/`CANCEL`. Two new
`PermissionAction` values, `VALIDATE`(23)/`CANCEL`(24) — the brief's own `EWAY_BILL_VIEW`
is seeded as `EWAY_BILL_READ`: this catalogue has never used a `_VIEW` code anywhere
(`SHIPMENT_READ`, `CUSTOMER_READ`, …), so `EWAY_BILL` follows the existing vocabulary
rather than being the one exception. Catalogue 223 → 231 (`DefaultPermissionCatalogTest`
asserts it). `BRANCH_MANAGER`/`BOOKING_OPERATOR` (the two roles that already hold
`SHIPMENT_CREATE`) are extended with all eight, same precedent V16/V17 set.

RBAC enforcement itself is still role-based (`WRITERS = hasAnyRole(COMPANY_ADMIN,
BRANCH_MANAGER, OPERATOR)`, `READERS = isAuthenticated()`), matching every other module
ahead of the "authorise on permissions" capstone — the seeded catalogue rows are the
"responsibility list ahead of the code" this project has flagged before, not yet wired
into `@PreAuthorize`.

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/eway-bills` | Attach an E-Way Bill to an already-booked shipment |
| `PUT` | `/api/v1/eway-bills/{id}` | Full replacement; refused once `CANCELLED` |
| `GET` | `/api/v1/eway-bills/{id}` | |
| `GET` | `/api/v1/eway-bills` | Paged, filterable by `shipmentId`/`status` |
| `POST` | `/api/v1/eway-bills/{id}/validate` | Re-runs `EwayBillProvider` against current fields |
| `POST` | `/api/v1/eway-bills/{id}/upload` | multipart `file`, PDF/JPG/PNG only |
| `POST` | `/api/v1/eway-bills/{id}/cancel` | Terminal |
| `PATCH` | `/api/v1/company-settings/eway-bill` | The mandatory threshold, `COMPANY_ADMIN` only |

`ShipmentResponse` gained `invoiceValue`, `ewayBillRequired`, and a nested `ewayBill`
(`EwayBillInfo`: id/number/status/invoiceValue/validFrom/validUntil/documentUrl) — plain
fields only, no enum type, so `shipment.api` depends only on `ewaybill.application`
(`EwayBillService.EwayBillSnapshot`), never on `ewaybill.domain` — the architecture doc's
"a feature may depend on another feature's application service, never its domain
entities" rule, applied here even though a couple of older cross-module reads elsewhere in
this codebase (e.g. `ShipmentServiceImpl.resolveRouteCode` returning a `Route` domain
entity) don't hold themselves to it as strictly.

## Frontend (`features/shipment`)

**Booking screen** (`shipment-create.ts`): a new "E-Way Bill" card — Invoice Value input,
a live `E-Way Bill Optional` / `⚠ E-Way Bill Mandatory` chip (auto-opens the section the
moment the value crosses the threshold, read from `GET /company-settings`'s new `ewayBill`
section, default 50000 until that resolves), "+ Add E-Way Bill" reveals E-Way Bill Number/
Invoice Number/Invoice Date/Vehicle Number/Valid From/Valid Until/Remarks plus a document
picker, "Remove" clears it back to unset. `ewayBillReason()` (a plain method, not
`computed()` — same `FormControl.value` staleness reason as this file's existing
`readyToPrice()`) mirrors `LocalEwayBillProvider`'s own checks for instant feedback and
disables **Book Shipment** with the reason shown next to it — **UX only**, the backend
re-enforces the real gate regardless; the frontend cannot be trusted to have gotten this
right, per the brief's own instruction.

**Booking Summary sidebar** (this app's actual "Review & Confirm" surface — the booking
screen is one page with a sticky pricing sidebar, not a step wizard; see
`shipment-booking.md`) shows Invoice Value and the same Optional/Mandatory line next to
Route/Load, and repeats the blocking reason directly above the Book button.

**Document upload**: picked as a `File` at booking time (matching the existing "Shipment
Image" delayed-upload shape), actually uploaded via `POST /eway-bills/{id}/upload` **after**
`book()` succeeds — the response's own `ewayBill.id` (nested on `ShipmentResponse`) is what
makes this possible without a second round trip. Fire-and-forget: a failed document upload
never undoes an already-booked, already-validated shipment.

**Shipment Details** (`shipment-view.ts`): new "E-Way Bill" card — Required/Invoice Value/
Number/Status/Validity/Document link, shown whenever the shipment has any of
`ewayBillRequired`/`invoiceValue`/`ewayBill` set; a red "Missing — required before AWB
generation" line when required but absent (should not normally be reachable, since the
backend blocks that shipment from ever having been booked — shown anyway as a defensive
display, e.g. for data from before this module existed). `Validate`/`Upload Document`/
`Cancel` actions call the standalone `EwayBillService` (new `features/shipment/eway-bill
.service.ts`, mirrors `ShipmentService`'s own shape one-to-one) and reload the shipment on
success.

**Not built**: a standalone E-Way Bill list/management page. The brief's own Frontend
section only asks for booking-flow integration and shipment-detail display, not a
masters-style CRUD list — the standalone backend endpoints exist for that (and for
Swagger/API consumers) without a redundant UI page nobody asked for.

## Testing

Backend: `EwayBillStatusTest` (every legal/illegal transition, including the terminal
`CANCELLED` and the self-transition refusal `upsertForShipment`'s status-diffing depends
on), `EwayBillServiceImplTest` (20 cases — `isRequired` at/under/over the threshold,
`enforceBookingRequirement` optional-allows-nothing / mandatory-missing-refused-with-exact-
wording / mandatory-invalid-refused / mandatory-valid-passes, `upsertForShipment`
create-when-none / update-existing-in-place / **reissue-after-cancellation** / marks-
INVALID-without-throwing, `create`'s DB-constraint-violation translation, `cancel`/
`validate` both refusing an already-`CANCELLED` row). `mvn test` 791 → 813 (backend was
mid-flight with an unrelated, concurrently-developed "manual shipment number" feature
landing in this same working tree during this task — both features' test suites coexist
cleanly; see the concurrent-session note below). `DefaultPermissionCatalogTest` updated
223 → 231.

Frontend: `tsc --noEmit -p tsconfig.app.json` and `ng build --configuration production`
both clean; `ng test` 133/134 (the one failure, `navigation.config.spec.ts`'s "reads the
branch and shipment reports" case expecting a `reports-dashboard` nav node, is pre-existing
and unrelated — confirmed via `git log`/`git diff` that this task never touched
`navigation.config.ts`, and the failure predates this session).

**Not verified live** — no MySQL boot or browser click-through this session; verification
stopped at the compile/build/unit-test bar, same precedent several other modules in this
project have shipped under (e.g. Follow-up 0.29.0, GST-on-Freight-Factor 0.24.0). `V47` has
not been applied against a real database.

## Concurrent session note (2026-08-20)

This task's own working tree had a second, concurrent session actively building an
unrelated "manual shipment number" feature (`CreateShipmentRequest.manualShipmentNumber`,
`ShipmentServiceImpl.existsByCompanyIdAndShipmentNumber`, `ShipmentService
.attachPodAsset`) in the exact same files this module needed to touch
(`ShipmentServiceImpl`, `ShipmentService`, `CreateShipmentCommand`, `ShipmentMapper`,
`CreateShipmentRequest`, `ShipmentServiceImplTest`) — several `mvn test` runs mid-task
briefly failed on the other session's own not-yet-finished edits (a referenced method not
yet added), self-resolved a short wait later each time, matching the same pattern
`[[branch-wallet]]`/`shipment-movement.md`'s own 0.28.5/0.28.6 notes describe. Both
features' code and tests coexist cleanly in the final state; worth remembering that this
working tree had two sessions in it again, not one, for whoever reads the diff next.
