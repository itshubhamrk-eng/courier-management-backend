# Hub Operations

v1.0, 2026-09-21. Direct request: a complete hub operations workflow — receive/in-scan,
shipment list at hub, sorting, Load Sheet creation, vehicle/driver assignment, out-scan,
dispatch, exceptions, a hub dashboard, movement history, and flexible routing (`Branch →
Delivery Branch`, `Branch → Hub → Delivery Branch`, `Branch → Hub → Hub → Delivery
Branch`, `Branch → Company Vehicle → Direct Delivery`). See `CHANGELOG.md` for the
narrative version of this entry.

## The one fact that shapes everything else

**A Hub is a `Branch` row with `branch_type = 'HUB'` (`V61`), not a new entity.** There is
no `hubs` table, no `Hub` Java class. Every decision below follows from taking that
literally rather than building a parallel module.

## Decisions

1. **Hub = `Branch{branchType: HUB}`.** `GET /branches?branchType=HUB` lists hubs; create/
   edit/activate/deactivate reuse the Branch screens and endpoints as-is. The frontend's
   `features/hub/` (previously a disabled stub referencing a nonexistent `/hubs`
   endpoint) is now a thin filtered view over `BranchService`.
2. **Hub staffing reuses `users.branch_id`**, exactly like a regular branch — **not** the
   separate, still-unused `users.hub_id` column. A `HUB_MANAGER`'s `branch_id` points at
   the hub's `Branch` row. This is what makes every existing "defaults to my own branch"
   screen (Loading Sheet, Trip Hire Challan/Dispatch, In Scan) work for a hub with **zero
   frontend code changes** — they are reused wholesale, routed again under
   `/hub-operations/*` with the same component. `users.hub_id` is untouched, unused
   legacy (as it already was before this module).
3. **In-scan/receive is entirely reused**, not rebuilt: `ShipmentService.inScan`/
   `scanOneIn` plus `com.courier.modules.crossing`'s existing `CrossingService.arriveAt`
   already implement "shipment arrives at an intermediate hub" — it completes the
   shipment's current crossing hop and transitions it to `READY_FOR_MANIFEST` (not
   `IN_SCAN`, which is reserved for arrival at the *final* delivery branch). Multi-hop
   (`Branch → Hub → Hub → Delivery Branch`) was already covered by existing tests
   (`attachAcceptsSecondLegAfterCrossingHop`, `inScanAtLastCrossingHopRoutesToDeliveryBranch`)
   before this module touched anything.
4. **Sorting needed no new backend mutation.** `GET /manifests/eligible-destinations`
   (destination-city grouping) and `GET /shipments?currentLocationId=` (shipments at a
   location) already give the "group shipments at this hub by destination" view; the
   existing Loading Sheet screen (`features/shipment-movement/loading-sheet.ts`) already
   *is* the destination-grouped multi-select → create Load Sheet flow, city mode and
   crossing-hub mode both. It is reused wholesale for both the "Sorting" and "Load
   Sheets" menu leaves — same component, two routes.
5. **New backend code lives in `com.courier.modules.crossing`**, extended in place per
   direct instruction, rather than a new `modules.hub` package — it already owns "a
   shipment's movement between branches" and has no reverse-dependency problem with
   `manifest`/`shipment`. It calls into `ShipmentService`/`ManifestService` rather than
   duplicating their logic.
6. **Out-scan gates dispatch only for hub-originated manifests.**
   `ManifestServiceImpl.dispatch()` gained one additive check: if the manifest's booking
   branch has `branchType == HUB`, every `MANIFEST_CREATED` shipment on it must already
   have a `HubOutScan` row, or dispatch is refused. A manifest booked from an ordinary
   branch is completely unaffected — confirmed live by dispatching a real non-hub
   manifest with zero out-scan rows and no change in behaviour.
7. **Exceptions never touch `Shipment.status`.** There is no exception state in
   `ShipmentStatus` and none was added — a shipment stuck at a hub because something is
   wrong with it stays exactly where its own state machine left it; a human resolves the
   exception explicitly. `ShipmentException` is its own append-only-per-raise incident
   table, plus a best-effort auto-raised support ticket (same graceful-degradation
   pattern as `ShipmentServiceImpl.raiseShortageTicket` — a missing "Shipment Issue"
   category just skips the ticket with a warning).
