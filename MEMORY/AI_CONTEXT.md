# AI_CONTEXT

> **Read this file first, before every task.**
> It is the entry point to the project memory. Keep it updated after every change.

---

## Current Version

`0.39.0` — **Missing indexes for a few real hot query paths (V56) + prod EC2 found
memory-starved, already OOM-killed the backend once.** Direct request to add indexing
after reports of slow pricing/booking on prod. Audited the whole schema against real
repository/Specification call sites (not guessed) — already well-indexed everywhere;
found four genuine gaps and added them in `V56`: `shipments(company_id,
current_location_id)` (next_location_id already had one, current_location_id never did),
`delivery_assignment(company_id, assigned_at)` and `(company_id, delivered_at)` (DRS/
Delivery Report date filters, only branch/user composites existed), `manifests(company_id,
delivery_branch_id, status)` (only booking_branch_id had one). Honesty: indexing was
unlikely to be the actual cause — this account has a handful of rows per table, and direct
timing on `/pricing/calculate` etc. came back 130-330ms consistently. The real cause found
same session: prod EC2 (`35.154.220.116`) has ~909MB RAM total, is actively swapping, and
`dmesg` shows the kernel already OOM-killed the backend `java` process once — see
[[prod-ec2-oom-memory-starved]]. Flagged directly to the user (upsize / tune container
limits / defer); they chose to defer and proceed with indexing only — the RAM issue is
still unresolved. `mvn test` still 945/945 (schema-correctness check only, not a
performance claim — no test exercises query plans/timing). Full detail in `CHANGELOG.md`
Unreleased 2026-09-04 "Missing indexes for a few real hot query paths (V56)".

Previously current:

