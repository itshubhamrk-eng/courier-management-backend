# Legacy consignment import — design

Date: 2026-11-18. Status: awaiting approval.

## Intent
The company ran on an older software. Its export (`Consignments_*.xls`, 2,869 rows,
all dated 11/2026) must be brought into this system as **history**, so tracking/search/
reports show those shipments. Re-uploading a newer export must be safe.

## Goals
- Upload `.xls` (the old tool writes an HTML table named .xls), real `.xls`, `.xlsx`.
- Per row, look up the consignment number as `tracking_number` in the company.
  - Found -> **EXISTING**: never modified. Report differences (date, consignee, weight,
    packages, total, destination) as informational "differs" so the user can look.
  - Not found -> **NEW**: booked as history.
  - Bad row (no number, unparsable date, duplicate number inside the file) -> **INVALID**, reason shown.
- Preview step (dry run, writes nothing) then explicit commit.
- Per-origin branch mapping (`From` text -> booking branch), chosen in the preview, remembered for next uploads. Commit blocked while any NEW row's origin is unmapped.
- Result report (downloadable CSV): per row outcome.
- Idempotent: committing the same file twice creates nothing the second time.
- Batch record + `legacy` marker on each shipment, so a batch can be audited / rolled back.

## Non-goals
- No pricing, no wallet debit, no commission, no WhatsApp/SMS/email, no events published.
- No manifests/DRS/POD for imported rows. No customer master creation.
- No updating existing shipments from the file.

## Anti-goals (failure even if it "works")
- Any wallet movement, notification, or commission job touching an imported row.
- Imported rows inflating live dashboards (pending/in-transit counts) or being picked by commission/billing jobs.
- Clobbering a live shipment because the old file disagrees with it.

## Constraints (found in code)
- `ShipmentServiceImpl.create()` prices, debits wallet, publishes `Booked`/`PrepaidBookingConfirmed`, generates numbers. Not reused.
- `shipments` has NOT NULL pincodes, sender/receiver address+contact, service type, payment mode, declared value. File lacks them -> placeholders.
- `uk_shipments_company_number` / `_tracking`: old consignment no. goes in both columns.
- Flyway: next migration is V145 (verify at build time; commit in order).
- New permission module needs a COMPANY_ADMIN backfill migration (V105-V107 precedent).
- POI already on classpath (`DistrictLevelFreightExcelImportService` is the pattern).
- S3/file storage not needed: file is parsed in-memory.

## Approaches
A. **Dedicated `com.courier.modules.legacyimport` module, direct persistence (chosen by user).** Isolation, no side-effect risk, batch audit. Cost: small duplication of Shipment mapping.
B. Legacy flag inside `create()` — rejected: one missed check = real money/WhatsApp side effect.
C. One-off SQL — rejected: no UI/preview/re-run safety.

## Design
Data model (V145, plus permission backfill in V146):
- `legacy_import_batches` (id, company_id, file_name, row counts new/existing/invalid, status, created_by/at, company-owned like other tables).
- `legacy_origin_branch_map` (company_id, origin_text normalised upper/trim, branch_id) unique on (company, origin).
- `shipments.legacy_import` boolean default false, `shipments.legacy_batch_id` nullable. No physical FK to other modules' tables beyond project convention.

Imported shipment row:
- trackingNumber = shipmentNumber = consignment number (trimmed).
- bookingDate = file Date; bookingBranch = mapped branch; fromCity = origin text; toCity = `To` text.
- status DELIVERED; currentLocationId = bookingBranchId; deliveryBranchId null.
- sender = Consignor, receiver = Consignee; addresses "N/A (legacy import)", contacts "0000000000", pincodes "000000" (placeholders).
- actualWeight/chargeableWeight = file weight (0 allowed, bypasses `applyInvariants` since builder+save skips it), volumetric 0, packages = file Package (min 1), declaredValue = Total if > 0 else 1? -> placeholder 1.00 flagged (builder may refine).
- service type / payment mode: company's default service type, and the company's PAID mode (lookup by code at commit; commit blocked with a clear message if none).
- `podApproved=true`, `commissionCredited=true`, `bookingCommissionCreditedAt` null — so no job ever credits.
- One `ShipmentCharge` row: freight = Total, net = Total, everything else 0.
- One status-history row: null -> DELIVERED, remarks "Imported from legacy system (batch <id>)".
- Audit record `LEGACY_IMPORT_COMMITTED` per batch (not per row).
- No `ApplicationEventPublisher` calls. Imported rows excluded from shipment queries that feed commission, wallet, daily-summary, pending-work dashboards (builder to enumerate by grepping `status`-based queries; default filter `legacy_import = false` on operational dashboards, included in list/search/reports).

API (`/api/v1/legacy-import`, permission `LEGACY_IMPORT_CREATE`/`_READ`, COMPANY_ADMIN only):
- `POST /preview` multipart file -> rows summary, per-origin list with suggested branch, per-row classification (paged), file hash.
- `PUT /origin-map` save origin -> branch.
- `POST /commit` multipart file + (server re-parses; never trusts client row data) -> batch result.
- `GET /batches`, `GET /batches/{id}/report.csv`.
Commit processes in chunks (e.g. 200 rows per transaction); a failing row is recorded INVALID, others proceed. Re-check existence inside the transaction (unique constraint is the backstop).

Frontend `features/legacy-import`: upload -> summary tiles (New / Existing / Differs / Invalid) -> origin-mapping table -> row table with filter -> Commit -> result + CSV download. Nav under Settings/Masters area, COMPANY_ADMIN-only.

## Tests / verification
- Unit: HTML-table parser, date `dd/MM/yyyy`, classification, placeholder mapping, no-events assertion.
- Integration: preview writes nothing; commit twice = idempotent; existing row untouched; wallet balance and event listeners unchanged.
- Live on verification ports (:8082/:4300) with the real file against dev `courier_db`; fixtures kept (per standing rule).

## My calls (not yet confirmed by user)
1. Existing shipments are never updated; differences are reported only.
2. declaredValue placeholder when Total is 0.
3. Default service type / PAID payment mode resolved per company; blocked with message if missing.
4. `legacy_import` excluded from operational dashboards, kept in lists/search.
5. Real `.xls`/`.xlsx` support via POI in addition to the HTML-table format.

## Left to the builder
Exact class/package layout, migration numbers at build time, dashboard-query enumeration, CSV columns, paging sizes.