8. **Permission codes**: reuse `MANIFEST_CREATE`/`MANIFEST_READ`/`MANIFEST_DISPATCH` for
   Load Sheet/vehicle-driver/dispatch, `HUB_READ` (alias `HUB_VIEW`) for hub visibility,
   and `REPORT_READ` for reports. Added exactly 4 new `PermissionAction` values —
   `IN_SCAN`, `OUT_SCAN`, `SORT`, `EXCEPTION_MANAGE` — seeded onto `PermissionModule.HUB`
   alongside the pre-existing `DISPATCH`, giving `HUB_IN_SCAN`/`HUB_OUT_SCAN`/
   `HUB_SORT`/`HUB_DISPATCH`/`HUB_EXCEPTION_MANAGE`.
9. **Existing companies' `HUB_MANAGER` role is not backfilled** with the new codes — same
   rule `V13` established: widening a role's grants silently is not a migration's call to
   make. New companies pick up the wider `HUB_MANAGER` automatically (`DefaultRoleCatalog`
   derives it from the catalogue). An existing company's `COMPANY_ADMIN` re-grants the new
   codes through the existing Permission Management screen (`POST
   /roles/{roleId}/permissions`) — exercised live in verification.
10. **Two new cross-module ports**, both owned by the consumer (`manifest`), matching the
    `CrossingBranchDirectoryPort` seam exactly:
    - `manifest.domain.BranchDirectoryPort` (`BranchRef` carries `branchType`), adapter
      `company.infrastructure.ManifestBranchDirectory`.
    - `manifest.domain.HubOutScanCheckPort`, adapter
      `crossing.infrastructure.HubOutScanCheckAdapter`.
11. **Hub Dashboard is its own endpoint/screen**, not an extension of the shared,
    company-wide `DashboardServiceImpl` — `GET /hub-operations/dashboard`, backed by
    `HubOperationsService.dashboard()`. Kept the two modules fully decoupled; the
    company-wide dashboard was not touched at all.
12. **Movement history is read-only reuse.** No new column on `shipment_status_history`.
    Vehicle/driver/Load Sheet number for a movement row are resolved at read time by
    joining `manifestId` → `Manifest`, not stored redundantly.

## Database (`V88`)

- `shipment_exceptions` — `company_id`, `shipment_id`, `hub_branch_id`, `exception_type`
  (`MISSING|DAMAGED|SHORT|WRONG_DESTINATION|MISROUTED|ON_HOLD`), `status`
  (`OPEN|RESOLVED`), `remarks`, `raised_by`, `raised_at`, `resolved_by`, `resolved_at`,
  `resolution_remarks`, `ticket_id`.
- `hub_out_scans` — `company_id`, `manifest_id`, `shipment_id`, `hub_branch_id`,
  `scanned_by`, `scanned_at`. Unique `(company_id, manifest_id, shipment_id)` — the
  structural duplicate-scan guard.
- 5 new permission rows (`HUB_DISPATCH`, `HUB_IN_SCAN`, `HUB_OUT_SCAN`, `HUB_SORT`,
  `HUB_EXCEPTION_MANAGE`).
- 10 new `menu_items` rows: a "Hub Operations" parent plus Dashboard/In Scan/Shipments At
  Hub/Sorting/Load Sheets/Out Scan/Dispatch/Exceptions/Reports leaves.
- No `role_permissions` backfill (see decision 9).

## API — the genuinely new endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/hub-operations/out-scan` | `{manifestId, hubBranchId, trackingNumbers[]}` → `MovementOutcomeResponse[]` |
| POST | `/api/v1/hub-operations/exceptions` | `{shipmentId, hubBranchId, exceptionType, remarks?}` |
| PATCH | `/api/v1/hub-operations/exceptions/{id}/resolve` | `{resolutionRemarks?}` |
| GET | `/api/v1/hub-operations/exceptions/{id}` | |
| GET | `/api/v1/hub-operations/exceptions` | filter: `shipmentId`, `hubBranchId`, `status` |
| GET | `/api/v1/hub-operations/dashboard?hubBranchId=` | the 8 `HubDashboardStats` figures |

Everything else (in-scan, hub list, shipments-at-hub, sorting, Load Sheet, vehicle/driver
assignment, dispatch) reuses an existing endpoint unchanged — see decisions 1, 3, 4, 6.

## The real bug live verification found (mocked tests could not)