`0.38.0` — **The 0.37.0 fix didn't actually work — real cause was Spring transaction
propagation.** Deployed 0.37.0, then immediately got "An unexpected error occurred when
pincode entered" live from the user. Read the real prod `courier-backend` container logs
(not guessed) and found `UnexpectedRollbackException` on `POST /pricing/calculate`, stack
`PricingEngineImpl.calculate -> FreightFactorServiceImpl.tryCalculate ->
AddressDistanceService.resolveBranchDistance` — the exact chain 0.37.0 touched. Root
cause: `resolveBranchDistance`'s default `@Transactional` propagation (`REQUIRED`) joins
the caller's transaction, so Spring's proxy marks the *shared* physical transaction
rollback-only the instant it throws — at its own AOP boundary, before `tryCalculate`'s
`catch` block (0.37.0's fix) ever runs. Catching the exception stops it propagating but
does nothing to un-mark the transaction; the caller's later commit then fails with a
different, generic exception 0.37.0 never anticipated. Invisible to `mvn test` throughout
both passes — Mockito unit tests never wire real Spring AOP transaction proxies, and this
codebase has no `@SpringBootTest` on this path, so only a real deployed request exercises
it; noted honestly rather than re-claimed as "tested" this time. Fixed for real:
`resolveBranchDistance` now runs `Propagation.REQUIRES_NEW` (its own transaction,
suspending the caller's) so a failure there rolls back only itself and 0.37.0's `catch`
genuinely works. Scoped to just this one method. `mvn test` still 945/945. **Deployed to
prod** (backend-only, no migration) and checked directly: `POST /pricing/calculate` still
prices cleanly on the known `TESTING`/`TEST-2` lane, and the container logs for 5 minutes
post-deploy show zero `UnexpectedRollbackException` entries. Couldn't force a fresh live
repro of the original trigger — every real branch on the account already has a resolved
geocode — so this rests on the Spring semantics diagnosed from the original stack trace,
not a new live failure-then-success. Full detail in `CHANGELOG.md` Unreleased 2026-09-04
"Deployed the transaction-propagation fix to prod" and 2026-09-03 "The ungeocoded-branch
booking fix didn't actually work — real cause was Spring transaction propagation".

Previously current:

`0.37.0` — **Ungeocoded branch no longer blocks Shipment Booking.** (Superseded
immediately by 0.38.0 above — the fix here looked right and passed every test, but did
not actually work once deployed; kept for the record of what was tried and why it
wasn't enough.) Direct report of
"Both addresses need a resolved location before their distance can be calculated..."
surfacing during booking. `FreightFactorServiceImpl.tryCalculate` — the Freight Factor
grid's booking-time fallback, now legacy since District Level Freight became the
authoritative/mandatory freight source in 0.34.0 — resolved the branch pair's road
distance with no `try`/`catch`, so a branch with no geocoded lat/long threw straight out
of a method whose own contract (`Optional`, "a gap in this grid no longer blocks a
caller") already said it shouldn't. Fixed: catches that specific exception and returns
empty, same as a genuine grid gap; a zero/negative weight is still refused outright,
validated separately so it isn't swallowed by the same catch. The standalone Freight
Factor calculator page's own `calculate()` is untouched — still throws for real there.
`mvn test` 943 -> 945 (2 new), full suite green. Full detail in `CHANGELOG.md`
Unreleased 2026-09-03 "Ungeocoded branch no longer blocks Shipment Booking".

Previously current:

`0.36.0` — **Branch GST/PAN fields + login displayName-on-reload fix.** Two independent
fixes. (1) Branches gained `gstNumber`/`panNumber` (`V55`, both optional, GSTIN/PAN format
validated in `Branch.applyInvariants`, branch-level not company-level since a company can
run branches under different state GSTINs) — threaded through `Create`/`UpdateBranchCommand`,
both request DTOs, `BranchResponse`, `BranchMapper`, `BranchServiceImpl` (create/update/audit
snapshot), and a new "Tax Details" card in `branch-form.ts` (editable in both create and
edit, unlike the create-only branch-user block) plus `branch-view.ts`. (2) The JWT carries no
name claim (only `email`, same reasoning as `cnm`/`clogo`) so `AuthService.hydrate()` — the
path a hard page reload takes — fell back to showing the email where the name belongs; login
itself was fine since `applySession` had the real `LoginResponse.displayName` in memory. Fix:
`TokenService` now stashes `displayName` in `localStorage` (`cs.dname`) alongside the tokens,
set on login/`refreshProfile`/impersonation, read back in `hydrate()`, and correctly
stash/restore paired through `beginImpersonation`/`restoreStash` so exiting impersonation
restores the real user's name, not the impersonated one's. Adds no new test cases itself
(`mvn test` still 943/943 — see the 0.35.0 entry below for where that count actually came
from; its own changelog claim of "939 -> 942" undercounted by one, confirmed by isolating
its files and re-running). `tsc --noEmit`/`ng build --configuration production` clean,
`ng test` 147/148 (the one failure, `reports-dashboard`, pre-existing and unrelated — same
as every prior release). **Deployed and verified live on prod** (`35.154.220.116`, commit
`bc02c39`): real GSTIN/PAN persisted and rendered correctly on `AMAZING_LOGISTICS`
(view page's Charges card and the edit form's Tax Details card both), bad-format GST
rejected 400, lowercase PAN uppercased on save; login-form sign-in followed by a real hard
page reload kept the signed-in name in the top-bar chip instead of falling back to the
email. Full detail in `CHANGELOG.md` Unreleased 2026-09-03 "Deployed to prod (commit
bc02c39)".

Previously current:

`0.35.0` — **Rate/KG override (increase-only, GST on delta) + destination Area picker
ahead of Delivery Branch for freight resolution.** Direct request, two additions to
0.34.0's District Level Freight booking integration. (1) Freight Calculation card's
"Rate / KG" is now editable — `ShipmentServiceImpl.requireRateNotDecreased` refuses a
typed value below `freightCalc.ratePerKg()` (the matched slab's own rate, a floor never a
ceiling); `effectiveFreight(freightCalc, ratePerKgOverride)` replaces the bare
`freightCalc.baseFreight()` throughout `copyCharge`/`netAmountWithOtherCharges`, GST
recomputed only on the difference from the Pricing Engine's own superseded freight —
identical delta-algebra to the existing `odaCharge` override. (2) New "Destination
Pincode"/"Destination Area" fields ahead of "Delivery Branch" in `shipment-create.ts`: a
6-digit pincode looks up its Areas (`GET /pincodes/{id}/areas`, existing 0.32.2 endpoint),
primary auto-selects, picking one is now what triggers the freight preview (`readyForFreight`
gates on `destinationAreaId`) and syncs `deliveryPincode` so Pricing Engine and District
Level Freight never target two different destinations. New `PincodeCoverageLookupPort
.findByPincodeAndArea` (impl in `MasterDistrictFreightCoverageDirectory`) resolves
District via the chosen Area's own `cityId -> districtId` chain and ODA via that exact
`PincodeArea` link's own `odaApplicable` — more accurate than the pincode-wide single flag
`findByPincode` still uses when no Area is given. `mvn test` 939 -> 943 (4 new — this
entry's own text said 942/3 new at the time; corrected retroactively, isolating its files
confirmed 943 with 0.36.0's changes absent),
`tsc --noEmit`/`ng build --configuration production` clean, `ng test` 147/148 (the one
failure, `reports-dashboard`, pre-existing and unrelated). **Verified live** on real
`:8100`/`:4200` (a throwaway District Level Freight fixture inserted for PUNE -> Kolhapur,
since none existed for that branch) as `pune@gmail.com`: pincode `416013` auto-resolved
"Girgaon, Kolhapur" and priced Rate/KG 8.50 before a Delivery Branch was even picked;
booked a real shipment (`PUNE-000022`) with Rate/KG raised 10.00 -> 15.00 — live preview
(Freight 45.00/GST 8.10) matched the persisted `shipment_charges` row and detail page
exactly; a lowered rate (5.00, below the 10.00 floor) was cleanly refused server-side with
no shipment created. Full detail in `CHANGELOG.md` Unreleased 2026-09-03.

`0.34.0` — **District Level Freight wired into Shipment Booking.** Direct follow-up to
0.33.0: "Now connect it to Shipment Booking." Freight and ODA are now District Level
Freight's own job at booking time — mandatory, not a fallback, replacing the Pricing
Engine's Route/Rate/Freight Factor freight figure entirely; a booking is refused when no
configuration exists for the From Station + destination district, or the weight falls
outside 1-2000 KG. New `districtfreight.application.FreightCalculationService`/`Impl` (one
class, two callers: `DistrictLevelFreightController`'s new `POST .../calculate` preview
endpoint, and `ShipmentServiceImpl.create`/`update` authoritatively) resolves destination
pincode -> district via a new `PincodeCoverageLookupPort` (walks the existing global
`Pincode -> Area -> City -> District` chain, implemented in `master`) -> From Station's own
`DistrictLevelFreightRepository` row -> weight slab -> base freight -> ODA (row's own
`odaApplicable` AND the pincode's own `odaApplicable`, never a hardcoded 250) -> total.
`ShipmentServiceImpl` still calls the Pricing Engine unchanged for fuel/handling/insurance/
discount/round-off/GST% — freight+ODA alone are overridden via the identical delta-algebra
trick the existing (0.30.3) manual-ODA-override already used, extended to freight too, so
the commission formulas (`copyCharge`) are byte-for-byte unchanged, only what `freight`
resolves to differs. Frontend: new "Freight Calculation" card in `shipment-create.ts`'s
Booking Summary (From Station/District/Weight/Slab/Rate/Base Freight/ODA/Total, own
loading/error states, gates Book Shipment), new `freight-calculation.service.ts`. `mvn
test` 904 -> 939 (35 new/updated — `FreightCalculationServiceImplTest` covers the brief's
full test list: worked examples, every slab boundary, ODA/non-ODA, missing/inactive
config, invalid weight, multi-record pincode coverage). `tsc --noEmit`/`ng build
--configuration production` clean, `ng test` 147/148 (the one failure, `reports-dashboard`,
pre-existing, unrelated). **Not verified live this session** — no MySQL boot or browser
click-through; verification stopped at the compile/build/unit-test bar. Full detail in
`CHANGELOG.md` Unreleased 2026-09-03.

Previously current:

`0.33.0` — **District Level Freight module: new rate-setup module, From Station + District +
six fixed weight slabs + configurable ODA, plus Excel import.** Direct full-spec request,
explicitly scoped to rate setup only — Shipment Booking, Commission, Rate Master, Pricing
Engine and Freight Factor are all untouched by this task. New `com.courier.modules
.districtfreight` (`V54`): `DistrictLevelFreight` is company-owned, keyed on `branchId`
("From Station", a `Branch` — this codebase has no separate Station Master) + `districtId`
(the existing global District master), `UNIQUE (company_id, branch_id, district_id)`
preventing a duplicate combination at the DB level. Six `rate1To15`..`rate1501To2000`
per-KG columns plus `odaApplicable`/`odaCharge` (configurable per row, defaults `250.0000`,
never hardcoded into any calculation). `branchId`/`districtId` are validated through this
module's own `BranchLookupPort`/`DistrictLookupPort` seams (implemented in `company`/
`master` respectively) rather than importing either entity — the same cross-feature-port
discipline `Route.bookingBranchId`/`BranchPincodeMapping.pincodeId` already use.
`ratePerKgFor(BigDecimal)` is a pure domain lookup for "the COMPLETE weight uses exactly
one slab's rate, never a progressive split" — declared for a future booking integration,
called from nowhere yet. RBAC is role-based (`COMPANY_ADMIN` writes, any authenticated
company user reads), no new permission-catalogue rows, mirroring `RateServiceImpl` exactly.

**Excel import** (new `poi-ooxml` dependency — no prior Excel import existed anywhere in
this codebase): maps the sheet's own headers (`From Station`/`District`/six weight-slab
rate columns), a row counts as data only when all eight of those cells are present and (for
the six rates) numeric — a blank spacer row and the sheet's trailing "* ODA charge Rs.250
extra..." note row are both silently ignored this way, never reported as errors. An
existing From Station + District combination is upserted (updated), never rejected —
only a combination repeated *within the same file* is a real error. `POST .../import
/preview` (dry run) and `POST .../import` (commits, one transaction per row via a
cross-bean call, same reasoning `PincodeBulkImportService` documents).

**Frontend**: bespoke `features/district-level-freight/` (list/create/edit/view + an
import dialog with preview -> commit), mirroring `features/rate-master/`'s own bespoke
shape rather than the shared twelve-master-list architecture — same reasoning Rate itself
wasn't folded into that architecture (multiple lookups + a fixed rate grid, no single
parent). New nav leaf under the existing "Rate Master" section.

**Verified live** via curl on a throwaway `:8082` (`:8100`/`:4200` untouched) against real
`courier_db`, `V54` applied (schema now at 54): real create/duplicate-409/activate/
deactivate/delete-then-404 all confirmed; `BRANCH_MANAGER` correctly 403'd on write, 200'd
on read; a real `.xlsx` built for this test round-tripped through both preview and commit —
new-district row `CREATED`, existing combination `UPDATED` (rates actually changed,
confirmed via a follow-up read), blank row and ODA note row both silently ignored, an
unknown branch name correctly reported as a row-level `ERROR`. **Then a full Chrome
click-through** on a throwaway `:4300` (`SPRING_PROFILES_ACTIVE=test` on the backend —
its CORS allowlist includes `:4300`, the default profile's doesn't; `:8100`/`:4200`
untouched): nav leaf, list/filters/create/view/edit/delete/deactivate and the Excel import
dialog's preview -> commit all clicked through against real data. **One real bug found and
fixed live**: the import dialog's content div was wider than Angular Material's own
`.mdc-dialog__surface` default `max-width:560px`, silently clipping its action buttons with
no visible scrollbar (confirmed via `getComputedStyle`, not guessed) — fixed by shrinking
the dialog to fit within 560px and making the results table wrap instead of forcing width;
re-verified live post-fix. Test fixtures left in `courier_db` per
`[[keep-test-data-in-dev-db]]`. `mvn test` 887 -> 904 (17 new), `tsc --noEmit`/`ng build
--configuration production` clean, `ng test` 147/148 (the one failure, `reports-dashboard`,
pre-existing and unrelated). Full detail in `CHANGELOG.md` Unreleased 2026-09-02.

Previously current:

`0.32.4` — **Pincode Branch Mapping: new "Pincode Branch Mapping" menu under Masters, map
a branch to many pincodes at once.** Direct request. A pincode is served by exactly one
branch per company — `branch_pincode_mapping` (`V53`) enforces it with `UNIQUE (company_id,
pincode_id)` **alone**, not the `(branch_id, pincode_id)` pair. Company-owned for real
(unlike `master_pincode_areas`/V52) since Branch is a genuine per-company entity;
`BranchPincodeMappingService` (new, in `com.courier.modules.company`) crosses into
`GlobalMasters.PLATFORM_COMPANY_ID` only to look up the (global) `Pincode` row itself.
Three endpoints nested onto `BranchController` (`GET/POST/DELETE /branches/{id}/pincodes`,
same shape as `assign-manager`/`assign-users`); `POST` is a batch add — pincodes already on
this branch come back in `alreadyMapped`, pincodes owned by a *different* branch come back
in `conflicts` naming it, never silently moved. `COMPANY_ADMIN`-only writes, branch-
visibility-scoped reads (reuses `BranchService.getById`'s own rule). New nav leaf under
Masters, new standalone `features/branch/branch-pincode-mapping.ts` (branch picker +
debounced pincode search/tick-list + mapped table with Remove). `mvn test` 876 → 882 (6
new). **Verified live** on a throwaway `:8082` (`:8100`/`:4200` untouched) against real
`courier_db`, `V53` applied: real add/already-mapped/conflict/remove all confirmed against
real branches and pincodes, `BRANCH_MANAGER` correctly 404'd (visibility) / 403'd (write).
Full detail in `CHANGELOG.md` Unreleased 2026-09-02.

Previously current:

`0.32.3` — **Pincode create form: full area preview, auto-filled post office, Placement/
zone dropped, list sorted by code.** Same-day UI polish following user's own live testing.
`PincodeServiceImpl.lookupPostalArea` now resolves every postal match (not just the first)
via `GeographyAutoResolver` — `PincodeAreaLookupResponse` gained an `areas` preview list,
the exact rows `syncAreas` would link once saved. Pincode's `areaId` field stays a real,
required form control (payload/validity still depend on it) but is excluded from both
`MasterForm`'s and `MasterView`'s rendered groups for `key === 'pincodes'` — no "Placement"
card, no manual Area picker, Area is set only by auto-fetch now. `zone` dropped from
pincode's `fields[]` entirely. New `MasterDefinition.defaultSort`, set for pincodes only,
applied before the operator ever clicks a column header. New "Areas served by this pincode"
preview card in the create form itself (no ODA controls — nothing to toggle pre-save).
**Real bug found and fixed via live re-testing**: post-office auto-fill's first cut guarded
on "only fill if empty," which let one auto-fill permanently block a later, correct one —
reproduced by retyping a pincode quickly. Fixed by tracking Angular's `pristine` flag
instead (programmatic `setValue` never dirties a control; only a real keystroke does),
correctly distinguishing "we auto-filled this" from "the operator typed their own label."
**Verified live** on real `:4200`/`:8100` (backend rebuilt+restarted once, JWT rotated
again, re-login needed): list opens sorted by code; a 9-post-office pincode's create-form
preview matched its post-save detail-page card exactly, row for row; the retype race that
broke the first auto-fill attempt re-tested clean after the fix. `mvn test`/`tsc --noEmit`/
`ng test`/`ng build --configuration production` all clean. Full detail in `CHANGELOG.md`
Unreleased 2026-09-02.

Previously current:

`0.32.2` — **Pincode-Area links: every Area a pincode names, ODA + amount per area.**
Direct follow-up: "some pincode have multiple city or area name" — a pincode routed to one
Area, but India Post's directory routinely names several real post offices per code, and
0.32.0's ODA toggle was per-pincode when it genuinely varies per locality. New
`master_pincode_areas` (`V52`) links a pincode to every Area its postal record names (one
`is_primary`), each with its own `oda_applicable`/`oda_amount` (defaults `250.00` the moment
ODA turns on with no amount given). New `PincodeAreaService` (`list`/`updateOda` controller-
facing; `syncAreas` internal — best-effort, never throws, called from `PincodeServiceImpl
.create`/`update`). New `GET/PATCH /global-masters/pincodes/{id}/areas[/{linkId}]`. Additive
— `master_pincodes.area_id`/`oda_applicable` unchanged, still drive the create form/list
column. Frontend: new `PincodeAreasCard`, mounted only on the pincode detail page (outside
the twelve-list shared architecture — a per-row editable sub-list isn't a flat field
descriptor). **Verified live** on real `:4200`/`:8100` (rebuilt+restarted for `V52`; the
restart rotated `JWT_SECRET` again, re-login needed): real pincode `416013` (3 upstream post
offices) → primary + 2 alternates auto-linked; ODA toggle-on defaulted `250.00`, custom
amount `400` accepted, toggle-off cleared it; `BRANCH_MANAGER` read-only confirmed (200 GET,
403 PATCH); clicked through the actual UI card end to end. `mvn test`/`tsc --noEmit`/`ng
test`/`ng build --configuration production` all clean. Full detail in `CHANGELOG.md`
Unreleased 2026-09-02.

Previously current:

`0.32.1` — **Pincode bulk-import (numeric ranges), seeded 152 real Maharashtra pincodes.**
Direct follow-up: "add all maharashtra all pincode with all area." Scoped via
`AskUserQuestion` — full Maharashtra brute force is ~45,000 candidate codes for ~7,000 real
ones (no bulk-by-state endpoint exists, only per-pincode lookup); chose a representative
sample now plus a real reusable endpoint over a one-off script. New `POST
/api/v1/global-masters/pincodes/bulk-import` (`PincodeBulkImportService`), same `WRITE`
audience as `create` — this is the endpoint `MASTER_DATA_IMPORT` has sat in the permission
catalogue for with nothing behind it since Master Data shipped. Probes each range through the
0.32.0 lookup pipeline and calls `PincodeService.create` per match (a cross-bean call, so
`create`'s own `@Transactional` gives every row its own transaction, not one lock held for
the whole run); an already-on-file code is inferred from `create`'s own
`DuplicateResourceException` and skipped — safe to re-run over an overlapping range.
**Verified live**: seven Mumbai/Pune/Nagpur/Nashik/Aurangabad/Kolhapur/Solapur ranges (180
candidates) → 152 created (real India Post locality names, 157 distinct Areas auto-created),
0 failed, ~77s; re-running 100 of them → 0 created, 85 correctly skipped, 0 duplicates;
`BRANCH_MANAGER` 403'd; actual Pincodes list page rendered all 158 rows. `mvn test` green, no
new unit tests (verify-live, same precedent as 0.32.0). Full detail in `CHANGELOG.md`
Unreleased 2026-09-02.

Previously current:

`0.32.0` — **Pincode master: auto-fetch Area from a real postal directory (India Post,
`api.postalpincode.in`), plus an ODA (Out-of-Delivery-Area) toggle.** Both inside the existing
Pincode Master create/edit flow. New `master_pincodes.oda_applicable` (`V51`, default false,
independent of `serviceable`). New `GeographyAutoResolver` finds-or-creates the missing
State/District/City/Area chain by name (direct repository access, not through the
`SUPER_ADMIN`-only `CountryService`/etc — same `COMPANY_ADMIN`-may-write reasoning
`PincodeServiceImpl.create` already documents for Pincode, extended one level up); new `GET
/global-masters/pincodes/lookup/{code}`, same write gate as `create`. Frontend debounces the
Pincode field and auto-selects the resolved Area with a "Matched to X, Y, Z" hint; a no-match
falls back to the existing manual picker. **Real bug found and fixed via live verification**:
the JDK `HttpClient`'s default User-Agent is silently connection-reset by this upstream — fixed
with an explicit `User-Agent` header (confirmed via `curl` reproduction, not guessed; an HTTP/1.1
pin tried first was ruled out and reverted). **Verified live** on throwaway `:8082`/`:4200` (the
real backend was not running; confirmed free before use, both stopped after) against real
`courier_db`, `V51` applied: real pincode `411001` resolved to `C D A (O), Pune City East, Pune,
Maharashtra`, auto-creating that chain; idempotent on a second lookup; `BRANCH_MANAGER` 403'd on
lookup; created a real pincode through the actual UI end to end with ODA on. `mvn test`/`tsc
--noEmit`/`ng build --configuration production` all clean. Full detail in `CHANGELOG.md`
Unreleased 2026-09-02.

Previously current:

`0.31.1` — **Branch dashboard KPIs/charts/recent-activity fixed to be branch-wide, not
company-wide.** Direct bug report: "dashboard count wrong for branch it showing all branches
data" for This Month's Bookings/Collection, Shipment Trend, Delivery Performance, Recent
Activity, Recent Shipment. `DashboardServiceImpl.summary()`'s 2026-08-17 ISSUE-001 fix made
every query explicit-`companyId`-scoped but never added a further branch predicate for a
caller with an own branch wallet (only `pendingDelivery`/`branchOverview` were actually
branch-scoped) — the KPI/chart/recent-activity block ran the same company-wide query for
every caller. Fixed by branching `summary()`/`charts()` on `ownBranchId` (computed once, up
front) alongside the existing `crossTenant` branch: new `bookingBranchId`-scoped
`ShipmentRepository`/`ShipmentChargeRepository` methods (mirroring the company-scoped ones
one scope narrower), a `ShipmentStatusHistory.branchId`-scoped recent-deliveries query, and
recent wallet activity via the caller's own `walletId` (reused existing
`WalletTransactionRepository.findRecent`). `totalShipments`/`totalRevenue` deliberately left
company-wide — no branch-scoped profile's tile set (`dashboard.roles.ts`) shows either. `mvn
test` 871 → 876 (extended `DashboardServiceImplTest`'s `BRANCH_MANAGER` case to assert the
branch-scoped methods are hit and the company-wide ones are never reached). **Verified live**
on throwaway `:8082` (`:8100`/`:4200` untouched) against real `courier_db` as `pune@gmail.com`
(`BRANCH_MANAGER`): dashboard returned correctly, recent shipments/Shipment Trend matched this
branch's own real bookings against a direct DB check (39 company-wide, 29 Pune's own);
`first.admin@gmail.com` (`COMPANY_ADMIN`, same company) confirmed unaffected — `companyOverview`
present, no `branchOverview` key. Full detail in `CHANGELOG.md` Unreleased 2026-09-02.

Previously current:

`0.31.0` — **Communication Center module, COMPLETE end-to-end.** Direct full-spec request.
New package `com.courier.modules.communication`, migration `V50`. Event-driven multi-channel
(WhatsApp/SMS/Email) customer notifications: business modules never send messages directly —
`ShipmentServiceImpl` (still the only writer of Shipment Booking/Movement state) publishes six
new plain-scalar `ShipmentEvent` records (`Booked`/`Dispatched`/`ReceivedAtBranch`/
`OutForDelivery`/`Delivered`/`Cancelled`) at its six existing call sites (create/cancel/
transitionToDispatched/scanOneIn's final-destination branch/assignOneOutForDelivery/deliver);
a new `ShipmentCommunicationListener` (`AFTER_COMMIT`+`REQUIRES_NEW`, same discipline
`ShipmentBookingWalletListener` already set) is the only place a shipment event turns into a
communication attempt.

**Flow, as code**: `CommunicationOrchestrator.handle` finds enabled channels -> loads the
active template -> queues one `communication_log` row per channel (`PENDING`, or `CANCELLED`
with a stated reason: channel disabled, customer opted out, no active template, no address on
file) — a fast DB insert only, never a network call on the listener thread. A new
`CommunicationDispatchJob` (`@Scheduled`, this codebase's outbox-plus-sweep answer to "use
Kafka if available, otherwise an event abstraction ready for it" — no Kafka dependency exists
anywhere in this repo) picks up due rows cross-tenant (same `CompanyContext`-unbound sweep
shape `TicketSlaSweepJob`/`ShipmentSlaSweepJob` already use) and `CommunicationSendService`
renders the template (`{{customerName}}`/`companyName`/`shipmentNumber`/`trackingNumber`/
`pickupLocation`/`deliveryLocation`/`amount`/`deliveryDate`/`receiverName`/`trackingUrl`/
`podUrl`) and calls the right provider.

**Two deliberately separate on/off switches**, resolving a real contradiction in the brief's
own DB-schema-vs-Default-Events sections: `communication_setting.enabled` is the channel-level
master switch per company (WhatsApp/SMS/Email on at all); `communication_template.status`
(`ACTIVE`/`INACTIVE`) is the actual per-event-per-channel switch the brief's "Company Admin can
enable/disable each channel per event" describes. The four default events (`SHIPMENT_BOOKED`/
`SHIPMENT_DISPATCHED`/`OUT_FOR_DELIVERY`/`SHIPMENT_DELIVERED`) x three channels seed lazily
(`CommunicationTemplateServiceImpl.seedDefaultsIfEmpty`, get-or-create like `CompanySettings`)
the first time a company's templates are read; `SHIPMENT_RECEIVED`/`SHIPMENT_CANCELLED` and the
two RTO events exist for a Company Admin to create manually.

**Providers**: `WhatsAppProvider`/`SmsProvider`/`EmailProvider` interfaces, each with a
`LogOnly*` default (no dev-environment vendor account exists — same accepted-gap class as
auth's own `LogOnlyNotificationSender`) and a real implementation gated by an explicit
`app.communication.<channel>.enabled` property pair (`@ConditionalOnProperty`, no
`@ConditionalOnMissingBean`, mirrors `PaymentGatewayConfig`'s own reasoning exactly):
`MetaWhatsAppProvider` (Meta Cloud API, plain `RestClient`, no SDK, approved-template-only
sends), `GenericHttpSmsProvider` (POSTs to whatever `apiUrl` a company configures — "do not
hardcode provider" taken literally), `SmtpEmailProvider` (new `spring-boot-starter-mail`
dependency, platform-level `spring.mail.*`, a company only sets its own from-name/from-email
identity — SMTP itself isn't a per-company secret here, unlike WhatsApp/SMS credentials which
are genuinely per-company and live encrypted in `communication_setting.secret_encrypted` via
the same `EncryptedStringConverter` `CompanyRazorpayConfig` (V46) already uses).

**RTO_INITIATED/RTO_DELIVERED are declared, never published** — this codebase has no
return-to-origin flow (`ShipmentStatus.RETURNED` is a generic terminal state nothing writes
yet, per its own doc). A future RTO module can start publishing into these two rows with zero
schema/enum change here — flagged, not guessed.

**Backend**: `communication_template`/`communication_setting`/`communication_log` (all
company-owned, the project's usual shape), `customers` gained `whatsapp_enabled`/
`sms_enabled`/`email_enabled` (default `TRUE`, opt-out not opt-in) threaded through
`CreateCustomerCommand`/`UpdateCustomerCommand`/both DTOs/the mapper/`CustomerServiceImpl`.
`ShipmentDirectoryPort` (owned by `communication`, implemented by `shipment.infrastructure
.CommunicationShipmentDirectoryAdapter`) goes straight to repositories, never a
`@PreAuthorize`-guarded service method — the dispatch job's scheduler thread carries no
authenticated caller, the same reason `TicketDirectory`/`AuthBranchDirectory` do the same;
sender/receiver `Customer` rows are looked up (never created) by exact mobile match, reusing
the row `ShipmentServiceImpl.create`'s own `findOrCreateForBooking` call already wrote.
14 endpoints across four controllers (templates/settings/logs/dashboard). RBAC role-based like
every module since Ticket Support (no new `PermissionModule`/`PermissionAction` rows) —
settings/template writes `COMPANY_ADMIN`-only, dashboard/logs/retry `COMPANY_ADMIN`+
`BRANCH_MANAGER`. 36 new backend unit tests (`mvn test` 835 -> 871), covering Success/Failure/
Retry/Disabled-Channel/Customer-Preference/Duplicate-Event/Company-Isolation per the brief's
own test list.

**Frontend**: `features/communication/` — Dashboard (today's Sent/Delivered/Failed/Pending,
folding `DELIVERED` into `Sent` since a delivered message was necessarily sent first), Channel
Settings (one card per channel, secrets never round-tripped — blank keeps the stored one),
Templates (list + edit dialog with Enable/Disable, variable-insert chips, live Preview against
synthetic sample data), Logs (filter/paginate/Retry Failed). New `ShipmentCommunicationCard`
embedded in Shipment Details ("SHIPMENT_BOOKED ✓ WhatsApp Sent ✓ SMS Sent ✗ Email Sent" per the
brief's own example). Customer create/edit gained a "Communication Preferences" card
(`mat-checkbox` x3, same pattern `module-permission-card.ts` already uses). New nav section
"Communication Center", `COMMUNICATION_READERS`/`COMMUNICATION_ADMIN` role gates mirroring the
backend split exactly. 11 new frontend tests (`ng test` 134 -> 145, the one pre-existing
`reports-dashboard` nav failure untouched and unrelated), `tsc --noEmit`/`ng build
--configuration production` both clean.

**Verified fully live** on a throwaway `:8083` (`:8100`/`:4200` untouched throughout — a
concurrent session's own `:8082`/`:4300` pair was also live and untouched) against real
`courier_db`, `V50` applied cleanly (schema now at 50): settings/templates lazy-seed exactly
3/12 rows; booked a fresh test shipment (`PUNE-000019`, own fixture, no existing row touched)
and confirmed the full pipeline end to end — 3 `communication_log` rows queued on
`SHIPMENT_BOOKED`, WhatsApp/SMS picked up by the dispatch sweep and marked `SENT` with a
synthetic `providerMessageId` within one sweep interval, Email correctly `CANCELLED` ("No
EMAIL address on file"); dashboard aggregation matched exactly; cancelling that same shipment
correctly queued `SHIPMENT_CANCELLED` rows `CANCELLED` with "No active template" (proving the
seed-only-four-events design live, not just in a unit test); `test-connection` correctly
reported missing WhatsApp credentials; a `BRANCH_MANAGER` token correctly 403'd on
`PUT /communication/settings/WHATSAPP` (`COMPANY_ADMIN`-only); template preview rendered
correctly against synthetic data; the auto-created test `Customer` row carried the expected
`whatsappEnabled=smsEnabled=emailEnabled=true` defaults. **Not verified live**: a genuine
`FAILED`/retry cycle (no real vendor credentials in this dev environment to force a provider
failure) and the `DELIVERED` terminal status (no provider delivery-receipt webhook exists yet
for any channel) — both covered by unit tests instead
(`CommunicationSendServiceImplTest`/`CommunicationLogServiceImplTest`), not fabricated live.

**Same-day "test it live" follow-up**: a full Chrome click-through (throwaway `:8083`/`:4301`,
real `:8100`/`:4200` and the concurrent session's `:8082`/`:4300` both untouched) as
`first.admin@gmail.com` found and fixed two real UI bugs: (1) Chrome's autofill overwrote
Channel Settings' Business Account ID/Sender ID/Access Token/API Key fields with the signed-in
admin's own saved email/password (`autocomplete="off"` alone doesn't stop Chrome once a
`type="password"` field makes it treat the card as a login form) — fixed with
`autocomplete="new-password"` on the secret field; (2) the Customer form's sticky action bar
painted directly over the new Communication Preferences checkboxes once that card pushed the
form's total height into the exact band where the bar's pinned position overlaps normal-flow
content — real DOM elements, correct colors, simply hidden underneath an opaque sibling
(confirmed via `getBoundingClientRect()` overlap, not guessed) — fixed with a structural
`padding-bottom` on the form container. Every other page/action clicked through clean:
Dashboard, Channel Settings save/test-connection, Templates list/edit/preview,
Logs+filters, the Shipment Details Communication tab (rendered the brief's own worked
example exactly), and a real Customer create persisting a deliberately-unchecked preference.
Full detail in `CHANGELOG.md` 0.31.0 and `MEMORY/modules/communication.md`.

**Same-day bugfix**: Consignment print's Customer/Office copy was printing a literal
"ToPay" label on Paid orders (0.30.2's own per-copy amount rules had this backwards).
`consignment-print.util.ts`'s `'hidden'` amount mode renamed to `'paid'` — now shows
"Total Paid" + the real amount instead. Driver/Delivery copy logic untouched. Not
verified live — code fix only, `tsc --noEmit` clean. Full detail in `CHANGELOG.md`
Unreleased 2026-09-02.

Previously current:

`0.30.3` — **Manual ODA Charge override at booking.** ODA Charge row moved below Other
Charges in the booking summary and made editable — was a read-only echo of the Pricing
Engine's own `chargeBreakup.odaCharge`. `ShipmentServiceImpl.copyCharge` treats a typed value
as an override (not an addition, unlike `otherCharges`), applying GST only to the difference
from the engine's own figure at the booking branch's GST%. (Concurrent-session entry,
reconstructed from `CHANGELOG.md` — this file had not been updated for 0.30.2/0.30.3 when this
task started.)

`0.30.2` — **Consignment print: 4 copies with per-copy amount rules.** Customer/Office/
Driver/Delivery copies (was Original/Office), amount block varies by copy + payment mode.
(Concurrent-session entry, reconstructed from `CHANGELOG.md` — see above.)

Previously current:

`0.30.1` — **POD Auto Verification module, COMPLETE end-to-end.** Direct full-spec
request. New package `com.courier.modules.pod`, migrations `V48`/`V49`. AI-scored gate in
front of the existing delivery close-out: `OUT_FOR_DELIVERY` -> upload POD -> AI
Verification -> `PASS` -> Complete Delivery, `REVIEW` -> manual approve/reject, `FAIL` ->
upload a new POD. **AI never itself updates a shipment's status** —
`ShipmentServiceImpl.deliver()` stays the only code path that ever writes `DELIVERED`,
completely untouched; this module only ever writes `pod_verification` rows. Full design
writeup, including the honest "why a heuristic, not a real AI vendor" reasoning, in
`MEMORY/modules/pod-verification.md`.

**Backend**: `PodVerification`/`PodVerificationStatus` (`PASS`/`REVIEW`/`FAIL`, no
`PENDING` — `verify()` is synchronous), `PodVerificationService`/`Impl`, and a
`PodVerificationProvider` abstraction (the brief's own AI-provider-abstraction
requirement) with one implementation, `HeuristicPodVerificationProvider` — a deterministic
local scorer, not a trained model, since no AI/vision vendor credential exists in this dev
environment (same accepted-gap class as `NotificationPort`/SMTP). It decodes the photo via
the JDK's own `ImageIO` and scores darkness/blur/resolution, checks signature presence,
cross-checks the claimed AWB against this platform's own DB record for the shipment (real
ground truth, no OCR needed), and flags duplicates via a SHA-256 photo-hash comparison.
Thresholds are configurable (`POD_AUTO_VERIFY_THRESHOLD`/`POD_MANUAL_REVIEW_THRESHOLD`,
defaults 85/60), never hardcoded. `pod.ai.enabled=false` swaps in
`UnavailablePodVerificationProvider`, exercising the brief's own "provider unavailable ->
REVIEW, never a silent PASS" rule for real. RBAC is role-based, same posture as every
module since Ticket Support — no new `PermissionModule`/catalogue rows, matching
Ticket/Follow-up/Vehicle-fleet's own precedent. New `GET /api/v1/pod/pending-review`
(beyond the brief's own three-endpoint list — without it a reviewer has no worklist).

**A real, live-found platform bug fixed in passing**: `GlobalExceptionHandler` had no
handler for a missing multipart part — calling `verify()` with no photo 500'd instead of
400ing cleanly. Pre-existing gap in shared infrastructure (the original POD-upload
endpoint carried the same latent gap since 0.17.9, just never tripped). Fixed.

**Frontend**: `features/shipment-movement/delivery.ts` reworked into a capture -> AI
verify -> decision flow; new `features/shipment-movement/pod-review.ts` (the Manual
Review screen — worklist, photo/signature thumbnails, AI result, Approve/Reject). New nav
leaf "POD Review" under Operations, `COMPANY_ADMIN`/`BRANCH_MANAGER` only.

**Verified live** on real `courier_db` via throwaway `:8082`/`:4300` (`:8100`/`:4200`, run
by a concurrent session throughout this task, untouched): missing-photo 400s cleanly now,
wrong-status refused correctly, a real `OUT_FOR_DELIVERY` fixture ran the full pipeline
(status/duplicate/AI-analysis all executed) and stopped exactly at the pre-existing
"no storage backend configured" gap — proving the AI step ran, not that it was skipped —
404/isolation/empty-list all confirmed correct. **Not verified live**: the actual
PASS/REVIEW/FAIL happy path (needs S3, not configured in this dev environment — an
accepted pre-existing gap, not new here) and the Delivery/POD-Review UI click paths
specifically (no `OUT_FOR_DELIVERY` fixture existed at the logged-in branch this session
to click through — the "POD Review" nav leaf and its empty state were confirmed rendering
live with no console errors). `mvn test` 813 → 835, `tsc --noEmit`/`ng build` clean. Full
detail in `CHANGELOG.md` 0.30.1 and `MEMORY/modules/pod-verification.md`.

**Concurrent-session note**: this working tree had E-Way Bill Management (`0.30.0`, below
— that session's own entry references this module too, see its own concurrent-session
note) and a Razorpay-per-company-config feature (`V46`) present throughout, neither
touched by this task. One of those sessions independently fixed a real schema bug in
*this* module mid-task (`V48`'s `pod_hash CHAR(64)` vs. the entity's default `VARCHAR`
mapping) via a forward-only `V49`, found already applied to the real dev DB by the time
this task's own live-boot verification ran.

Previously current:

`0.30.0` — **E-Way Bill Management, COMPLETE end-to-end.** New package
`com.courier.modules.ewaybill`, migration `V47`, full-spec direct request. Business rule:
invoice value over the company's own configurable threshold
(`CompanySettings.ewayBillMandatoryValue`, default 50000.00) makes an E-Way Bill mandatory
before AWB generation; at or under it, optional.

**Integration point, decided before writing code**: the brief's flow places the E-Way Bill
check before Pricing/AWB Generation inside the booking flow, but this codebase mints the
AWB synchronously inside `ShipmentServiceImpl.create()` — one `@Transactional` method, no
separate later "AWB Generation" step to intercept. So the gate is enforced **inline**:
`CreateShipmentCommand`/`UpdateShipmentCommand` gained `invoiceValue` + an optional
`EwayBillDataCommand`; `EwayBillService.enforceBookingRequirement` throws (with the brief's
own exact wording, threshold interpolated) **before** the shipment is built or an AWB
minted whenever required-but-missing-or-invalid — the whole transaction never starts
writing, so this is a real backend guarantee, not a frontend-trusted checkbox. Only after
the shipment has an id does `upsertForShipment` create/update the `EwayBill` row.
`update()` runs the identical gate, so raising `invoiceValue` past the threshold on an edit
can't leave a shipment `BOOKED` without a validated E-Way Bill either.

**Backend**: `EwayBill` (own table, own lifecycle, no unique `(company, shipment)` — a
shipment may carry more than one row over time, a cancelled one re-issued),
`EwayBillStatus` (`NOT_REQUIRED→REQUIRED→PENDING→UPLOADED→VALIDATED/INVALID→EXPIRED→
CANCELLED`, `CANCELLED` terminal). `EwayBillProvider`/`LocalEwayBillProvider` — the "future
ready" seam the brief asked for: local field/format checks only (12-digit number shape,
non-blank invoice fields, validity not expired), never a call to the government portal, per
the brief's own "don't implement an external API unless already configured." A real
integration becomes a second `EwayBillProvider` implementation with zero change to the
caller. Standalone `POST/PUT/GET /eway-bills`, `.../validate`, `.../upload`, `.../cancel`
for managing an E-Way Bill after booking. `PermissionModule.EWAY_BILL` (8 rights; two new
`PermissionAction`s, `VALIDATE`/`CANCEL`; catalogue 223 → 231) — the brief's `EWAY_BILL_VIEW`
seeded as `EWAY_BILL_READ` since this catalogue has never used a `_VIEW` code anywhere.
`shipments` gained `invoiceValue`/`ewayBillRequired` (frozen at booking time, never
recomputed against a later threshold change). `company_settings_config` gained a new
`ewayBill` section (`PATCH /company-settings/eway-bill`).

**A real bug caught by its own unit test, not live boot**: the first draft of
`upsertForShipment` reused the same "newest row, falling back to a cancelled one" lookup
`findLatestForShipment` (reads) uses — which tried to resurrect a `CANCELLED` row straight
to `VALIDATED` on a write, and `EwayBillStatus`'s own illegal-transition guard correctly
threw. Fixed by giving the write path its own non-cancelled-only lookup — reads and writes
now genuinely differ, as they must for "a cancelled E-Way Bill is reissued, never reused."

**Frontend**: `shipment-create.ts` gained an "E-Way Bill" card (Invoice Value, an
auto-opening Optional/Mandatory chip, E-Way Bill Number/Invoice Number/Invoice Date/
Vehicle Number/Validity/document picker, Add/Remove) and a matching Booking Summary
sidebar line — `ewayBillReason()` (plain method, not `computed()`, this file's own
established staleness-avoidance pattern) disables Book Shipment with the reason shown,
**UX only**, the backend re-enforces regardless. The document itself uploads via
`POST /eway-bills/{id}/upload` after `book()` succeeds, using the new nested
`ShipmentResponse.ewayBill.id` — no second round trip needed to discover it.
`shipment-view.ts` gained an "E-Way Bill" card (Required/Invoice Value/Number/Status/
Validity/Document) plus Validate/Upload/Cancel actions against the new
`features/shipment/eway-bill.service.ts`. No standalone E-Way Bill list page — not asked
for by the brief's own Frontend section, which only covers booking-flow integration and
shipment-detail display.

**Verification**: `mvn test` 791 → 813 at the time this task's own suite ran (two new
files, `EwayBillStatusTest` + `EwayBillServiceImplTest`'s 20 cases including the
cancel-reissue bug above), `DefaultPermissionCatalogTest` 223 → 231.
`tsc --noEmit -p tsconfig.app.json`/`ng build --configuration production` both clean,
`ng test` 133/134 (the one failure, `navigation.config.spec.ts` expecting a
`reports-dashboard` nav node, confirmed pre-existing and unrelated via `git log` — this
task never touched `navigation.config.ts`). **Not verified live** — no MySQL boot or
browser click-through this session; `V47` not yet applied against a real database.

**Concurrent-session note**: this task's working tree had at least two other sessions
active in the exact same `shipment` module files throughout — a "manual shipment number"
feature (`CreateShipmentRequest.manualShipmentNumber`) and a "POD Auto Verification"
feature (`api-endpoints.ts`'s new `pod` entry, `ShipmentService.attachPodAsset`) both
landed mid-task. Several `mvn test`/`tsc` runs briefly failed on a not-yet-finished
concurrent edit and self-resolved a short wait later, same pattern 0.28.5/0.28.6 already
documented. Final state has all three features' code and tests coexisting cleanly — `mvn
test` climbed to 835/835 by the last run, reflecting the other sessions' own added
coverage, not just this task's. Full detail in `CHANGELOG.md` 0.30.0 and
`MEMORY/modules/eway-bill.md`.

Previously current:

`0.29.2` — **Four new reports: Finance, Branch Performance, Customer, Shipment
Exception**, direct request ("create important reports"), scoped via
`AskUserQuestion` to exactly these four. Two were already promised in
`navigation.config.ts` — "Finance Reports (Soon)" and "Branch Reports (Soon)" had
sat unbuilt since the Reports section was first created. Every report reuses
existing search/aggregate endpoints wherever one already answered the question
(same "unpaged aggregate, single call" shape Booking/Commission Report use), adding
only the minimal backend an existing endpoint genuinely couldn't answer — no DB
migration.

**Backend**: new `GET /shipments/branch-performance` (`ShipmentService
.branchPerformance`, mirrors `commissionSummary`'s in-memory grouping-by-branch
shape, adds a group-by-status pass for delivered/in-transit/returned/cancelled
counts) and new `GET /branch-wallet/company-summary` (`WalletService
.companySummary`, the first cross-branch wallet read — every other `WalletService`
method resolves to exactly one branch. `summarise`'s body factored into
`summariseResolved(branchId, companyId)`, looped over a new
`BranchDirectoryPort.listBranches(companyId)` backed by the already-existing
`BranchRepository.findAllByCompanyIdAndStatusOrderByBranchCodeAsc`. Restricted to
`COMPANY_ADMIN`/`FINANCE_USER`, the same two roles the nav item was already gated
on). Customer Report and Shipment Exception Report needed **no backend change** —
`GET /customers` and `GET /shipments?status=RETURNED,CANCELLED` already answer
them; deliberately did not fabricate an SLA-breach join (Ticket Support already
surfaces those as tickets).

**Frontend**: four new `features/reports/` pages in the existing five reports' own
shape. `FinanceReport`/`BranchReport` branch on `AuthService.user()?.branchId` —
branch-scoped sees their own branch via existing endpoints, company-wide sees the
two new aggregates across every branch. New nav entries `customer-report`/
`exception-report`; "(Soon)" dropped from `finance-reports`/`branch-reports`.

**Verified live** on throwaway `:8082`/`:4300` (`:8100`/`:4200` untouched — the real
dev backend now runs on port `8100`, not `8081`; `[[local-dev-environment]]`
corrected) as `first.admin@gmail.com` (COMPANY_ADMIN) and `pune@gmail.com`
(BRANCH_MANAGER): all four pages render real, cross-consistent data (Branch
Performance's per-branch commission figure matched Finance Report's own branch row
exactly), CSV export confirmed, nav confirmed "(Soon)"-free, branch-scoped role
correctly denied `/reports/finance` (not in the gated role set) and correctly
branch-locked on `/reports/branches`. No console errors. `mvn test` 786/786,
`tsc --noEmit`/`ng build` clean. No new unit tests added for the two aggregate
methods, matching `commissionSummary`/`summaryStats`'s own existing precedent of
verify-live-only. Full detail in `CHANGELOG.md` 0.29.2.

Previously current:

`0.29.1` — **"Login as branch" (COMPANY_ADMIN spoof login, no password)**, direct
request: "add login option for branch same as super admin login to company, now add
functionality as company admin login to branch and do not ask for password."
Deliberately reused 0.28.11's SUPER_ADMIN "login as company" mechanism end to end —
same JWT (`JwtTokenProvider#generateImpersonationAccessToken`, already generic),
same frontend stash/exit/banner infra (`TokenService`/`AuthService.isImpersonating`/
`AdminLayout`'s banner), same 15-minute hard cap, no refresh token. One deliberate
divergence, per the user's own instruction: **no step-up password re-entry** — the
caller is already the company's own admin acting inside their own company, not a
platform role reaching into a foreign tenant.

**Backend**: new `AuthService.impersonateBranch` (`@PreAuthorize("hasRole
('COMPANY_ADMIN')")`) resolves the branch via a new `auth.application.port
.BranchDirectoryPort` (implemented by new `company.infrastructure
.AuthBranchDirectory`, distinct from Finance's own `BranchDirectoryPort`), finds
that branch's real `BRANCH_MANAGER` (new `UserRepository
.findByCompanyIdAndBranchIdAndRoleAndStatus`), mints the token acting as that real
user, audits new `AuditAction.BRANCH_IMPERSONATED`. New `POST /auth/impersonate/
branch/{branchId}`, no request body. **`SecurityConfig` gotcha caught pre-ship**:
the existing `/api/v1/auth/impersonate/**` → `SUPER_ADMIN`-only outer gate would
have covered this new path too (first-match wins) — added a more specific
`/api/v1/auth/impersonate/branch/**` → `COMPANY_ADMIN` rule ahead of it. `mvn test`
782 → 785 (3 new cases).

**Frontend**: `AuthService.impersonateBranch(branchId)` (no password param). Branch
list gained a "Login as Branch" row action (`BranchPerms.impersonate`,
`COMPANY_ADMIN`-only, `ACTIVE` branches only), a plain confirm dialog standing in
for the missing password step-up. **Found and fixed in passing**:
`AdminLayout.exitImpersonation()` unconditionally navigated to `/companies` on Exit
(correct for SUPER_ADMIN, would misroute a COMPANY_ADMIN who has no access to that
route) — now branches on the restored session's own role. `tsc --noEmit`/`ng build`
clean.

**Verified live end to end** on throwaway `:8082`/`:4300` (`:8081`/`:4200`
untouched) as `first.admin@gmail.com` (COMPANY_ADMIN): Branches list → "Login as
Branch" → confirm (no password) → banner + toast + nav genuinely switched to the
branch-scoped operations menu (a real role switch, not a label) → Exit correctly
restored the COMPANY_ADMIN session at `/dashboard`. Confirmed a real
`BRANCH_IMPERSONATED` row in `audit_logs`. Full backend suite green throughout.
Full detail in `CHANGELOG.md` 0.29.1.

Previously current:

`0.29.0` — **Follow-up Management module, COMPLETE end-to-end.** Direct full-spec
request: "Build a Follow-up module for Branch users to track operational tasks
requiring manual action," linkable to Shipment/Customer/Delivery/Payment/Exception/
General, company- and branch-isolated. Asked via `AskUserQuestion` before writing
anything whether to fold this into Ticket Support (the closest existing shape) or
build separately — user chose separate, since a follow-up's due-date/reschedule
semantics and mandatory branch ownership are a different domain from a ticket's SLA/
conversation/escalation and risked corrupting Ticket's own SLA-bucket math.

**Backend**: new `com.courier.modules.followup` (`V44__follow_up.sql`) — `follow_up`
(branch_id mandatory, unlike Ticket's optional relatedBranchId) + one combined
`follow_up_history` timeline table (creation/status/reschedule/assignment/notes, not
Ticket's two separate history tables). `FollowUpStatus` OPEN→IN_PROGRESS/RESCHEDULED
→COMPLETED/CANCELLED, RESCHEDULED reachable only via its own dedicated endpoint,
COMPLETED/CANCELLED terminal ("cannot be edited except through history").
`FollowUpServiceImpl` mirrors `TicketServiceImpl`'s hand-rolled scoping but with
**no SUPER_ADMIN cross-tenant view** — purely company/branch data. Branch isolation
(`resolveBranchForWrite`) and "assignee must belong to the branch"
(`requireAssigneeInBranch`) are both enforced server-side, not just UI-hidden.
Overdue is computed live at read/dashboard time, never stored. **Notifications reuse
Ticket Support's existing infrastructure rather than duplicating it** (explicit spec
instruction) — `Notification` gained a nullable `follow_up_id` alongside `ticket_id`,
`NotificationService.notifyFollowUp(...)`, four new `NotificationType` constants.
New `FollowUpSweepJob` (`@Scheduled`, this codebase's third scheduled job) fires
OVERDUE/DUE_TODAY once each via idempotency flags; URGENT fires on assignment.
Dashboard integration is a **separate self-contained endpoint**
(`GET /follow-ups/dashboard`), not folded into `DashboardSummaryResponse` — zero
changes to `DashboardServiceImpl`. RBAC is role-based, same posture as every module
since Ticket Support (the "authorise on permissions" capstone is still not built).
`mvn test` 761 → 782 (21 new `FollowUpServiceImplTest` cases: CRUD, branch-scoped
create/foreign-branch refusal, assignee-branch validation, stale-version conflict,
illegal/RESCHEDULED-via-status-refused transitions, COMPLETED stamping, reschedule
due-date swap, cross-branch assign refusal, assignment notification, notes, history,
branch/company isolation, assignee-sees-across-branches exception, dashboard bucket
counts).

**Frontend**: `features/follow-up/` — list (filters hydrate from query params so the
dashboard widget's tiles deep-link), create (shipmentId/customerId/branchId query
params, same convention as `ticket-create.ts`), edit (full PUT, 409-reload-on-stale
-version), detail (Assignment/Status/Reschedule cards, all hidden once terminal),
history timeline (copies `TicketConversationTimeline`'s markup). New
`FollowUpWidget` (four clickable Overdue/Urgent/Due Today/Upcoming tiles) mounted on
the Operations Dashboard next to Track Shipment. Cross-page "Create Follow-up" links
added to Shipment Details and Customer Details, next to their existing "Raise
Ticket" links. New nav section "Follow-ups" (order 6.4). `tsc --noEmit -p
tsconfig.app.json` and `ng build` both clean.

**Not verified live** — no MySQL boot or browser check performed this session;
verification stopped at the compile/build/unit-test bar. No frontend `.spec.ts`
files added, matching Ticket Support's own precedent (it has none either despite
`[[frontend-test-runner]]` existing since 2026-07-28). Full detail in `CHANGELOG.md`
0.29.0 and `MEMORY/modules/follow-up.md`.

Previously current:

`0.28.12` — **Dashboard Recent Activity: real backend feed**, direct bug report
("Recent Activity ... dashboard not working"). Root cause: the frontend
(`activity-timeline.ts`/`dashboard.service.ts`/`dashboard.model.ts`) has been
fully wired for `recentActivity` since the initial commit; the backend never
implemented it, so `raw.recentActivity ?? []` always resolved to `[]` — not a
tenant-scoping bug, not OnPush, not a regression, just a missing endpoint field.
New `DashboardActivityResponse` DTO on `DashboardSummaryResponse`;
`DashboardServiceImpl.recentActivity()` merges BOOKING (existing `recent`
shipments + charge join), DELIVERY (new
`ShipmentStatusHistoryRepository.findTop5By(CompanyIdAnd)StatusOrderByChangedAtDesc`
on `DELIVERED`), and WALLET (new
`WalletTransactionRepository.findTop5By(CompanyId)OrderByCreatedAtDesc`, signed
amount) into one 8-row, time-sorted feed — same explicit-companyId /
`CompanyContext.runAs(null, ...)` cross-tenant discipline as every other query
in this method (ISSUE-001). No `SYSTEM`-kind source exists anywhere in the
codebase — omitted, not fabricated. `mvn test` 761/761 (`DashboardServiceImplTest`
extended with 2 new mocks, preserving its own scoped-vs-cross-tenant regression
coverage), `tsc --noEmit` clean, **zero frontend changes needed**. **Verified
live** on a throwaway `:8082` (rebuilt jar; real `:8081`/`:4200` untouched) as
`pune@gmail.com` (BRANCH_MANAGER) against real dev MySQL — 8 real, correctly
time-sorted, correctly-signed activity rows returned (booking, delivery, branch
commission, DRS commission, freight debit for shipments PUNE-000017/000016).
**Not verified live**: the `SUPER_ADMIN` cross-tenant branch (didn't know
`super.admin@gmail.com`'s dev password at the time) — covered by the existing
unit test's cross-tenant assertions instead. Found (not touched) the 0.28.11
impersonation feature already uncommitted and mid-flight in this same working
tree from a concurrent session — including a stale `:8082`/`:4300` pair left
over from its own live-verification pass, killed and cleanly restarted for this
task's own verification. Full detail in `CHANGELOG.md` 0.28.12.

Previously current:

`0.28.11` — **SUPER_ADMIN "Login as Company" (spoof login)**, direct request. Scoped
via `AskUserQuestion` first since this is a real security decision — the codebase
already had a *deliberately restricted* impersonation mechanism
(`CompanyResolutionFilter.resolveForPlatformAdmin`, `X-Company-ID`, `PLATFORM_ADMIN`
only, comment explicitly warns against widening it) that this feature does not
reuse. User's choices (all "recommended"): mint a real new JWT (not the header
trick), act as the company's real `COMPANY_ADMIN` (not a synthetic
still-super-admin token), plus audit log + banner/exit + 15-min hard cap + step-up
password re-entry.

**Backend**: `JwtTokenProvider.generateImpersonationAccessToken` — access-only
(no refresh, hard-expires), carries the real target admin's identity plus
display/audit-only `imp`/`impBy`/`impByEmail` claims. New
`AuthService.impersonateCompany` (`@PreAuthorize("hasRole('SUPER_ADMIN')")`):
step-up password check (throttled, deliberately not wired into account-lock),
`CompanyContext.runAs` to find the target company's real `COMPANY_ADMIN`
(`UserRepository.findByCompanyIdAndRoleAndStatus`, new), mints the token, audits
`AuditAction.COMPANY_IMPERSONATED`. New `POST /auth/impersonate/{companyId}`.
**Real bug avoided**: `CompanyDirectoryPort.findById` actually queries by
`company_id`, not the `companies.id` PK (confirmed against the adapter before
wiring the frontend) — the frontend correctly passes `company.companyId`, a
different field than `CompanyList.open()`'s PK-keyed route uses. `mvn test`
758/758 (+3 new).

**Frontend**: `TokenService.beginImpersonation()`/`restoreStash()` stash the real
tokens and swap in the impersonation access token (no refresh token stashed
alongside it — can't be silently extended). `AuthService.isImpersonating` reads
straight off the current token's own claims — survives a page reload for free.
New `ImpersonateDialog` (password step-up) on the Companies list's new "Login as"
button; `AdminLayout` gained a persistent banner + Exit while active;
`error.interceptor.ts` auto-restores the real session on an impersonation token's
401 instead of the generic `/session-expired` page.

**Verified fully live** on throwaway `:8082`/`:4300` (`:8081`/`:4200` untouched) as
`super.admin@gmail.com`: full login-as → real company-scoped session (nav,
dashboard, header all genuinely switched) → Exit → restored SUPER_ADMIN session;
wrong password correctly rejected; a real `COMPANY_IMPERSONATED` row confirmed
directly in `audit_logs` with both identities. `tsc --noEmit`/`ng build` clean.
**Known trade-off, by design**: not tracked in the device/session list (no
refresh token, nothing to rotate) — it simply expires; accepted per the agreed
safeguards. Full detail in `CHANGELOG.md` 0.28.11.

Previously current:

`0.28.10` — **Consignment print: real "Print LR" click verified live**, direct
follow-up ("verify live", "once") closing 0.28.9's own flagged gap. Clicked the
actual "Print LR" button in the real running app (`:4200`, real session,
`:8081`/`:4200` untouched) against real shipment PUNE-000017.
`printConsignmentCopies()` fires a genuine `window.print()` in a hidden iframe —
blocking/automation-unsafe like `alert()`. Worked around it: a `MutationObserver`
installed before the click patches the new iframe's `contentWindow.print` to a
flag-setter the instant the iframe appears, landing before the production
code's own 50ms auto-print timeout fires. Confirmed `window.print()` was truly
invoked (`__printBlocked === true`), no OS dialog appeared, page stayed
interactive, and the iframe's real client-rendered HTML (the production Angular
bundle's own output, not an offline reproduction) contains both copies with the
correct tracking number, shipment number, sender/receiver, branch labels, and
₹53.10 net amount. Full detail in `CHANGELOG.md` 0.28.10.

Previously current:

`0.28.9` — **Consignment print verified with real shipment data**, direct
follow-up to 0.28.8. `ConsignmentPrintData` was already wired to real shipment
fields via `shipment-view.ts`'s `print()` — the actual gap was verification,
blocked by `printConsignmentCopies()` triggering a real, automation-unsafe
`window.print()`. Split out `renderConsignmentHtml(data, autoPrint = true)`
(returns the same HTML string, auto-print `<script>` conditional) — same
production code path, testable without a live DOM. Compiled the util to
CommonJS with `esbuild` and rendered it in Node with **real data pulled from
the already-running dev backend** (`:8081`, untouched): shipment `PUNE-000017`
/ LR `26080000023`, Pune→Latur, ₹53.10. Screenshotted the static output in
`claude-in-chrome` (served over a throwaway local HTTP server — `file://` is
blocked by the extension) — header, title strip, party block, and
details/charges body all render correctly with real values, both copies.
`POST /auth/login`'s field is `companyCode`, not the `tenantSlug` an older
memory note claimed — see `[[dev-login-credential]]`'s 2026-08-18 entry.
`tsc --noEmit` clean. Full detail in `CHANGELOG.md` 0.28.9.

Previously current:

`0.28.8` — **Consignment/LR print receipt redesigned (SmartPost-style)**, direct
request to match a reference design file. Rewrote only `copy()`'s markup/CSS in
`frontend/src/app/features/shipment/consignment-print.util.ts` — 4-column boxed
header (logo, Booking Branch block, Delivery Branch block, LR-number stamp),
route/date title strip, party block, two-column details/charges body, signature
footer. `ConsignmentPrintData` interface and `shipment-view.ts`'s caller
untouched — no new data plumbing. Reference design's two-company header blocks
don't map to this app's data model (no company/branch address+phone flows into
this util), so mapped honestly to Booking/Delivery branch labels instead of
fabricating addresses; dropped the reference's decorative (non-scannable) QR
placeholder for a real `trackingNumber` stamp box instead. `tsc --noEmit`
clean. Full detail in `CHANGELOG.md` 0.28.8.

Previously current:

`0.28.7` — **Index for `TicketSlaSweepJob`'s sweep query**, direct request: "add
query index for heavy query." `TicketRepository.findAllOpenWithPendingSla()`
(0.28.6, `fixedDelay=5min`) is deliberately cross-tenant — no `company_id`
predicate — so none of `tickets`' existing `(company_id, ...)`-leading indexes
could help it; full table scan every 5 minutes, forever. New
`V43__ticket_sla_sweep_index.sql`: `(sla_resolution_due_at, status)`, leading on
the due-date column since most tickets have it `NULL` (SLA is opt-in per company)
lets InnoDB skip straight to the SLA-tracked rows. Purely additive, no app code
touched — same shape as the same-day perf-testing pass's own `V42` (shipment
search). Applied live to the real dev `courier_db` via a standalone Flyway
invocation (found creds off the running `:8081` process's env — `DB_USERNAME=root`/
`DB_PASSWORD=Root@1234`, not the `application.yml` default `courier`/`courier` —
worth remembering next time raw DB access is needed; `:8081`/`:4200` themselves
untouched, DDL needs no app restart). Schema now at v43. `EXPLAIN` on the sweep's
own predicate confirmed the optimizer switched to `type: range` / `Using index
condition` on the new index.

Previously current:

`0.28.6` — **Ticket Support Phase 2: SLA rules + in-app notifications**, completing the
module the user scoped into two phases back at 0.28.0. Direct request: "complete all
phase."

**Backend** (already-existing, uncommitted work found on this working tree at task
start — the concurrent session referenced throughout 0.28.4/0.28.5's notes; this task
finished it and verified it, rather than rewriting it): `V40__ticket_sla_and_notifications
.sql` — `ticket_sla_rules` (company-owned, unique on `(company_id, priority)`,
`firstResponseMinutes`/`resolutionMinutes`/`active`), four new `tickets` columns
(`sla_first_response_due_at`/`sla_resolution_due_at`/`sla_warning_notified`/
`sla_breach_notified`), and `notifications` (company-owned, `recipientUserId`/`type`/
`title`/`message`/nullable `ticketId`/`isRead`). `TicketServiceImpl.create` resolves the
active `TicketSlaRule` for the ticket's priority and stamps both due dates (null when no
rule exists — SLA is opt-in per company); every lifecycle action (`reply`/`assign`/
`reassign`/`escalate`/`changeStatus`/`changePriority`/`reopen`/`close`) now also fires a
`NotificationService.notify(...)` call, fire-and-forget (try/catch swallows and logs,
never blocks the ticket action on notification failure). New `TicketSlaSweepJob`
(`@Scheduled(fixedDelay=5min)`, this codebase's *second* scheduled job alongside
0.28.5's `ShipmentSlaSweepJob` — noted there, confirmed harmless here) sweeps every open
ticket with a pending SLA cross-company (no `CompanyContext` bound — scheduler thread
starts clean), fires `SLA_WARNING`/`SLA_BREACHED` once each via the idempotency flags.
`TicketServiceImpl.slaBucket()` (static, public so `TicketMapper` can call it) buckets a
ticket into `NO_SLA`/`ON_TRACK`/`WARNING`/`BREACHED`/`MET` — `WARNING` at ≤20% of the
allotted window remaining. New `TicketSlaRuleController` (`/support/sla-rules`,
`COMPANY_ADMIN` writes) and `NotificationController` (`/notifications`, paged list +
unread-count + mark-read + mark-all-read). `TicketDashboardStats` gained `slaBreached`/
`slaPerformance` (0/empty for cross-tenant SUPER_ADMIN — company-scoped SLA targets can't
be meaningfully mixed across tenants).

**Frontend, built this task**: `ticket.model.ts` gained `SlaStatus`, the four SLA fields
on `Ticket`, `SlaRule`/`UpsertSlaRuleRequest`/`AppNotification`(backend
shape)/`NotificationType`, and `slaBreached`/`slaPerformance` on
`TicketDashboardStats`. `ticket.service.ts` gained `slaRules()`/`upsertSlaRule()`/
`setSlaRuleActive()`. New `features/support/ticket-sla-rules.ts` — one row per
`TicketPriority` (four total, upsert-only, no separate create), first-response/
resolution minutes with a human `(1h)`/`(1d)` hint, Save + Activate/Deactivate.
`ticket-list.ts`/`ticket-detail.ts`/`support-dashboard.ts` all gained SLA badges (list
column, header + sidebar due-dates on detail, a new "SLA Breached" stat tile + "SLA
Performance (open tickets)" chart on the dashboard) — `slaTone`/`slaLabel` follow the
same per-file-local-function shape every other status badge in this module already uses,
not a shared component.

**Notification bell wired to the real backend for the first time**: `notification-feed
.service.ts` was previously a permanently-empty stub (documented as such since the
feature didn't exist yet) — rewritten to poll `GET /notifications` every 60s via
`ApiService`, root-scoped so the first poll fires only once `NotificationMenu`
(rendered exclusively inside the authenticated `AdminLayout`) is first injected, never
against an anonymous session. `ApiService.patch` gained the same optional `HttpContext`
parameter `get` already had, so mark-read/mark-all-read can go through
`SILENT_ERRORS` too. `NotificationMenu` clicking an item now marks it read (optimistic,
rolled back on failure) and navigates to `/support/tickets/:id` when the notification
carries a `ticketId`.

Nav: new "SLA Rules" entry under Ticket Support, `COMPANY_ADMIN`/`SUPER_ADMIN` only
(company-scoped decision, not a platform one — distinct from Categories' `SUPER_ADMIN`-
only tier).

**Verified live** on throwaway `:8082`/`:4300` (real `:8081`/`:4200` never touched):
saved a CRITICAL rule (5 min first response / 30 min resolution), raised a CRITICAL
ticket, confirmed the due dates and `ON TRACK` badge matched exactly; self-assigned the
ticket and confirmed the bell showed "A ticket was assigned to you," clicking it marked
it read and navigated correctly; confirmed the SLA Breached tile and SLA Performance
chart render real aggregates. `mvn test` 754/754 (repo-wide — the earlier session's
`CompanySettingsServiceImplTest` breakage referenced in 0.28.5 had already self-resolved
by the time this task ran it), `tsc --noEmit`/`ng build` clean.

Previously current:

`0.28.5` — **Shipment lifecycle SLA auto-raises a ticket**, on direct request: booked
with no loading sheet in 24h, loading sheet with no THC in 24h, THC with no in-scan in
48h, in-scan with no DRS in 12h, DRS with no delivery in 12h — each threshold
company-configurable, defaults matching the user's own numbers exactly. A genuinely new
category from 0.28.4's shortage-ticket (that one fires synchronously off an In Scan
action; this one has no triggering action at all — a shipment can simply sit still — so
it needed this codebase's first `@Scheduled` job.

**Backend**: `V41__shipment_sla_breach_tickets.sql` — six new `company_settings_config`
columns (`sla_breach_ticket_enabled` + five `sla_*_hours`, one per stage), a new global
"SLA Breach" ticket category, new `shipment_sla_breaches` table (one row per shipment
per stage ever breached — the sweep's idempotency record, not a ledger), and
`tickets.created_by_user_id` made nullable (every ticket until now had a human
requester). New `ShipmentSlaSweepJob` (`support.application`, hourly cron) →
`ShipmentSlaSweepService`, which iterates every active company, reads that company's
thresholds, and asks a new `ShipmentSlaPort` (support owns the interface, `shipment
.infrastructure.ShipmentSlaAdapter` supplies it — same seam as `TicketDirectoryPort`/
`company.infrastructure.TicketDirectory`) for shipments past threshold in their current
status. That adapter is one native query on `shipments`/`shipment_status_history`
(`ShipmentRepository.findSlaBreachCandidates`) — a per-row `TIMESTAMPDIFF` against each
shipment's latest status-history row, awkward in JPQL. `TicketDirectoryPort` gained
`listActiveCompanyIds`/`managerOfBranch`/`shipmentSlaSettings` for the same reason.
Auto-raised tickets need no authenticated caller — new `TicketService.raiseSystemTicket`
skips `SecurityUtils.requireCurrentUser()`/`@PreAuthorize` entirely (the sweep runs on a
scheduler thread with no request), assigns to the breaching shipment's
`currentLocationId` branch's own `Branch.managerId` when one exists (the user's own
choice over leaving every auto-ticket unassigned), and leaves `createdByUserId` null.
Company Settings gained a `PATCH /company-settings/sla` section, mirroring every
existing section's merge-only-supplied-fields pattern exactly.

**A real bug caught only by live boot, not by 34 new/updated unit tests**: the migration's
`ALTER TABLE company_settings` targeted the wrong table — `CompanySettings`'s actual
`@Table` name is `company_settings_config` (`company_settings` is a *different*,
pre-existing plan-derived key/value table, per that controller's own doc comment).
Flyway applied the ALTER without complaint (the table exists, just isn't the one Hibernate
maps), and only failed on the next line of context startup: `Schema-validation: missing
column [sla_booking_to_loading_sheet_hours] in table [company_settings_config]`. Exactly
the class of bug `[[local-dev-environment]]` warns unit tests can't catch. Fixed the
migration, then had to manually unwind the half-applied first attempt on the shared dev
DB (drop the six stray columns off the real `company_settings`, clear the failed
`flyway_schema_history` row) before a clean re-run succeeded — `V41` now applies
correctly, `GET /company-settings` serves the new `sla` section with the exact spec
defaults (24/24/48/12/12), and `PATCH .../sla` is confirmed `COMPANY_ADMIN`-only (403 for
a `BRANCH_MANAGER` caller) over real HTTP against `courier_db`.

**A second concurrent-session discovery, not a bug of this task's own**: enabling
`@EnableScheduling` (new `SchedulingConfig`, this codebase's first) also activated an
already-present-but-inert `TicketSlaSweepJob` (`support.application`) — uncommitted work
from the still-in-flight Ticket-priority-SLA/notifications feature (`V40`, distinct from
this one: that's how fast staff must respond to an existing ticket, this is how long a
shipment may sit still), apparently written by a concurrent session on this same working
tree while this task was in progress. It fired harmlessly (no tickets in dev currently
carry `slaResolutionDueAt`) — noted here since the next session touching scheduling
should know two jobs exist, not one, and that this working tree had two people in it
part of this session.

**Known gap, flagged not guessed**: a shipment mid-crossing (`READY_FOR_MANIFEST`) is not
checked — that status means "awaiting the next leg's own loading sheet," which does not
map onto one of the five stages without guessing which leg's clock should be running.
Frontend: `settings-page.ts` gained an "SLA" preview card (same read-only preview every
other section already has — this page has no edit UI for *any* section yet, so this adds
no new gap); `ticket.model.ts`'s `createdByUserId` widened to `string | null` and
`ticket-detail.ts`'s `userLabel`/`resolveMissingUsers` updated to show "System" rather
than crash/blank for the first-ever system-authored ticket. `mvn test` 754/754 (this
task's own share: 3 new `TicketServiceImplTest` cases for `raiseSystemTicket`, 4 new
`ShipmentSlaSweepServiceTest` cases — the total also reflects the concurrent session's
own in-flight work, not only this task's), `tsc --noEmit`/`ng build` clean. Full detail
in `CHANGELOG.md` 0.28.5.

Previously current:

`0.28.4` — **In Scan short-receipt auto-raises a Support ticket.** Direct request:
"when THS create for 10 shipment and only 8 shipment received that time should be
automaticaly ticket generate and visible for company." Hooked into In Scan's own
manifest checklist (the "uncheck if not physically available in THC" flow) rather
than building a new "close manifest" step — the unchecked complement is already the
operator's own explicit "these N are missing" answer. `ShipmentService.inScan` gained
`manifestNumber` (descriptive only) + `missingTrackingNumbers`; a non-empty list
auto-raises a Support ticket (category "Shipment Issue", HIGH priority, related to the
receiving branch) in the same transaction, reported back via new
`BulkMovementResult.shortageTicketNumber`. `in-scan.ts` sends the unchecked tracking
numbers automatically and toasts the raised ticket number — the ticket itself needs no
new UI, it's already visible in the existing Ticket Support list/dashboard (0.28.0).
**Found and fixed in passing, unrelated**: `TicketServiceImplTest` was already broken
in the working tree (an uncommitted, mid-flight SLA/Notification build had grown
`TicketServiceImpl`'s constructor with no test update behind it) — fixed mechanically
so `mvn test` could run at all; the SLA/notification feature itself untouched. `mvn
test` 744/744, `tsc --noEmit`/`ng build` clean. **Not verified live** — no local MySQL
session this task. Full detail in `CHANGELOG.md` 0.28.4.

Previously current:

`0.28.3` — **Mobile/tablet responsive: closed as far as it can be closed this
session.** Direct "do all pending task" continuation, targeting 0.28.2's last open
item. Code-reviewed every `features/support/` page: `support-dashboard.ts` needs no
breakpoint (`auto-fit`/`minmax` grids collapse on their own), `ticket-list.ts` needs
none either (`flex-wrap` on the filter row, `UiTable`'s own `.tbl__wrap { overflow:
auto }` already handles narrow-viewport table scrolling for every page that uses it,
not just this one), and the three two-column pages (`ticket-create.ts`,
`ticket-detail.ts`, `ticket-categories.ts`) all already carry an explicit `@media
(max-width: …)` breakpoint collapsing to one column, the same pattern this codebase
uses everywhere else. Attempted a live visual check anyway (booted `:8082`/`:4300`
again, `resize_window` to 375×800) — **confirmed the exact same
`claude-in-chrome` limitation 0.22.0 already documented**: the call reports success
but the captured screenshot stays full desktop width, so no genuine visual mobile
verification is possible from this side this session. Reported honestly rather than
claimed. This closes the loop on every gap raised across 0.28.0–0.28.3: the two that
remain (attachment upload with no S3, Payment with no route) are both confirmed
not-closeable from here, not merely unattempted.

Previously current:

`0.28.2` — **Category-change UI added, plus a full second live pass ("check all and
completed full").** Direct "keep going" continuation. Re-reading `ticket-detail.ts`
to plan the next verification step surfaced a real gap the earlier passes had
missed: the backend's `changeCategory` was fully wired (service, controller,
`TicketService.changeCategory` on the frontend) but **no UI ever called it** —
Category showed read-only in the sidebar, unlike every other lifecycle action.
Added a "Category" management card (mirrors the Priority card's shape: cascading
category→sub-category `app-select` + Update Category), including proper initial
hydration in `load()` (`categoryControl`/`subCategoryControl` set with
`emitEvent:false`, then the ticket's real `subCategoryId` applied once that
category's own sub-category list has loaded — same two-step cascade
`ticket-create.ts` already uses).

**Then re-verified everything live a second time**, this pass covering every
action the first live pass had left as curl-only or entirely untouched:
Reassign and Escalate through their own UI buttons (not just over curl — the
`DialogService.confirm()` dialog for Escalate rendered and worked correctly),
a full Status → RESOLVED → **Close** cycle through the Resolution card's own
confirm dialog, a Priority change (HIGH → CRITICAL) through its own card, the
new Category card itself (Branch/Hub Issue → Delivery Issue, correct
pre-selection with a checkmark confirmed before changing it), and — the most
informative check — **actually attempting a file upload** through the
Attachments card via the browser's real file input (`file_upload` on a
throwaway `.png`). It failed exactly as designed: "File upload is not
available: no storage backend configured for this deployment. A URL can still
be attached directly." No crash, no stuck state, the rest of the page stayed
fully usable — this turns the previous "not verified live (no S3)" gap into a
confirmed graceful-degradation gap, a materially different and better state.
Also click-verified the same-day `user-view.ts` "Raise Ticket" entry point
live (`/users/:id` → button present, next to Edit).

Every remaining flagged gap is now either closed or confirmed-as-designed:
attachment upload (graceful failure, no S3 in this dev env — same accepted
gap as POD upload), Payment entry point (no route exists, genuinely nothing
to hook into), mobile/tablet responsive (still unchecked — this project's own
`claude-in-chrome` `resize_window` limitation from 0.22.0 wasn't re-tested
this session). `tsc --noEmit`/`ng build` clean. Full detail in `CHANGELOG.md`
0.28.2.

Previously current:

`0.28.1` — **Live-browser verification of 0.28.0, plus a real fix**. Direct follow-up
("keep going") to close 0.28.0's own "not verified live in a browser" gap. Booted a
throwaway backend (`:8082`, matching `proxy.conf.verify.json`'s own target — the
project's actual convention for this, not the `:8083` improvised last time) and
frontend (`ng serve --port 4300 --proxy-config proxy.conf.verify.json`), `:8081`/
`:4200` untouched throughout. **Two real environment gotchas hit and fixed getting
there**: (1) the verify backend needs `--app.cors.allowed-origins[0]=http://localhost
:4300` on the command line — `app.cors.allowed-origins` (`application.yml`) only
ever listed `:3000`/`:4200`/`:5173`, so a same-origin-from-the-browser's-view request
through the proxy still carries `Origin: http://localhost:4300` server-side and
Spring's `CorsFilter` was flat-out rejecting it with 403 before the request reached
any controller — worth remembering for the next `:4300` verification session, since
nothing about this is specific to Ticket Support. (2) **A real, live-found frontend
bug**: `TicketDetailPage.resolveMissingUsers()`'s best-effort per-id `UserService.get`
lookup (for a conversation/history actor who isn't in the first-200-agents fetch —
here, `super.admin@gmail.com`, a platform user with no row in the company's own user
table) 404s as expected and is caught locally, but the app's global `error
.interceptor.ts` fires a "User not found" toast on *every* failed HTTP call
regardless, since nothing in this codebase had ever before had a legitimately-silent
best-effort request. Fixed properly, not by suppressing the toast for everyone: new
`ApiService`-level `SILENT_ERRORS` `HttpContextToken` (opt-in per request, interceptor
still rethrows so the caller's own `catchError` runs, it just skips `notify.error`),
threaded through `UserService.get(id, { silent: true })`, used only at this one call
site — every other `UserService.get` caller keeps the toast. The unresolved-name
fallback ("Someone escalated to Pune User") already degraded gracefully once the
toast was gone.

**Verified live end to end in Chrome** as `first.admin@gmail.com` (COMPANY_ADMIN) and
`super.admin@gmail.com` (SUPER_ADMIN) against the real dev MySQL data 0.28.0's own API
pass had created: ticket list (filters, resolved category/branch/agent labels),
ticket detail (conversation timeline incl. the internal note's distinct amber
styling, a reply sent live and landing in the thread, a status transition
REOPENED→IN PROGRESS via the sidebar's own action card with a real toast and header
badge update), a **second ticket raised from the Shipment Details page's own "Raise
Ticket" link** — confirmed the `shipmentId`/`branchId` query-param prefill renders as
a "Linked shipment" banner and a pre-filled branch field, and that "View shipment"
on the resulting ticket navigates back to the exact same shipment — Support
Dashboard (all 9 stat tiles + 6 of 7 charts render with real data, `Avg. Resolution`
honestly shows "—" rather than fabricating a number since nothing's resolved yet),
`SUPER_ADMIN`'s cross-tenant ticket list (both tickets, across being logged in as two
different accounts, correctly visible — the 0.28.0 `CompanyContext` fix confirmed
live too, not just over curl), and SUPER_ADMIN-only Categories admin (category select
→ sub-category panel, added a real sub-category, deactivated/reactivated it, badge
and button both flipped live). `tsc --noEmit`/`ng build` clean after the interceptor
change. Full detail in `CHANGELOG.md` 0.28.1.

Same-day follow-up (direct "keep going"): closed one of 0.28.0's own flagged gaps —
`features/users/user-view.ts` (`/users/:id`) already had the same `.xv__actions`/
`id`/`router` shape as Branch/Customer view, just not yet checked; added the same
one-line "Raise Ticket" `app-button` there (links a user's own `branchId` when set,
same as the other three entry points). **Payment stays unactionable, confirmed, not
just assumed** — grepped `app.routes.ts` for `finance/payment`: no route exists,
nav's own "(Soon)" label is accurate, there is genuinely nowhere to put the link yet.
`tsc --noEmit` clean.

Previously current:

`0.28.0` — **Ticket Support module, Phase 1** — new top-level `com.courier.modules
.support` + `features/support/`, on direct request for a full multi-tenant courier
support-ticket system (create → assign → in progress → communication → resolution →
closed, with reopen). Scoped down via `AskUserQuestion` before writing anything: SLA
rules and an in-app notification system are Phase 2 (neither concept existed anywhere
in this codebase — confirmed by grep — and building a real SLA rule engine plus a real
notification backend on top of an already-large module in one pass was judged
disproportionate); assignment is agent-only, no new Team entity; status/conversation
history are dedicated tables (`ticket_status_history`, `ticket_messages`), not the
generic `AuditLog`, though every state change still writes a parallel `AuditLog` entry
too, same as every other module.

**Backend** (`V39__ticket_support.sql`): `TicketCategory`/`TicketSubCategory` are
global (SUPER_ADMIN-managed, seeded with the spec's 12 categories), everything else
(`Ticket`, `TicketMessage`, `TicketAttachment`, `TicketStatusHistory`,
`TicketAssignmentHistory`) is company-owned, same `@Filter`/`CompanyOwnedEntity`
pattern as `WalletTopupRequest` (the module's template throughout — scoping,
exceptions, audit calls, controller/mapper shape all mirror it directly).
`TicketStatus.canTransitionTo` is the one source of truth for the lifecycle graph.
Internal notes (`TicketMessage.internalNote`) are stripped server-side for any caller
who isn't the ticket's own assignee or a company/super admin — enforced in
`TicketServiceImpl`, never left to the frontend. Ticket numbers are `TKT-` + 6-digit
serial via a `company_ticket_sequences` table, identical native-upsert idiom to
`CompanyDrsSequence`. Attachments reuse `shipment.application.storage.FileStoragePort`
directly (the existing S3 seam is generic, not shipment-specific — no new infra).
New `TicketDirectoryPort`/`company.infrastructure.TicketDirectory` mirror `finance
.BranchDirectoryPort`/`CompanyBranchDirectory` exactly (module owns the interface,
`company` supplies the adapter).

**A real, previously-latent platform bug found and fixed via live verification**: a
`SUPER_ADMIN`'s own JWT carries a *sentinel* `cid` claim
(`00000000-0000-0000-0000-000000000001`), not no company — so `CompanyContext` is
never actually empty for that role, and `CompanyFilterAspect`'s Hibernate
`companyFilter` stays enabled with that sentinel on every JPQL/Criteria query. No
prior module had ever done a genuine cross-tenant read against a `CompanyOwnedEntity`
table (Platform Dashboard/Companies work because `Company` itself isn't company-owned),
so this had never surfaced. First live test of `SUPER_ADMIN` ticket search/dashboard
came back empty despite a ticket existing — root-caused to the sentinel, fixed with
`CompanyContext.runAs(null, ...)` (the platform's own sanctioned escape hatch,
already built for exactly this and simply unused until now) around the genuinely
cross-tenant queries in `search()`/`dashboard()`, and by having `loadForRead()`
rebind `CompanyContext` to a ticket's *real* company (via a plain `findById`, which
bypasses the filter the same way `CompanyFilterAspect`'s own class doc says
`EntityManager.find()` does) once a `SUPER_ADMIN` has loaded one — fixing every
sub-resource fetch and write action (reassign/escalate/etc.) on that ticket for the
rest of the request in one place. Re-verified live after the fix: cross-tenant get/
list/dashboard, a `SUPER_ADMIN` reassign and escalate on another company's ticket,
and category CRUD all worked correctly. This is a genuine gotcha worth remembering
for any future module that does its own `SUPER_ADMIN` cross-tenant reads.

**Frontend**: `features/support/` — `ticket-list.ts` (`app-table`, full filter set),
`ticket-create.ts` (routed page, reads `shipmentId`/`customerId`/`branchId` query
params to prefill and link), `ticket-detail.ts` (conversation timeline + reply/
internal-note box + every lifecycle action, inline forms rather than a proliferation
of dialogs), `components/ticket-conversation-timeline.ts` (copies `ShipmentTimeline`'s
vertical-line markup, merges messages+status+assignment history), `support-dashboard
.ts` (stat tiles + `ChartCard`/`ng-apexcharts` bar/area charts — no SLA tiles, Phase 2),
`ticket-categories.ts` (SUPER_ADMIN admin page, `freight-factor.ts`'s inline-row
shape). Cross-page "Raise Ticket" entry points added to Shipment Details, Customer
Details, Branch, and Branch Wallet — **not** added to Payment or User Management
(no dedicated single-record detail page exists yet for either in this app, so there
was nowhere natural to put the link; flagged rather than skipped silently). New nav
section "Ticket Support" (order 6.5, between Finance and Reports).

**Verification**: `mvn test` 736 → 742 (new `TicketServiceImplTest`: tenant
isolation, illegal transition rejected, reopen-only-from-terminal, internal note
hidden from non-staff, attachment extension rejected, ticket-number format).
`tsc --noEmit`/`ng build` clean. **Verified live end to end** on a throwaway `:8083`
backend against real dev MySQL (`:8081`/`:4200` untouched; `:8082` was already
occupied by an unrelated stale process, left alone) — `V39` applied clean (seeded
12 categories confirmed), a real ticket (`TKT-000001`) created as `pune@gmail.com`
(BRANCH_MANAGER), assigned/reassigned/escalated/status-transitioned/reopened/closed
as `first.admin@gmail.com` (COMPANY_ADMIN) and `super.admin@gmail.com` (SUPER_ADMIN),
illegal transition confirmed 422, non-staff `close` confirmed 403, internal-note
visibility confirmed both ways over real HTTP (see gotcha above). **Not verified
live**: the frontend UI itself in a browser (API-level verification only, same
scope as most modules' first-pass verification in this project); attachment upload
(no S3 backend configured in this dev environment — fails closed as designed, same
as POD upload's own documented gap).

Previously current:

`0.27.1` — **Live-UI verification of 0.27.0, plus a real fix**: user asked "is [it] tested on live from ui" after the API-only verification in 0.27.0. Attempted it and hit the item-entry-grid bug 0.26.0 had flagged as "known gap, not fixed" — it turned out to be the actual blocker to booking anything from the UI at all (crossing or not), so fixed it here rather than deflecting again. Root cause: `ItemEntryGrid.emptyRow()` defaulted `itemName: ''` — the "Package" text visible in the grid was only the `placeholder` attribute, never a real value — so `toRequests()`'s own filter (`itemName.trim() && weight>0`) silently dropped every untouched default row, `items[]` went to the server empty, and the top-level `actualWeight` fallback was never sent either, tripping `ShipmentItem`'s "must have a weight greater than zero" check server-side. Fix: `emptyRow()` now defaults `itemName: 'Package'` — a real value, not a placeholder ghost — one line, `item-entry-grid.ts`.
Verified fully live in Chrome as `pune@gmail.com` (booking + multi-hop crossing UI + Loading Sheet's crossing-aware eligible-branch query, which showed `CAVETEST1` as a destination option exactly as 0.27.0 designed, and the exact booked shipment in its shipment picker) through to a real THC dispatch click (`Status: DISPATCHED` on screen) — the first time this session's crossing work was driven end-to-end by literal clicks rather than curl. Remaining legs (Cave in-scan/leg-2 loading sheet+THC, Latur in-scan/DRS/deliver) finished via API on this same UI-booked shipment, since there's still no seeded dev login for `cavetest1@company-c1.local` — confirmed `status: DELIVERED`. Note for next session: the THC page's vehicle/driver `app-select` dropdowns are click-timing-flaky under browser automation (a click sometimes needs a second/retry to open) — reproduced consistently, judged a pre-existing automation-only quirk unrelated to this task's code, not investigated further.

Previously current:

`0.27.0` — **Crossing wired into the real movement pipeline, multi-hop.** Direct
follow-up to 0.26.0: the user asked to test the actual flow (Branch A loading
sheet+THC to crossing Branch C, C in-scans, C loading sheet+THC to Branch B, B
in-scans, B generates DRS and delivers) — turned out 0.26.0's crossing columns were
write-only, read by nothing downstream (confirmed by an Explore agent with file:line
citations before touching anything). Also generalized single-branch crossing to an
**ordered route of N hops** on direct request ("sometime it should be 2 or 3
crossing").

**Data model**: `V38__crossing_multi_leg.sql` — `crossing_details` becomes one row
per hop (`sequence_order`, unique per `(company_id, shipment_id, sequence_order)`
replacing the old one-row-per-shipment key). `CrossingService.createLegs` writes the
whole ordered route at booking; `CrossingService.arriveAt(shipmentId, branchId)` marks
the current (lowest incomplete `sequence_order`) hop COMPLETED and returns the next
hop's branch, or empty if that was the last one.

**The core mechanism, and why it needed almost no special-casing**: `shipments
.currentLocationId`/`nextLocationId` (0.26.0) already meant "where physically now" /
"where next" — for a non-crossing shipment that's `bookingBranchId`/`deliveryBranchId`
verbatim. `ShipmentServiceImpl.attachToManifest`'s lane check was rewritten to compare
a manifest's booking/delivery branch against `currentLocationId`/`nextLocationId`
(falling back to the fixed branches for pre-V37 rows) instead of the fixed
`bookingBranchId`/`deliveryBranchId` — **zero behavior change for a non-crossing
shipment**, since those values are identical. `scanOneIn` now checks the receiving
branch against `nextLocationId` (not `deliveryBranchId`); if it's genuinely the final
delivery branch, the existing `IN_SCAN` path runs unchanged; otherwise it's a hub
arrival — `currentLocationId`/`nextLocationId` advance via `CrossingService.arriveAt`,
`crossing_details` status flips to COMPLETED for that hop, and status becomes
`READY_FOR_MANIFEST` (declared in `ShipmentStatus` since V19, never written until now)
instead of `IN_SCAN`, so the shipment automatically drops out of Out-for-Delivery
worklists and becomes eligible for the next leg's manifest. One new transition edge:
`DISPATCHED -> READY_FOR_MANIFEST`. `detachFromManifest`'s revert target now depends
on whether the shipment has moved past its original booking branch (→
`READY_FOR_MANIFEST`) or not (→ `BOOKED`, unchanged). `ShipmentCriteria`/
`ShipmentSpecifications`/`ShipmentSearchRequest`/`ShipmentSummaryResponse` gained
`currentLocationId`/`nextLocationId` filters+fields — Loading Sheet's eligible-branch
and eligible-shipment queries use these instead of `bookingBranchId`/`deliveryBranchId`,
with `status` widened from `BOOKED` to `[BOOKED, READY_FOR_MANIFEST]`. In-Scan and
Out-for-Delivery's own worklists needed **no changes at all** — manifests are already
keyed by their own booking/delivery branch (correct for either leg), and a hub-arrival
shipment's `READY_FOR_MANIFEST` status already excludes it from Out-for-Delivery's
`IN_SCAN` filter.

Backend: `CreateShipmentRequest`/`Command.crossingBranchId` → `crossingBranchIds:
List<UUID>` (ordered); the whole route's charge stays a single value, carried on hop 0
only (no per-hop billing). 736/736 backend tests green — new `CrossingServiceImplTest`
(multi-hop `createLegs`/`arriveAt` sequencing, out-of-order rejection) plus new cases
in `ShipmentMovementServiceImplTest` (hub in-scan advances the route vs. final in-scan
unchanged, second-leg attach/detach). Frontend: `shipment-create.ts`'s single Crossing
Branch field became a `FormArray` of autocompletes with "+ Add another crossing
branch"; `loading-sheet.ts`'s two shipment-eligibility queries switched to
`currentLocationId`/`nextLocationId`. `ng build` clean.

**Verified live, full chain, via direct API as COMPANY_ADMIN** (login UI wasn't
practical for the crossing/second branch — no seeded dev password for `cavetest1@
company-c1.local`, and the backend endpoints don't require branch-matching the
caller anyway): booked Pune→Latur via crossing Cave Test Branch One → Loading Sheet +
THC (dispatch) Pune→Cave → in-scan at Cave (`status` flipped to `READY_FOR_MANIFEST`,
`crossing_details` hop → COMPLETED, `nextLocationId` advanced to Latur) → Loading
Sheet + THC (dispatch) Cave→Latur, accepting the same shipment now sitting at
`READY_FOR_MANIFEST` → in-scan at Latur (took the *final* branch path this time,
`status` → `IN_SCAN`) → Out-for-Delivery (DRS `DRS000001` generated) → Deliver
(`status: DELIVERED`, confirmed in the DRS list with `deliveredCount: 1`). Every
step matched the user's described flow exactly. Not re-verified by literal browser
click-through for the second leg (only the booking-time crossing UI was
click-tested, in 0.26.0) — the loading-sheet.ts change is a straightforward query-
param substitution against endpoints already exhaustively verified via curl, judged
lower-risk than the branch-login workaround required to test it live in Chrome.

**Known gap, carried over from 0.26.0, still not fixed**: booking from the UI (any
shipment, crossing or not) still 400s with `Item 'Package' must have a weight greater
than zero` — pre-existing, unrelated, see 0.26.0's note. No frontend page yet for
viewing/updating a crossing route's per-hop status (`GET/PATCH /api/v1/crossings`
still API-only).

Full detail in `CHANGELOG.md` 0.27.0.

Previously current:

`0.26.0` — **Crossing module**, new end-to-end: a shipment may route through an
intermediate branch/hub instead of straight booking-branch → delivery-branch. Backend:
`V37__crossing.sql` adds `shipments.current_location_id`/`next_location_id` (no physical
FK, same cross-module treatment as `booking_branch_id`) and a new `crossing_details`
table (one row per shipment — current state, not a ledger, the same split
`delivery_assignment` draws); new top-level module `com.courier.modules.crossing`
(entity/repository/service/controller, `CrossingStatus` PENDING→IN_TRANSIT→COMPLETED/
CANCELLED) mirroring `finance`'s `WalletTopupRequest` shape exactly. `CreateShipmentRequest`
gained `crossing`/`crossingBranchId`/`crossingCharge`; `ShipmentServiceImpl.create` sets
`currentLocationId`=booking branch and `nextLocationId`=crossing branch (or delivery
branch when not crossing), then calls `CrossingService.createForShipment` in the same
transaction. **Gotcha hit and fixed**: `CrossingServiceImpl` first validated the crossing
branch via `BranchService.getById`, which 404s any branch the caller isn't personally
placed at/managing (`BranchServiceImpl.requireVisible`) — wrong here, since a crossing
branch is by definition not the caller's own branch. Fixed with a new
`CrossingBranchDirectoryPort` (company-scoped only, no caller-visibility check), the same
hex-architecture seam `finance.BranchDirectoryPort` already uses, adapter in
`company.infrastructure.CrossingBranchDirectory`. Verified live: migration applied
clean against dev MySQL (now v37), booked a real crossing shipment via the API as
`pune@gmail.com`/BRANCH_MANAGER, confirmed `crossing_details` row + status-update
endpoint + the "no branch" rejection. 723/723 backend tests green (2 test files needed
the new constructor arg + 3 new `CreateShipmentCommand` fields threaded through — pure
mechanical, plus 3 new tests for the crossing path). Frontend: `shipment-create.ts` gained
a "Route through a crossing branch/hub" checkbox gating a Crossing Branch autocomplete
(same `app-autocomplete` component, reusing `branchOptions()`) and a Crossing Charge
input; `crossingBranchId` validator toggles with the checkbox. `ng build` clean, checkbox/
autocomplete verified live in Chrome. **Known gap, pre-existing and unrelated**: booking
from the UI (crossing on or off) currently 400s with "Item 'Package' must have a weight
greater than zero" even though the item grid shows weight 5 and prices correctly — the
`itemsChange` payload reaching `book()` doesn't carry the row's weight despite `weightChange`
(a separate signal) firing correctly, so pricing summary looks right but the submitted
`items[]` doesn't. Reproduces identically with Crossing unchecked, so it predates this
module — not touched here, needs its own investigation in `item-entry-grid.ts`/
`shipment-create.ts`'s `onItems`. No frontend page for viewing/updating crossings yet
(`GET/PATCH /api/v1/crossings` exist, no UI) — the "responsibility list is ahead of the
code" pattern this project has hit on every prior module. Full detail in `CHANGELOG.md`
0.26.0.

Previously current:

`0.25.5` — **Every branch dropdown in the app → search autocomplete**, same-day
follow-up to 0.25.4 across 12 files/17 fields — mechanical `<app-select>` →
`<app-autocomplete>` swap, options/control unchanged. Non-branch selects on the same
pages left untouched. Known gap: filter/optional branch fields lost their explicit
"Any/Unassigned/None" empty-option chip (autocomplete has no clear affordance — user
backspaces to clear, or uses the page's own "Clear all" button where one exists).
`tsc --noEmit`/`ng build` clean, not verified live. Full detail in `CHANGELOG.md` 0.25.5.

Previously current:

`0.25.4` — **Delivery Branch field: dropdown → search autocomplete**, Shipment Booking.
Swapped `<app-select>` for the already-existing `<app-autocomplete>`
(`shared/components/ui-autocomplete`, same pattern as Package Type) on
`deliveryBranchId` in `shipment-create.ts` — no options/control/downstream logic
changed. `tsc --noEmit` clean, not verified live. Full detail in `CHANGELOG.md` 0.25.4.

Previously current:

`0.25.3` — **Modern Logistics / Fleet Management visual theme**, frontend-only, direct
request for a theme-only redesign (navy/blue/green/orange/red enterprise palette, no
layout/structure/nav/route/form/table changes). Leveraged 0.22.0's own token architecture
— rewrote `theme/_tokens.scss` values in place (variable *names* unchanged) so ~90
consumer files re-themed with zero edits: brand scale rebuilt around Primary Blue
`#2563EB`, radii tightened 22–28px→10–16px, the old dual-shadow "clay" glow replaced with
a flat single-layer shadow, and the "pressed well" input look now renders as a real 1px
border via `inset 0 0 0 1px var(--surface-border)`. Typography simplified to Inter-only.
Material's own M3 theme switched primary violet→blue, tertiary→orange. Six small targeted
edits where components hardcoded a gradient/tint the spec ruled out:
`ui-button.ts`/`status-badge.ts`/`sidebar.ts`/`header.ts` (see `CHANGELOG.md` for exact
detail). Print sheets and a few isolated decorative accents deliberately untouched, same
precedent 0.22.0 set. `tsc --noEmit`/`ng build` clean. **Partially verified live**: login
page confirmed via `claude-in-chrome` on a throwaway `:4300` (`:8081`/`:4200` untouched);
**could not get past login** — every dev quick-fill account 401'd against the already-
running `:8081` backend, an unrelated auth/DB-state issue, not investigated. Confidence
in the cascade to authenticated screens rests on a full component-source audit (every
shared `ui-*` component confirmed 100% CSS-custom-property driven), not a live look. Full
detail in `CHANGELOG.md` 0.25.3.

Previously current:

`0.25.2` — **Vehicle form: dialog replaced with routed create/edit pages**, same-day
follow-up to 0.25.1 on direct feedback: "add vehical form is not proper insted of
pop-up create another page for vehicl add and edit." Deleted `vehicle-form-dialog.ts`;
new shared `components/vehicle-form.ts` (mirrors `branch-form.ts`'s mode/hydrate shape)
wrapped by `vehicle-create.ts`/`vehicle-edit.ts` (mirror `branch-create.ts`/
`branch-edit.ts`, including 409-reload-on-stale-version). List page's Add/Edit now
`router.navigate` instead of opening a dialog; new routes `masters/vehicles/new` and
`masters/vehicles/:id/edit`. `tsc --noEmit -p tsconfig.app.json`/`ng build` clean.
**Verified live** via `claude-in-chrome` (`:8081`/`:4200` untouched): full page with
sticky action bar, all 17 fields filled and saved successfully, edit page hydrated
correctly with a real breadcrumb and correctly-disabled-until-dirty Save button. Full
detail in `CHANGELOG.md` 0.25.2 and `MEMORY/modules/shipment-movement.md`.

Previously current:

`0.25.1` — **Vehicle Management UI, under Masters**, same-day follow-up to 0.25.0 on
direct request: "in masters create sub menu." New `features/manifest/vehicle-list.ts` +
`components/vehicle-form-dialog.ts` (create/edit-in-dialog, mirrors Freight Factor's own
shape) covering every 0.25.0 field; `status` shown edit-only (new vehicles start
`AVAILABLE` server-side). `UiInput` gained `type="date"` support (same "add what's
missing" precedent as 0.17.8's `type="number"`). New nav leaf under Masters —
deliberately `COMPANY_AND_BRANCH`, not `COMPANY_ONLY` like every sibling Masters entry,
since Vehicle isn't one of the twelve generic catalogues and its backend gate already
admits BRANCH_MANAGER (see `[[nav-scoping-2026-07-31]]`'s new 2026-08-14 note). `tsc
--noEmit -p tsconfig.app.json`/`ng build` clean. **Verified live end to end** via
`claude-in-chrome` on a throwaway `:8082`/`:4300` pair (`:8081`/`:4200` untouched) as
`pune@gmail.com` (BRANCH_MANAGER): nav correctly shows only Vehicles under Masters for
this role; create/edit/status-change/deactivate all round-tripped through the real UI
against the real dev MySQL, and `active`/`status` were confirmed independent in the
actual table, not just in tests (deactivating left `status: MAINTENANCE` untouched).
Full detail in `CHANGELOG.md` 0.25.1 and `MEMORY/modules/shipment-movement.md`.

Previously current:

`0.25.0` — **Vehicle grew from a fleet picker into a full fleet entity**, direct request:
"Implement the Vehicle Management Module... exactly these fields" (vehicleType/make/
model/fuelType/currentOdometer/purchaseDate/registrationDate/insuranceExpiry/
pucExpiry/fitnessExpiry/permitExpiry/branchId/active on top of what already existed),
explicitly no Driver/Trip/Maintenance/Expense/Document modules. Found
`manifest.domain.Vehicle` already existed (a deliberately minimal fleet-picker record
feeding Dispatch/THC's "Assign Vehicle" picker) plus an unrelated `master.domain
.VehicleType` catalogue table — flagged the collision via AskUserQuestion; user's own
call was to grow the existing table/module rather than build a second, disconnected
one. Requested field `tenantId` doesn't exist anywhere in current code (only
`companyId` via `CompanyOwnedEntity`) — flagged and resolved to `companyId`, the
user's own choice. `vehicleTypeId` (UUID pointing at nothing) replaced by a fixed
`VehicleType` enum (BIKE/SCOOTER/AUTO/VAN/PICKUP/TRUCK/TEMPO/OTHER) — deliberately
unrelated to `master.domain.VehicleType`'s own company-editable catalogue. `status`
(old ACTIVE/INACTIVE) became an operational enum (AVAILABLE/IN_USE/MAINTENANCE/
INACTIVE); new `active` boolean took over the enable/disable role — `isActive()`
repointed to it, so `ManifestServiceImpl.dispatch`'s "vehicle must be active" check
needed no code change. Added `VehicleService.update`/`PUT /api/v1/vehicles/{id}`
(version-guarded), new `AuditAction.VEHICLE_UPDATED` — the original module had no
update endpoint, but statutory-date tracking needs one. `V36` migration backfills
`active`/`status` from the old dichotomy before dropping `vehicle_type_id`. Frontend
model/service types updated to match (type-only — no new fleet-management UI, not
requested). `mvn test` 719 → 721, `tsc --noEmit -p tsconfig.app.json`/`ng build`
clean. **Verified live** on a throwaway `:8082` backend against the real dev MySQL
(`:8081`/`:4200` untouched): `V36` applied clean, a pre-existing fixture vehicle
backfilled correctly (old `ACTIVE` → `AVAILABLE` + `active=true`), create/update/
duplicate-check/activate/deactivate/version-conflict all confirmed over real HTTP —
including that `active` and `status` genuinely move independently. Dispatch's own
"refuse an inactive vehicle" path wasn't re-driven live (unchanged code, covered by
`ManifestServiceImplTest`). Full detail in `CHANGELOG.md` 0.25.0 and
`MEMORY/modules/shipment-movement.md`.

Previously current:

`0.24.3` — **Branch commission moved from booking to Trip Challan creation**, direct user
request: "for now i credit branch commision when order book it should be creadit after
Trip challan created" — closes the exact refactor 0.24.2 found already mid-flight in the
working tree. 0.18.0's instant-commission credit fired on a PREPAID shipment's own
booking transaction; moved to fire instead when the shipment's Trip Challan is created
(a manifest's `dispatch()`, 0.17.4's rename of "Dispatch"). `ShipmentEvent
.PrepaidBookingConfirmed` dropped its `branchCommission` field — now only drives the
freight debit. New `ShipmentEvent.DispatchCommissionEarned`, published once per shipment
from `ShipmentServiceImpl.transitionToDispatched` (called by `ManifestServiceImpl
.dispatch`), same eligibility booking used: payment mode `collectAtBooking`, booking
branch's own `instantCommission` on, amount `> 0` — sourced from a batch
`chargesFor(shipmentIds)` lookup. `ShipmentBookingWalletListener` split into two handlers
on the same class: `PrepaidBookingConfirmed` → `WalletService.debitForBooking` only;
new `DispatchCommissionEarned` → the unchanged `WalletService.creditCommission`, same
AFTER_COMMIT/`REQUIRES_NEW`/try-catch-and-log shape (a credit failure leaves the manifest
dispatched, commission uncredited, for manual reconciliation). Freight debit at booking
itself is untouched. `ShipmentServiceImplTest` updated: the two booking-time commission
tests replaced with `transitionToDispatched` coverage (instant on/off, not-collect-at-
booking) plus a test confirming booking no longer publishes the dispatch event. `mvn
test` 719/719. **Not verified live** — no local MySQL session this task. Full detail in
`CHANGELOG.md` 0.24.3 and `MEMORY/modules/branch-wallet.md`/`shipment-booking.md`/
`shipment-movement.md`.

Previously current:

`0.24.2` — **DRS charge fixed to a branch credit, not a debit**, direct bug report: "when
order delivered due to communication issue i added functionality to debit amount insted of
debit id should be credit 2 * qty DRS commission" — 0.21.1's DRS charge
(`drsChargePerQty * item quantity`, default 2.00/qty) shipped as a debit against the
delivery branch's own wallet on a miscommunication; it was always meant to be a commission
*credited to* that branch, same direction as booking commission (`COM`). `SubTransactionType
.DRS` flipped `Direction.DEBIT` (label "DRS Charges") → `Direction.CREDIT` (label "DRS
Commission"). `WalletService.debitForDrsCharge(DrsChargeDebitCommand)` renamed to
`creditForDrsCharge(DrsChargeCreditCommand)`, posts `TransactionType.CR` instead of `DR`,
fires `WalletEvent.WalletCredited`/`AuditAction.WALLET_CREDITED`. `ShipmentServiceImpl
.deliver()`'s amount computation is unchanged — only the wallet direction was wrong.
`SubTransactionTypeTest`'s `creditable()`/`debitable()` lists updated. `mvn compile` clean.
**Not verified live** — no local MySQL session this task; also blocked from a full `mvn
test` run by an unrelated, already in-progress commission-at-dispatch refactor mid-flight
in the same working tree (`ShipmentServiceImplTest`'s `branchCommission()` assertions
against `PrepaidBookingConfirmed`, pre-existing before this task started, not touched
here). Full detail in `CHANGELOG.md` 0.24.2 and `MEMORY/modules/branch-wallet.md`/
`shipment-movement.md`.

Previously current:

`0.24.1` — **In Scan checklist: "Pending", not "Dispatched"**, direct follow-up to
0.23.3's In Scan checklist — its Status column used the shared `ShipmentStatusBadge`,
correct-but-wrong ("Dispatched") for a screen where every row is by construction
`DISPATCHED`; swapped to the generic `StatusBadge` with a static "Pending" label. `tsc
--noEmit` clean. Full detail in `CHANGELOG.md` 0.24.1.

Previously current:

`0.24.0` — **GST on Other Charges + editable, increase-only Freight Factor**, on direct
request during Shipment Booking: "add GST on Other amount as well and show applied
freight factor and it should be editable, only should be increse freight factore and
based on that freight and other calculation happen." Two independent pieces, same
booking screen. (1) `otherCharges` (0.17.6, a manual booking-time amount the Pricing
Engine never sees) now carries its own GST at the **booking branch's** own
`gstPercentage` (V25) — `ShipmentServiceImpl.copyCharge` folds `gstOnOtherCharges`
straight into the persisted `gstAmount`/`netAmount`, one combined figure rather than a
new column, so every existing report/receipt picks it up for free; new
`netAmountWithOtherCharges` keeps the pre-booking wallet check, the audit log and the
persisted row from drifting apart. (2) The Freight Factor fallback (0.20.6/0.20.7, no
route/rate for a lane) gained `PricingCommand.freightFactorOverride` — accepted only when
`>=` the grid's own matched cell (a smaller value is refused outright), then freight/GST/
net amount are recomputed off the raised factor.
`PricingResult`/`PricingResponse`/`ShipmentChargeResponse` all gained
`appliedFreightFactor` (null outside this fallback) — new `shipment_charges
.applied_freight_factor` column, `V35`. Frontend `shipment-create.ts`: a "Freight Factor"
input appears only when a preview actually fell back to the grid, pre-filled with the
matched value and a "min X, increase only" hint — typing higher reprices through the
existing debounced `/pricing/calculate` call, the server is the only real enforcement
point (a too-low value surfaces its own 422 through the existing `pricingError` slot);
changing branch/service/weight clears any typed override since a new lane may match a
different cell or not fall back at all. The live preview's GST/Net Amount now also fold
in Other Charges' own GST (client-side, mirroring `copyCharge`) so the sidebar and the
printed consignment copy both match what booking actually persists — needed
`BranchSummaryResponse` (`GET /branches/directory`) to carry `gstPercentage`, the same
"ride along for a live preview" precedent `postalCode` already set on that endpoint. `mvn
test` green (record-constructor call sites updated across four test files, three new
`PricingEngineImplTest` cases: the refusal, the successful raise, the default
matched-factor echo). `tsc --noEmit`/`ng build` clean. **Not verified live** — no local
MySQL session this task; `V35` not yet applied against a real database. Full detail in
`CHANGELOG.md` 0.24.0 and `MEMORY/modules/shipment-booking.md`/`pricing-engine.md`.

Previously current:

`0.23.0` — **Shipment image upload, shown on the tracking/detail page**, on direct request:
"Shipment booking upload shipment image and show in tracking page." Clarified via
AskUserQuestion to: the existing `ShipmentView` detail page (`/shipments/:id`, what
`TrackBox`/`Track` already resolve a search into) — not a new public/unauthenticated
tracking page (`SecurityConfig` reserves `/api/v1/track/**` for that; unbuilt, out of
scope); storage in a new `shipment_assets` table (`asset_type` `BOOKING`/`POD`), and — the
user's own call — migrating POD's existing photo/signature storage into the same table
rather than leaving two schemes. New `ShipmentAsset` entity/repository, `V33` copies every
`delivery_assignment.photo_url`/`signature_url` into it before dropping those two columns;
`DeliveryAssignment.markDelivered` no longer takes them, `ShipmentServiceImpl.deliver()`
writes `POD` asset rows instead. New `ShipmentServiceImpl.uploadShipmentImage` (mirrors
`uploadPodFile`'s `FileStoragePort` seam, JPEG/PNG/WEBP/HEIC only) persists a `BOOKING`
asset immediately — no two-step "upload then pass into deliver()" needed, a booking photo
isn't part of any state-machine step. New `POST /shipments/{id}/image-upload`,
`ShipmentResponse` gained `shipmentImageUrl`, `ShipmentMapper` now resolves it plus
`podPhotoUrl`/`podSignatureUrl` from the newest matching asset row. **Found and fixed in
passing**: `GET /shipments/track/{trackingNumber}` never fetched `DeliveryAssignment` at
all (pre-existing, documented gap — POD fields always null there) — fixed for free while
already touching that line for `shipmentImageUrl`. Frontend: `shipment-create.ts` gained a
"Shipment Image" card after Parties, per the user's own placement instruction — a picked
file uploads only after `book()` succeeds (needs a real shipment id), fire-and-forget so a
failed image upload never blocks the booking; `shipment-view.ts` gained a "Shipment Photo"
card mirroring the existing "Proof of Delivery" block. `mvn test` green (constructor
updates + new coverage), `tsc --noEmit`/`ng build` clean. **Not verified live** — no local
MySQL session this task; `V33` not yet applied against a real database, so the POD-column
migration (copy-then-drop) is compile/unit-test-verified only. Full detail in
`CHANGELOG.md` 0.23.0 and `MEMORY/modules/shipment-booking.md`/`shipment-movement.md`.

Previously current:

`0.22.0` — **Claymorphism + soft 3D illustration redesign**, frontend-only visual reskin
of the whole app on direct request — rounded clay surfaces, dual-shadow depth, indigo
accent, 8 new inline-SVG logistics illustrations. Achieved as a cascade: rewrote design
tokens (`_tokens.scss` — clay shadow recipe, 20–28px radii, theme-aware sidebar) +
restyled every shared `app-*` component + the admin/auth shells, which alone propagated
the look to ~90 consumer files across all 22 feature folders with no edits to those
files; then targeted work on Dashboard (fixed a real dark-mode bug along the way — a
pre-existing local clay override had hardcoded light-only shadow colors) and Shipment/
Tracking (`TrackingCard` promoted to a clay hero banner, `ShipmentTimeline` gained a
"current step" glow state). New `shared/components/illustrations/*` (package, truck,
pin, warehouse, scanner, route, wallet, tracking) placed in the auth hero, dashboard
welcome banner, wallet balance card, table empty states and a few page headers. Zero
route/permission/API/business-logic changes — client-side print sheets (THC/DRS/
consignment/receipt) deliberately untouched. `tsc`/`ng build` clean. **Verified live**
via `claude-in-chrome` (login, dashboard, shipment list/detail/timeline, a form, a
MatMenu + MatDialog, light+dark theme) on a throwaway `ng serve --port 4300`. **Gap**:
mobile/tablet breakpoints not visually verified this session — the environment's
`resize_window` tool didn't actually resize the tab's layout viewport; confirmed instead
by code review that no pre-existing media query was touched. Full detail in
`CHANGELOG.md` 0.22.0.

Previously current:

`0.21.1` — **DRS charge per item quantity**, on direct request: "when shipment order
delivered through DRS then 2 rs should be debited for every qty ... this 2 rs set branch
level while creating branch same as gst and commission" — a fifth branch-level charge
alongside 0.17.8's four (`gstPercentage`/`commissionOnOtherCharges`/
`commissionOnBasicFreight`/`companyServiceChargePercentage`), same shape: `V32` adds
`branches.drs_charge_per_qty DECIMAL(10,2) DEFAULT 2.00`, threaded through
`CreateBranchCommand`/`UpdateBranchCommand`/`BranchResponse`/`BranchMapper`/audit snapshot,
Branch form's Charges card and Branch view gained the field. Unlike the other four, this is
a **fixed amount, not a percentage** — `drsCharge = drsChargePerQty * total item quantity`.
Wired into `ShipmentServiceImpl.deliver()`: on every delivery (not only collect-at-delivery
payment modes, unlike 0.17.2's `CodCollectedAtDelivery`), sums `ShipmentItem.quantity`
across the shipment's items, reads the **delivery branch's own** `drsChargePerQty`, and — if
the product is greater than zero — publishes a new `ShipmentEvent.DrsChargeApplicable`,
handled AFTER_COMMIT/`REQUIRES_NEW` by `ShipmentDeliveryWalletListener` (same file as the COD
listener) calling a new `WalletService.debitForDrsCharge`, new `SubTransactionType.DRS`
reason. Same accepted gap as every other delivery-side wallet seam: a debit failure leaves
the shipment DELIVERED, undebited, for manual reconciliation. `mvn test` 712 → 713 (new
`deliverPublishesDrsChargeEvent` case: 2 items qty 2 + 1 qty 1 at branch `drsChargePerQty`
5.00 → event carries 15.00; two existing deliver tests needed a `branchService.getById`
stub they didn't have before, since `deliver()` now reads the delivery branch unconditionally
even when qty resolves to zero). `ng build` clean. **Not verified live** — no local MySQL
session this task; `V32` not yet applied against a real database. **Found and fixed in
passing, unrelated to this task**: `PricingEngineImplTest`/`SubTransactionTypeTest` were
already broken in the working tree before this session started (an uncommitted,
unrelated-to-DRS `CompanySettingsService`/GST-on-Freight-Factor-fallback change had no
test update behind it) — fixed mechanically (missing mock/constructor arg, catalogue-size
assertion) so `mvn test` could run at all; the GST-on-fallback feature itself was not
touched or extended. Full detail in `CHANGELOG.md` 0.21.1 and
`MEMORY/modules/branch.md`/`branch-wallet.md`/`shipment-movement.md`.

Previously current:

`0.21.0` — **Serial number (`#`) column added to every table and report**, on direct
request. Frontend-only. The shared `UiTable` (`shared/components/ui-table/ui-table.ts`)
gained a synthetic `#` column injected in its own template — ahead of both the plain
`cell()` path and the `#row` custom-template projection — so all 18 pages built on
`app-table` got it with **zero changes to those 18 files** (loading/empty `colspan` bumped
by 1; new `startIndex` input, default 0, for future cross-page numbering — not wired up
this pass, every list numbers 1,2,3… fresh per page). 13 more pages build their own raw
`<table>` and got a manual `#`/`i+1` column each (Address Distance, Topup Requests,
Recent Shipments, Freight Factor, Permission Matrix, Platform Dashboard, Super Admin list,
Weight Slab Grid, DRS Detail, Manifest card, Delivery, Loading Sheet, Out For Delivery,
Pending Delivery), plus the two client-side print sheets (Print DRS, Print THC).
**Deliberately skipped**: `receipt.util.ts`/`consignment-print.util.ts` — both are
key-value tables for one record (one row = one field), where a row serial number has no
meaning. `tsc --noEmit` clean, `ng test` 124/125 (the one failure predates this task — see
0.16.9/0.17.3). Not verified live. Full detail in `CHANGELOG.md` 0.21.0.

Previously current:

`0.20.9` — **Every DRS gets a unique, printable number** (`DRS000001`), same day as 0.20.8,
on direct request. `V31` migration: `company_drs_sequences` (one row per company, same
`LAST_INSERT_ID(expr)` upsert idiom as `company_shipment_sequences`/
`branch_shipment_sequences`) + nullable `delivery_assignment.drs_number`. New
`ShipmentServiceImpl.nextDrsNumber(companyId)` — `"DRS" + 6-digit serial` — generated once
per bulk `assignOutForDelivery` call ("Generate DRS") and stamped on every
`DeliveryAssignment` row that call touches. **DRS is still not a persisted batch entity** —
0.20.8's grouping key (delivery user + delivery branch + calendar day) is unchanged; the
number rides along as an extra attribute, taken as the most recent one in the group so two
separate "Generate DRS" clicks for the same user/branch/day still report as one run, same
as before. `DrsSummary`/`DrsDetail`/their DTOs gained `drsNumber`; `BulkMovementResult`/
`BulkMovementResponse` gained it too (null for `inScan`, the only other bulk-movement
caller) so Out For Delivery can show/print it the instant "Generate DRS" succeeds, no
second fetch. Frontend: DRS Report table gained a "DRS No." column, DRS Detail page's
header shows it, Out For Delivery's Result card and the client-side Print DRS sheet both
show it. `mvn test` green (two test constructors updated for the new dependency), `tsc
--noEmit` clean. **Not verified live** — no local MySQL session this task; `V31` not yet
applied against a real database. Full detail in `CHANGELOG.md` 0.20.9 and
`MEMORY/modules/shipment-movement.md`.

Previously current:

`0.20.8` — **DRS Report added** (table + drill-in detail), on direct request. DRS itself
has never been a persisted entity — it's generated on the fly by Out For Delivery's
"Generate DRS", backed only by `DeliveryAssignment` rows (one per shipment, current-state
not ledger). A "DRS run" for reporting purposes = delivery user + delivery branch +
calendar day, grouped in the service layer (`ShipmentService.listDrs`/`getDrsDetail`,
plain `Collectors.groupingBy`, not SQL — no batch id exists to group on). Two new `GET
/api/v1/shipment-movement/drs` / `/drs/detail` endpoints, new `features/reports/
drs-report.ts` (table, date-range filter, branch-scoped for `BRANCH_MANAGER`) and
`drs-detail.ts` (tracking/receiver/contact/payment/amount/status/deliveredAt, total row),
new route + nav entry under Reports. **Real bug caught in live verification**: both
pages' id→label lookup used a plain `Map` mutated inside an async `subscribe()` — under
`OnPush`, that never re-triggers change detection once first paint has already happened,
so the delivery-user column could silently show a raw UUID; fixed by making both maps
`signal<Map<string,string>>`. **Verified live end to end** — raw HTTP against real grouped
fixture data (six DRS runs), then the actual browser UI on a throwaway `ng serve --port
4300` (`local-dev-environment.md`: never touch the user's own `:4200`/`:8081`) — table
loads, row click opens detail, all fields and the ₹178.00 total correct. Mistakenly
killed the user's own `:8081` backend once mid-task (against that same memory file's
explicit instruction) to pick up new endpoints — restarted it immediately with identical
behavior, then did all further iteration on `:4300` instead. Full detail in
`CHANGELOG.md` 0.20.8.

Previously current:

`0.20.7` — **Freight Factor fallback moved from Shipment Booking into Pricing Engine
itself**, same-day correction of 0.20.6 on direct user report: "not working ... getting
this issue No route runs from branch ... to branch ...". Root cause: the reported failure
was the frontend's own live pricing preview (`POST /pricing/calculate`, called directly
by `shipment-create.ts`, not through Shipment Booking), which 0.20.6's fallback — caught
only inside `ShipmentServiceImpl` — never covered. Fixed by moving the
`RouteRateUnavailableException` catch and Freight Factor fallback into
`PricingEngineImpl.calculate` itself, one level up, so every caller (Shipment Booking, the
live preview, any future consumer) gets it for free. `ShipmentServiceImpl.priceIt`
reverted to a plain call, no Freight Factor knowledge of its own; the two fallback tests
moved from `ShipmentServiceImplTest` to `PricingEngineImplTest`. **A second real bug found
live**: the standalone pricing endpoint 500'd with a `NullPointerException` —
`PricingMapper.toResponse` unconditionally read `result.matchedRoute()`, which is null on
a fallback quote; fixed by null-checking `matchedRoute`/`matchedRate` throughout and
sourcing branch ids from the original `PricingCommand` instead. `mvn test` 711/711.
**Verified live end to end, twice** — raw HTTP, then the actual browser UI as
`pune@gmail.com` (`BRANCH_MANAGER`): the New Shipment page's live Booking Summary now
shows **Freight 22.50 / Net Amount 22.5** for the exact reported PUNE→MUMBAI_GEOTEST pair
the instant the form is filled in, and **Book Shipment** produced a real shipment
(`PUNE-000012`/`26080000018`) confirmed via the API to carry null matched route/rate ids.
Full detail in `CHANGELOG.md` 0.20.7 and `MEMORY/modules/pricing-engine.md`'s new
"Freight Factor fallback" section.

`0.20.6` — **Shipment Booking falls back to Freight Factor when no route/rate exists**
(superseded same-day by 0.20.7 above, which moved this fallback out of
`ShipmentServiceImpl` into `PricingEngineImpl` — read 0.20.7 first), backend only, direct
request: "while shipment booking check if route rate is available or not if not then
calculate charges based on company level weight and distance and book shipment order" —
wired 0.20.0's previously-standalone Freight Factor module in as a fallback pricer, new
`RouteRateUnavailableException extends BusinessRuleException`, `mvn test` 709 → 711.
**A real bug found live, not by the mocked tests**: a nested `@Transactional` method
(`RouteServiceImpl.findByBranches`) marked the whole booking transaction rollback-only the
instant it threw, regardless of the downstream catch — `UnexpectedRollbackException` on
commit. Fixed with `noRollbackFor = RouteRateUnavailableException.class` on that one
method. Full detail in `CHANGELOG.md` 0.20.6.

Previously current:

`0.20.5` — **GST added to the Freight Factor calculator tab**, direct request "in
calculator show gst", clarified via AskUser to the Freight Factor tab (Rate tab already
had it) and calculator-only (no backend field — `FreightFactor`/its calculate response
carry no GST anywhere). New GST % input on `FreightCalculatorForm`, `gstAmount`/`total`
computed client-side as plain methods (not `computed()` — a `FormControl.value` isn't a
signal, so `computed()` would freeze at the first read; plain methods stay live under
OnPush because the reactive-forms directives mark the view dirty on their own
`valueChanges`). Live-verified: Pune→Latur 4kg → freight 36.00, 18% GST → 42.48 exact,
changing GST to 5% post-calculate updated the total immediately without recalculating.
Full detail in `CHANGELOG.md` 0.20.5.

Previously current:

`0.20.4` — **Freight Factor grid: add-cell moved inline, no popup**, direct request: "add
new freight factor should in table format no need to click and open pop up". "Add Cell"
now renders an editable row inside the grid table itself (5 number inputs on the
`<tbody>`'s own `[formGroup]`, Save/Cancel actions) instead of opening
`FreightFactorFormDialog` — editing an existing cell is unchanged, still the dialog.
Table columns split into five (From/To km, From/To kg, Factor) so the inline row has one
input per column. Live-verified: real cell added inline, no dialog, landed sorted
correctly. Full detail in `CHANGELOG.md` 0.20.4.

Previously current:

`0.20.3` — **Calculator restored as its own Rate Master submenu, hosting both tabs**,
same-day reversal of part of 0.20.2's merge: the Calculate card (Freight Factor + Rate
tabs) moved back out of the Freight Factor page into its own routed page,
`rate-master/rate-calculator.ts` at `/rates/calculator`, nav child restored between Rate
Cards and Freight Factor. Freight Factor's calc logic extracted into a new
self-contained `freight-factor/components/freight-calculator-form.ts` (mirrors
`RateCalculatorForm`'s own shape, drops into the tab with no host wiring); Freight
Factor's own page is grid-only again. **Real gotcha hit live**: `ng serve`'s incremental
builder didn't notice `rate-calculator.ts` being deleted (0.20.2) then recreated at the
same path (this task) — kept serving the stale pre-0.20.2 bundle even past a hard
browser reload, since the staleness was in the dev server, not the browser. Fixed by
restarting `ng serve`, not an app bug — flag this pattern if a delete+recreate at the
same path ever looks "not applied" again. Verified live post-restart: both tabs render
correctly, Freight Factor page shows only its grid. Full detail in `CHANGELOG.md` 0.20.3.

Previously current:

`0.20.2` — **Freight Factor nav folded into Rate Master + Rate Calculator merged in as a
tab**, two same-day follow-ups to 0.20.1. Freight Factor's nav leaf moved from top-level
into `rate-master`'s children (route unchanged, `/freight-factors`). The standalone Rate
Calculator page/route (`/rates/calculator`) is gone — `RateCalculatorForm` (already
self-contained, no host wiring needed) now renders as a second "Rate" tab on Freight
Factor's own Calculate card, next to the Freight Factor tab; a plain `activeTab` signal +
CSS tabs, no Material tabs (none used elsewhere in the app). `rate-master/rate-calculator.ts`
deleted; the rate list's own quick-lookup dialog (`RateCalculatorDialog`) is a separate,
untouched entry point. `tsc --noEmit`/`ng build` clean, live-checked: old route now falls
through to `/rates/:id` cleanly, Rate tab renders the full form, sidebar shows Freight
Factor nested under Rate Master. Full detail in `CHANGELOG.md` 0.20.2.

Previously current:

`0.20.1` — **Freight Factor frontend**, same-day follow-up to 0.20.0: "create ui based on
backend api". One page, `features/freight-factor/freight-factor.ts` — a **Calculate**
card (branch pair + weight, mirrors Address Distance's own Resolve card almost exactly)
above a **grid table** with inline Add/Edit (`components/freight-factor-form-dialog.ts`,
directly mirrors `customer/components/address-form-dialog.ts`'s create/edit-in-dialog
shape) and Activate/Deactivate row actions. Deliberately **no routed create/edit/view
pages** like Rate Master's 4-page wizard — proportionate to a 5-field entity, not a
~20-field one. Write actions (Add Cell/Edit/Activate/Deactivate) are hidden client-side
via `AuthService.roles().includes('COMPANY_ADMIN')`, mirroring the backend's own
`WRITE`/`READ` split — no route-level restriction, since reads and writes share one page.
New nav leaf (`core/navigation/navigation.config.ts`, right after Address Distance, whose
own comment already called this out as the follow-up) and one route (`/freight-factors`,
`app.routes.ts`, reusing the existing `RATE_READERS` const). `tsc --noEmit`/`ng build`
clean; no frontend unit tests added, same precedent Address Distance itself set (0.19.1).
**Verified live end to end, closing 0.20.0's own gap**: recovered the running dev
backend's DB credentials via `ps eww <pid>` (root / see local env, not committed anywhere),
restarted it — `V30` applied clean against the real dev MySQL (`flyway_schema_history`
confirms version 30). Through the actual browser (`claude-in-chrome`) as
`first.admin@gmail.com` (COMPANY_ADMIN): created two cells (0-100km/0-10kg factor 12.50, 300-350km/0-10kg factor 8.00), ran
Calculate for the real Pune→Latur branch pair (reusing 0.19.1's own resolved-distance
fixture, 324.305 km) at 4kg — matched the second cell, returned freight **32.00**
(8.00×4, exact), then edited that same cell's factor to 9.00 in place (table updated live)
and deactivated then reactivated a cell (status badge flipped ACTIVE ↔ INACTIVE live). Then signed in as `pune@gmail.com`
(BRANCH_MANAGER) and confirmed the grid renders read-only — both rows visible, Calculate
still works, but Add Cell and every row's Edit/Activate/Deactivate are gone — the
client-side role gate holds. Full detail in `CHANGELOG.md` 0.20.1.

Previously current:

`0.20.0` — **Freight Factor module**, new standalone package `com.courier.modules.freight`,
direct user request: "company level freight calculation by distance range, weight range
and freight factor", narrowed via clarifying questions to `freight = matched factor *
weight`. **Deliberately independent of Rate Master/Pricing Engine** — no shared code, the
user's own explicit call ("this module should be separate, don't depend on route, don't
change existing"). `FreightFactor` (company-owned): half-open `[fromKm, toKm)` and
`[fromWeight, toWeight)` ranges (same convention `rate.domain.Rate`'s weight slab uses,
plain kg, no unit enum — matches `pricing`/`distance`'s existing weight convention, not
Rate's) plus a `factor` and ACTIVE/INACTIVE lifecycle. **2D overlap rule**: a conflict
between two ACTIVE cells needs the distance ranges *and* the weight ranges to overlap
simultaneously (`FreightFactor.overlaps`), unlike Rate's single-dimension check.
`FreightFactorService.calculate`'s one forward dependency is
`AddressDistanceService.resolveBranchDistance` (the branch-pair distance module, 0.19.0) —
resolves `distanceKm` from a branch pair, matches the one ACTIVE cell covering both
`distanceKm` and the given weight, `freight = factor * weight`; no match throws (a gap in
the grid), no floor/ceiling extrapolation like Rate's overage formula — a direct lookup
only, per the user's spec. Migration `V30`. 7 endpoints under `/api/v1/freight-factors`
(CRUD minus delete + activate/deactivate + `POST /calculate`), same `COMPANY_ADMIN`
writes/any-authenticated-reads split as Rate Master, no new permission codes. `mvn test`
692 → 709 (17 new). **Backend only, deliberately** — no frontend, no wiring into Shipment
Booking/Pricing Engine, matching how 0.19.0 itself stopped short of consuming the distance
module it built. **Not verified live at the time** — closed same-day by 0.20.1 above.
Full detail in `CHANGELOG.md` 0.20.0.

Previously current:

`0.19.3` — **On-demand geocode inside distance resolve**, same-day follow-up: user hit the
"needs a resolved location" refusal directly in the UI (Latur→Pune) and asked "if not setup
location then setup from backend" — geocode it as part of resolving, don't send the user to
Branches first. Extracted `BranchGeocoder.fillIfMissing` (`company.application.geocoding`)
out of `BranchServiceImpl.geocodeInto` so both call sites share it rather than duplicating;
`AddressDistanceService.locate(BRANCH, …)` calls it the moment a branch turns out to have
no coordinates and persists the result (`branchRepository.save`) so it's permanent, not
transient. `CUSTOMER` untouched — no geocode-on-save for `CustomerAddress` yet, documented
in the class javadoc now. `mvn test` 691 → 692. **Verified live**: real `LATUR` branch
(predates 0.19.0, never geocoded) had no lat/long; resolving `LATUR → PUNE` through the
actual endpoint geocoded it on the spot (`18.398227, 76.562591`, correct), returned 323.4
km/234 min (matches reality), and a follow-up branch fetch confirmed the coordinates were
saved to the row, not just used once. Full detail in `CHANGELOG.md` 0.19.3.

Previously current:

`0.19.2` — **Geocode-on-update**, same-day follow-up: "set latitude longitude for existing
branch" → clarified to a permanent fix, not a one-off. `BranchServiceImpl.update()` gets
the same geocode fallback `create()` has (blank lat/long only; an explicit pair, including
one that clears a previous geocode, is never second-guessed). **Using it live on the real
`PUNE` branch found a real bug**: `NominatimGeocodingService` mapped `district` straight to
Nominatim's structured `county` param, but this app's `district` is user-typed and often a
locality name ("Kothrud" for Pune), not a formal administrative district — an
over-constrained structured query silently returned zero results (`geocodeInto`'s
`ifPresent` logged nothing on a miss). Fixed: try with `county` first, retry without it on
a miss, log at INFO when both come back empty. Confirmed live: `PUNE` now geocodes to
`18.521374, 73.854507` via a real `PUT /branches/{id}`, distance to `MUMBAI_GEOTEST`
resolves to the same 148.7 km/119 min the two purpose-made test branches already gave. 4
new `BranchServiceImplTest` cases, `mvn test` 687 → 691. **Gap, not a decision**:
`NominatimGeocodingService` itself still has no unit test — exercised only through real
HTTP calls across three sessions now (0.19.0/0.19.1/0.19.2). Full detail in
`CHANGELOG.md` 0.19.2.

Previously current:

`0.19.1` — **Address Distance frontend page**, same-day follow-up to 0.19.0 ("address to
address menu?" → "keep going"). New `features/address-distance/` — one page, no separate
list/resolve split: a two-branch-picker "Resolve" card (mirrors Rate Calculator's own
picker shape, off `MasterDataService.branchDirectory()`) plus a table of previously
resolved pairs with Refresh/Delete (`DialogService.confirm()`, not a bare `confirm()`).
Branch-only, matching 0.19.0's own scope decision — no frontend for the backend's
`/customer-addresses` path. Nav leaf `/distances`, `COMPANY_ADMIN`/`BRANCH_MANAGER`, no
permission code (none exists server-side yet). **Live-testing this page immediately found
a real backend bug** 0.19.0's compile-only verification had missed: resolving a pair after
deleting it 409'd instead of computing a fresh row, because `uk_address_distance_pair`
isn't scoped by `deleted` (deliberately, mirroring `branches`' own code/name uniqueness) —
a plain insert after a soft delete collides with the still-there row. Fixed with a
check-first pattern (`AddressDistanceRepository.countDeletedPair`/`restoreAndUpdate`, both
native since `@SQLRestriction` hides deleted rows from HQL too) rather than catch-after-
`save()`, since a failed JPA flush leaves the persistence context unsafe to keep using in
the same transaction. `mvn test` 686 → 687, `ng build`/`tsc --noEmit` clean. **Verified
live end to end**: backend restarted against real MySQL, two branches created with blank
lat/long geocoded live via real Nominatim (both coordinates correct), their distance
resolved via real OSRM (148.7 km / 119 min Pune↔Mumbai, matches reality), every endpoint
exercised over raw HTTP, then the delete-then-resolve bug found through the actual browser
UI and confirmed fixed the same way after restart. Also fixed along the way: a YAML bug in
`application.yml`'s default `GEOCODING_USER_AGENT` (unquoted `:` inside parens, parsed as a
nested mapping) that broke every boot, caught before any of the above — the backend
wouldn't start until it was quoted. Side effect: `first.admin@gmail.com`'s password reset
to `Password@1234` (was undocumented-in-context at the time) — `login.ts` quick-fill and
`[[dev-login-credential]]` both updated. Full detail in `CHANGELOG.md` 0.19.1.

Previously current:

`0.19.0` — **Branch geocoding + Address Distance module**, first piece of a user-stated
plan ("company level charges based on distance and freight factor"). Two parts, backend
only, deliberately stopping short of any actual charge/pricing logic (a later, separate
ask): (1) `BranchServiceImpl.create()` now geocodes a branch's latitude/longitude (columns
existed since `V9`, unused until now) from its address fields when the administrator
leaves both blank — new `GeocodingPort` seam (mirrors `FileStoragePort`'s shape, opposite
failure stance: best-effort, never throws, a miss just leaves the branch as it would have
been), `NominatimGeocodingService` default (free/keyless OSM), `NoopGeocodingService` when
disabled. (2) New `com.courier.modules.distance` module: `V29` `address_distance` table
(one row = resolved road distance + travel time between two addresses of the *same* kind —
`address_type` `BRANCH`/`CUSTOMER`, `from_id`/`to_id` with no FK, same reasoning
`branches.manager_id` uses), `AddressDistanceService` (cache-or-resolve/get/search/refresh/
delete), `RoutingPort` seam (same shape as `GeocodingPort` but the *opposite* stance — an
explicit distance request surfaces a lookup failure as 503, never a silent miss) backed by
`OsrmRoutingService` (free/keyless OSRM demo server). **`CUSTOMER` reads
`CustomerAddress`'s coordinates, not `Customer`'s** — `fromId`/`toId` for that type are
`customer_addresses.id`; customer addresses are not geocoded on save (only branches are,
per this session's scope), so a `CUSTOMER` pair only resolves once an address already
carries lat/long by hand. 5 endpoints under `/api/v1/distances`, `isAuthenticated()`, no
new permission codes. `app.geocoding.*`/`app.routing.*` both on by default (free/keyless,
unlike S3/Razorpay there's no secret forcing an off switch) — both public services
explicitly not for production volume, self-hosting is the documented upgrade path. `mvn
test` 676 → 686 (9 new `AddressDistanceServiceTest` cases + `BranchServiceImplTest` updated
for the new constructor dependency). **Not verified live** — no local MySQL session this
task; `V29` unapplied, geocoding/routing unverified against the real network services,
compile- and unit-test-verified only. Full detail in `CHANGELOG.md` 0.19.0.

Previously current:

`0.18.1` — **Booking auto-saves sender/receiver as Customer**, direct user request, same
day as 0.18.0: "when i book shipment that time customer should be saved and for next
shipment search then should be search and show for suggestion" — the second half of a
two-part ask, the first half ("search by contact number and name then show suggested
customer") turning out to already be fully built, uncommitted, in `shipment-create.ts`
(300ms-debounced dropdown against `GET /customers?search=`, both parties, both fields —
nothing to add there). New `CustomerService.findOrCreateForBooking(fullName, mobile)`:
exact-mobile lookup (new `CustomerRepository.findByCompanyIdAndMobile`) or a bare
`INDIVIDUAL` customer created via the existing `create()` (name split on first space into
first/last), called twice from `ShipmentServiceImpl.create()` — sender and receiver — same
transaction as the booking, no separate failure-tolerant carve-out. `Shipment` still
carries no `customerId` FK; this only feeds Customer's own table so the next booking's
search has something to find. Deliberately not wired into `update()` — editing a shipment's
sender/receiver still doesn't touch Customer. `mvn test` 672 → 676. **Verified live twice**:
first over raw HTTP on a temporary `:8082` instance, then — on direct follow-up request
"test it live" — through the actual browser UI, restarting the user's own `:8081` on the
new build (`:4200` `ng serve` untouched) and driving Shipment Booking as `latur@gmail.com`:
typing an already-created mobile into the Consignor field surfaced it in the existing
suggestion dropdown, and a full booking through the real **Book Shipment** button
(`LATUR-000009`) produced an immediately-searchable new Customer, with the repeat sender
mobile resolving to the same customer id as the API-side test — reuse-not-duplicate holds
across both entry paths. Found live, not fixed (both pre-existing, unrelated to this
change): multi-word full-name search doesn't match (`CustomerSpecifications`' per-column
`LIKE`), and `ItemEntryGrid`'s default row visually shows a weight but an empty item name,
which gets silently dropped and 422s on submit unless the name is typed. Full detail in
`CHANGELOG.md` 0.18.1.

Previously current:

`0.18.0` — **Branch commission calculation on shipment booking**, direct user request across
several turns: percentages ("80% branch on other amount, 10%/10% split on basic freight, use
login branch's own config") → an Instant Commission toggle on Branch's Operations card →
restructuring storage into 4 named columns (`totalCommission`/`commissionOnBasicFreight`/
`branchCommissionOnOtherAmount`/`companyCommissionOnBasicFreight`) → surfacing all 4 in every
report. `Branch`'s own charge percentages (0.17.8) had no calculation behind them until now.
`shipment_charges` gains the 4-column breakdown (`V26` then `V28`), computed in
`ShipmentServiceImpl.copyCharge` from the **booking branch's own** percentages on every
booking/re-price. **`totalCommission` is all three lines summed** (fixed same day — user
caught it was only two: "total commision should 90 for order 26080000010" — was 85; see
`CHANGELOG.md` 0.18.0's same-day "Fixed" entry). `branches.instant_commission` (`V27`,
default true): when on, a PREPAID booking's wallet credit (new `WalletService
.creditCommission`, `COM` reason) uses **only the branch's own two lines**
(`commissionOnBasicFreight + branchCommissionOnOtherAmount`) — deliberately *not*
`totalCommission`, which also folds in the company's own cut and must never land in the
branch's wallet (a bug caught and fixed in the same pass as the formula fix, before it could
ship). Branch pays full freight at booking, earns its own commission back; when off, still
computed/stored, just not auto-credited. COD/TO_PAY: stored, never auto-credited — an
accepted, logged gap, same shape as other wallet seams. New `ShipmentService.chargesFor(ids)`
batch method attaches the breakdown to every list/report row (`ShipmentSummaryResponse`/
`ShipmentChargeResponse`). Frontend shows it on Shipment Details, Shipment Charges, Shipments
list/search, Booking Report (table + CSV all three), Delivery Report (CSV), and the Branch
form/view/summary card shows the Instant Commission toggle. `mvn test` 669 → 672, `ng build`/
`tsc --noEmit` clean, `ng test` 124/125 (pre-existing unrelated gap, see 0.16.9). **Verified
live end to end, twice the same day:** backend restarted against real MySQL, `V26`/`V27`/`V28`
applied clean, a real PREPAID shipment booked at Latur branch (`LATUR-000007`/tracking
`26080000010`, freight 50/otherCharges 100) — commission figures hand-verified against
Latur's own percentages, wallet ledger confirmed `SBK` 189.00 debit → `COM` 85.00 credit
chaining correctly, list/report endpoint confirmed carrying all 4 fields; then, after the
formula fix, re-priced the same live order (`PUT /shipments/{id}`) and confirmed
`totalCommission` now reads 90.00 (5+80+5) while the already-posted 85.00 wallet credit stays
untouched, per the project's own "update never touches the wallet" rule. Full detail in
`CHANGELOG.md` 0.18.0.

Previously current:

`0.17.9` — **POD upload to AWS S3**, direct user request ("POD upload while delivery
use aws s3 for POD and other image video document upload"), narrowed to POD only
(delivery Photo/Signature on the Delivery page) via an AskUserQuestion round — the
generic `shipment-documents` URL-entry feature stays untouched. New `FileStoragePort`
seam in `shipment/application/storage` + a new `shipment/infrastructure` package
(`S3FileStorage`/`UnconfiguredFileStorage`/`FileStorageConfig`), mirroring
`PaymentGatewayPort`'s exact shape — fails closed (422) when no bucket is configured,
the same choice Razorpay makes for the payment gateway. New
`ShipmentService.uploadPodFile` validates kind (`PHOTO`/`SIGNATURE`) and the original
filename's extension against an allowlist, stores to
`pod/<companyId>/<shipmentId>/<kind>-<uuid>.<ext>`, returns a URL that flows straight
into `deliver()`'s existing `signatureUrl`/`photoUrl` fields — `deliver()` itself is
unchanged. New endpoint `POST /shipment-movement/{shipmentId}/pod-upload` (multipart),
same `WRITERS` role gate as `deliver()`, not the seeded-but-unused
`SHIPMENT_UPLOAD`/`DELIVERY_UPLOAD` permission codes. Also created live via AWS CLI (not
code): S3 bucket `courier-saas-pod-547268988887` (private, SSE-S3), and an IAM role
scoped to `PutObject`/`GetObject` on `pod/*` only, attached to the existing EC2 dev
instance as its first instance profile. `mvn test` 665 → 669, `ng build`/`tsc --noEmit`
clean. **Verified live end to end**: local backend restarted with real S3 credentials,
browser flow (`claude-in-chrome`) uploaded a real file for both Photo and Signature on
an `OUT_FOR_DELIVERY` shipment, confirmed both objects landed in the bucket via
`aws s3 ls`, then completed the delivery successfully. **Not yet verified on the EC2
box itself** — the instance-role path is wired but unexercised there this session.
Full detail in `CHANGELOG.md` 0.17.9.

Previously current:

`0.17.8` — **Branch-level charge percentages**, direct user request: `Branch` gains
`gstPercentage` (default 18), `commissionOnOtherCharges` (company's commission on other
charges, default 20), `commissionOnBasicFreight` (default 10), and
`companyServiceChargePercentage` (default 10) — `V25`, all `DECIMAL(5,2)` 0–100,
optional-with-default on `CreateBranchRequest`, required on `UpdateBranchRequest` (full
replacement, same as every other editable field on that endpoint). Threaded through
`CreateBranchCommand`/`UpdateBranchCommand`/`BranchResponse`/`BranchMapper`/audit
snapshot. Frontend `BranchForm` gained a "Charges" card, prefilled with the defaults on
create; along the way `UiInput` (shared) gained `type="number"` support (`min`/`max`/
`step`), which it didn't have before — every prior numeric field in the app used a bare
native `<input type=number>` instead. `mvn test` 665/665 (two test call sites fixed for
the grown command records), `ng test` 124/125 (pre-existing unrelated nav gap, see
0.16.9). **Not verified live** — no working local MySQL session this task; `V25` not yet
applied against a real database. Full detail in `CHANGELOG.md` 0.17.8.

Previously current:

`0.17.7` — **Out For Delivery UX rework**, direct user request, same day as 0.17.5:
Delivery User is now picked *first* (its own `app-select`, nothing else visible until
it has a value), then a plain checkbox `<table>` of this branch's IN_SCAN shipments
appears — replacing the old multi-select-shipments-then-pick-user `app-select` pair.
Selection lives in a `Set<string>` signal (`selectedIds`, toggled by `toggleOne`
/`toggleAll`), not a reactive form array — simpler to check/uncheck than juggling
`FormArray` indices for a table row. Submit button renamed "Bulk Assign" → **"Generate
DRS"**, disabled until at least one row is checked. 0.17.5's `printDrs()`/
`printableShipments` untouched — only the selection UI upstream of `assign()` changed;
`assign()` now reads `Array.from(this.selectedIds())` instead of the form's
`shipmentIds` value. Verified live: table confirmed hidden pre-selection, checkbox +
select-all worked, Generate DRS assigned and produced the same Print DRS button as
0.17.5. `tsc --noEmit` clean on the file. Full detail in `CHANGELOG.md` 0.17.7.

Previously current:

`0.17.6` — **Other Charges on Shipment Booking**, direct user request: a manual numeric
field in the Rate Charges section of Booking, on top of the Pricing Engine's own
Freight/Fuel/Handling/ODA/Insurance/GST/Discount/Round Off lines. New migration `V24`
(`shipment_charges.other_charges`), threaded through Create/UpdateShipmentRequest/Command,
`ShipmentServiceImpl.copyCharge` (`netAmount = priced.netAmount() + otherCharges`), and
the wallet-sufficiency check + `PrepaidBookingConfirmed` event so a PAID booking with
Other Charges debits the correct total. Frontend: `ChargeSummary` gained an editable row
(mirrors the existing `manualNetAmount` override pattern, except this one is real — sent
to the server, not display-only); `ShipmentCreate` carries its own signal that survives a
reprice; `ShipmentEdit` fetches the persisted charge row to hydrate the field (needed
since `ShipmentResponse` doesn't carry it — skipping this would have silently zeroed it
on every edit). `mvn test` 665/665, `ng build`/`tsc --noEmit` clean. **Not verified live**
— no working local MySQL session this task; `V24` not yet applied against a real
database. Full detail in `CHANGELOG.md` 0.17.6.

Previously current:

`0.17.5` — **Print DRS (Delivery Run Sheet)** on Out For Delivery, on direct user
request ("Allocate order to delivery boy and allocated order list should be print").
Same client-side pattern 0.17.4's Print THC set: `OutForDelivery.printDrs()` opens a
new window, `document.write()`s a self-contained escaped HTML document, `win.print()`.
No PDF service, no new backend endpoint — every field the sheet needs (tracking no.,
receiver name/contact, payment mode, amount) is already on the list-row `Shipment` this
page holds before `assign()` submits, so no extra fetch either. Button appears on the
existing Result card only for shipments the bulk assign actually succeeded on (matched
back by `shipmentNumber`, the same string `MovementOutcome.reference` carries — not the
tracking number, despite the display column looking like one). Payment-mode label via
`MasterDataService.options('payment-modes')`, branch label via `branchDirectory()`,
same lookups THC already uses for vehicle/driver. Verified live: logged in as Pune
Branch, received a real DISPATCHED shipment via In Scan to get a genuine `IN_SCAN` row,
bulk-assigned it to "Pune User", confirmed "Print DRS" appeared, clicked it — no
console errors, matches THC's own documented limitation (`window.print()` blocks CDP
automation past that point, same as `confirm()`). `tsc --noEmit` clean on the file (only
pre-existing `.spec.ts` test-runner-global noise elsewhere, unrelated). No backend
changes — `assignOutForDelivery` (`SHIPMENT_MOVEMENT` module) already existed and does
the actual allocation; this pass only adds the print action on top of it.

`0.17.4` — **"Dispatch" renamed to "Trip Hire Challan (THC)"** + two real features, same
session as 0.17.3, on direct user request. Rename: mechanical, same treatment as 0.17.3
— `dispatch.ts` → `trip-hire-challan.ts`, component `Dispatch` → `TripHireChallan`,
selector `app-dispatch` → `app-trip-hire-challan`, route `/movement/dispatch` →
`/movement/trip-hire-challan`, nav title, breadcrumb, tour step, dashboard quick action
(`QA.dispatch` label → `THC`), `ManifestCard`'s own worklist button (`Dispatch` → `THC`).
Internal action names (`dispatch()`, `dispatching`, `DISPATCHED` status) intentionally
untouched — same "action stays, page label changes" split 0.17.1 set for `OUT_SCAN`.
**Print THC:** a plain browser print view — `window.open('', '_blank')` +
`document.write()` a self-contained HTML string (manifest number, vehicle, driver,
dispatched-at, LR table fetched fresh via `ManifestService.shipments`) + `win.print()`.
No PDF service, no new backend endpoint; every interpolated field passed through a
`.esc()` helper first since sender/receiver names are user-entered. Verified live: title
`THC MFT-260812-8308` set on the new tab confirms the fetch + write ran before
`print()` opened the native print dialog (which — like `confirm()` — blocks CDP
automation; verification stopped at that point, same limitation, not a functional gap).
**Remove shipment from Loading Sheet:** new backward edge `MANIFEST_CREATED` -> `BOOKED`
in `ShipmentStatus` (the one backward edge in that graph) + `ShipmentService
.detachFromManifest` (inverse of `attachToManifest`) + `ManifestService.removeShipment`
(refuses an already-dispatched manifest) + `DELETE /api/v1/manifests/{id}/shipments
/{shipmentId}` + new `AuditAction.MANIFEST_SHIPMENT_REMOVED`. Frontend: `ManifestCard`
owns the mutation itself (new `showRemoveAction` input, `removed` output) rather than
delegating to a parent, the same way it already owns fetching its own shipment list —
wired true only from Loading Sheet (a manifest is always still `CREATED` there), not
from THC or In Scan's reuse of the same card. **First attempt used a bare `confirm()`**
— broke live-testing (blocks CDP the same way `print()` does) and was inconsistent with
every other feature's `DialogService.confirm()`; fixed to match convention before this
landed, not left as a known gap. Verified live end-to-end: created a manifest, removed
its one shipment (weight/parcels dropped to 0, "No shipments on this manifest."),
confirmed the shipment reverted to BOOKED (reappeared in Loading Sheet's own eligible-
branch/booked-shipment list), re-added it, dispatched, printed. `mvn compile` and
`ng build` both clean. Full detail in `CHANGELOG.md` 0.17.4.

Previously current:

`0.17.3` — **"Out Scan" renamed to "Loading Sheet"**, on direct user request, across
every layer: frontend page (`out-scan.ts` → `loading-sheet.ts`, component `OutScan` →
`LoadingSheet`, selector `app-out-scan` → `app-loading-sheet`), route
(`/movement/out-scan` → `/movement/loading-sheet`), nav item title, breadcrumb, tour
step (element/data-tour id, popover copy), dashboard quick action (`QA.outscan` →
`QA.loadingSheet`), AI command router (kept `outscan`/`out scan` as legacy voice
synonyms, added `loading sheet`), the `MANIFEST_CREATED` status display label ("Out
Scan Created" → "Loading Sheet Created" — `ShipmentStatusBadge` frontend, mirrored in
backend `ShipmentServiceImpl.TIMELINE_LABELS`), and every doc comment on both sides
that named it. No DB/enum change — `OUT_SCAN`/`out-scan` as historical identifiers
(applied `V20` migration filename, the dead `/shipment-movement/out-scan` endpoint
reference in `ManifestController`'s javadoc) were left alone, since those name a past
state, not the current UI. Found and fixed in passing, not part of the rename itself: two
Swagger descriptions (`ShipmentController.getTimeline`, `TimelineStepResponse`) still
listed `Out Scan` as its own arrow between `Manifest Created` and `Dispatched`, stale
since V20 folded it away — removed the dangling step; `ShipmentMovementController`'s
`@Tag` description likewise still listed five verbs for four remaining endpoints,
trimmed. Verified: `mvn compile` clean, `ng build` clean, `ng test` on
`navigation.config.spec.ts` — 11/12 pass, the one failure (`reports-dashboard` nav
node missing) pre-existing and unrelated (Reports section is aspirational, no route
behind it). Not otherwise live-verified this session.

Previously current:

`0.17.2` — **COD delivery debit seam**, closed on live user report: shipment
`26080000004` (COD) delivered with nothing debited from its delivery branch. Root
cause: `SubTransactionType.COD` existed in the wallet module since it shipped, but
nothing ever constructed a transaction with it — `ManifestServiceImpl`/
`ShipmentServiceImpl`'s delivery flow had zero wallet wiring, only the booking-time
`PAID` debit (`ShipmentBookingWalletListener`) existed. Fixed by mirroring that exact
seam on the delivery side: `WalletService.debitForCodDelivery(CodDeliveryDebitCommand)`
(reason `COD`, reference `SHIPMENT`, `isAuthenticated()` not `COMPANY_ADMIN`-gated,
same `resolveBranchForWrite` scoping) + `ShipmentEvent.CodCollectedAtDelivery`,
published from `ShipmentServiceImpl.deliver()` when `paymentMode.isCollectAtDelivery()`
(true for both `TO_PAY` and `COD` — the flag the payment-mode module already exposes,
no new one added), amount = the shipment's persisted `ShipmentCharge.netAmount`, debited
from the *delivery* branch, not booking. Handled AFTER_COMMIT/`REQUIRES_NEW` by a new
`ShipmentDeliveryWalletListener`, same accepted-gap shape as the booking listener (a
debit failure leaves the shipment DELIVERED, undebited, for manual reconciliation — not
swept under "impossible"). `mvn test` 664 → 665: 1 new COD-event test, 1 new negative
(PAID path publishes nothing), plus `deliverHappyPath` itself needed a
`paymentModeService` stub it never had — latent because nothing in `deliver()` read
that collaborator before this change. **Not verified live** — no working local MySQL
credentials this session (root/no-password and courier/courier both refused); the fix
is compile- and unit-test-verified only. Full detail in `CHANGELOG.md` 0.17.2 and
`MEMORY/modules/branch-wallet.md` / `shipment-movement.md`.

Previously current:

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

**Build status:** `mvn compile` clean · `mvn test` **665 pass of 665**.
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