`HubOperationsServiceImpl.outScanOne`'s first draft caught `DataIntegrityViolationException`
around a plain `save()`, expecting the unique-constraint violation to surface there for a
duplicate scan. Against real MySQL it didn't: `save()` only queues the `INSERT` for the
transaction's end-of-method flush, so the exception was thrown (and the `catch` long
since exited) after the method had already returned a false "success". Worse, once that
uncaught flush failure did occur, it left the Hibernate session unusable for the rest of
the *same bulk call*'s other tracking numbers — a second, unrelated item in the same
`outScan(...)` request 500'd too, instead of getting its own per-item outcome. Fixed by
pre-checking `HubOutScanRepository.existsByCompanyIdAndManifestIdAndShipmentId(...)`
before the insert, rather than catching a flush failure; the unique constraint remains as
the structural backstop against a genuine race between two concurrent requests. Confirmed
live with the exact batch that broke it (a duplicate scan and a wrong-manifest scan in one
call) now returning two clean per-item outcomes with no exception at all.

## Tests

- `HubOperationsServiceImplTest` (new, 10 tests): out-scan rejects unknown tracking
  number/wrong manifest/wrong status/duplicate; `raiseException` never touches
  `Shipment.status`, raises a ticket when the category exists and skips it gracefully
  when it doesn't; `resolveException` happy path and refuses-twice; `dashboard` scopes
  every figure to the given hub branch.
- `ManifestServiceImplTest`: `dispatchRefusesHubManifestWithoutOutScan`,
  `dispatchSucceedsHubManifestWhenFullyOutScanned` — existing `dispatchHappyPath`/
  `dispatchRefusedTwice` (non-hub) confirmed unaffected.
- `HubOperationsService.spec.ts` (frontend, 4 tests): out-scan/raise/resolve/dashboard
  HTTP shape.
- Full suite: `mvn test` 1096/1096, `ng build` clean, `ng test` clean (one pre-existing,
  unrelated `navigation.config.spec.ts` failure — a `reports-dashboard` nav-node
  assertion, not touched by this change, fails identically on `main`).

## Verified live

Throwaway backend on `:8082` (`local` profile) against real `courier_db` (MySQL root,
per `mysql-root-password` memory) — `V88` applied clean. Used the `LOADTEST01` company
("Load Test Disposable Co", an existing disposable fixture pool — never touched a real
account's credentials): created a real `Branch{branchType: HUB}` via `POST /branches`,
staffed it (`PATCH /users/{id}/branch` to the hub, `POST /users/{id}/roles` for
`HUB_MANAGER`, removed the legacy `BRANCH_MANAGER` company-role grant to isolate the
test), re-granted the new `HUB_*`/`MANIFEST_*` codes to this pre-`V88` company's
`HUB_MANAGER` role through the real Permission Management endpoint and confirmed the
JWT carried them on next login. Then: `GET /hub-operations/dashboard` (real empty-state
figures), raised and resolved a real exception against a real `BOOKED` shipment
(confirmed status unchanged throughout, a real "Shipment Issue" ticket auto-raised),
out-scanned a real `MANIFEST_CREATED` shipment, rejected an unknown manifest (404),
rejected a shipment not on that manifest, rejected a duplicate scan (after the fix
above, in the same batch as another failure), and dispatched a real **non-hub** manifest
with zero out-scan rows to confirm the gate is hub-only.

**Not exercised live** (covered only by the mocked `ManifestServiceImplTest`): dispatch
of a real **hub-originated** manifest, both the refused-without-out-scan and
succeeds-once-fully-scanned paths — building that live requires a full shipment booking
+ crossing-hop arrival at the hub, which needs the Shipment Booking wizard's customer/
pricing/package payload, out of scope for this pass. Low risk: the mocked test exercises
the exact same `ManifestServiceImpl.dispatch()` code path, same `BranchDirectoryPort`/
`HubOutScanCheckPort` wiring, that the live-verified non-hub path also runs through.

## Known follow-ups, not done here

- Frontend `dashboard.roles.ts`'s `activeHubs` stat tile and `hubs` quick action stayed
  commented out — wiring them needs a company-wide `activeHubs` count on
  `DashboardStatisticsResponse`, unrelated to hub-scoped work and out of scope.
- `/hub-operations/reports` reuses the existing Booking Report as-is rather than a
  hub-specific report; a real "Hub Reports" screen (todays' in/out volumes, hub-wise
  throughput) is a reasonable next increment.
- A live hub-originated dispatch walkthrough (see above).
