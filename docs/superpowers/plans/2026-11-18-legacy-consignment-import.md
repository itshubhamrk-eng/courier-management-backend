# Legacy Consignment Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upload the old software's consignment export, preview it, map origins to branches, and book missing consignments as history-only DELIVERED shipments.

**Architecture:** New backend module `com.courier.modules.legacyimport` (api/application/domain) persisting `Shipment`, `ShipmentCharge`, `ShipmentStatusHistory` directly — never calling `ShipmentServiceImpl.create()`, never publishing events. Server re-parses the file on commit. New Angular feature `features/legacy-import`.

**Tech Stack:** Spring Boot 3 / JPA / Flyway / Apache POI (already in pom), Angular (signals, OnPush, vitest).

**Spec:** `docs/superpowers/specs/2026-11-18-legacy-consignment-import-design.md`

## Global Constraints
- Existing shipments are NEVER modified; differences are reported only.
- Imported shipment: `status=DELIVERED`, `trackingNumber == shipmentNumber == consignment no (trimmed)`, `podApproved=true`, `commissionCredited=true`, placeholders: addresses `N/A (legacy import)`, contacts `0000000000`, pincodes `000000`, declaredValue `1.00` when Total is 0 else Total.
- No wallet debit, no commission, no WhatsApp/SMS/email, no `ApplicationEventPublisher` call anywhere in the module.
- Migrations: next free number (V145 at plan time; re-check `ls db/migration | sort -V | tail`), commit in order. Permission module needs COMPANY_ADMIN backfill (V107 pattern).
- Role gate: `hasRole(COMPANY_ADMIN)` plus permission codes `LEGACY_IMPORT_CREATE`, `LEGACY_IMPORT_READ`.
- Entities company-owned: repeat `@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")` on each; single-row loads via `findByIdWithinCompany`.
- Verify only on :8082 (profile `local`) / :4300; never touch real :4200/backend. Keep test data in dev DB. MySQL root pw `Root@1234`. After finishing: update `MEMORY/CHANGELOG.md` + `MEMORY/AI_CONTEXT.md`.

## Review Focus
1. Same file committed twice -> second run creates 0 shipments (test in Task 4).
2. Duplicate consignment number inside one file -> both rows INVALID, none booked (Task 3).
3. Origin text variants (`SANGALI ` trailing space, case) -> normalised key `trim().toUpperCase()` (Task 3).
4. Date not `dd/MM/yyyy`, blank consignment no, non-numeric weight/total -> INVALID with reason, rest of file proceeds (Tasks 2, 4).
5. Company has no PAID payment mode / no service type -> commit refused with message, nothing written (Task 4).

## File Structure
Backend `backend/src/main/java/com/courier/modules/legacyimport/`:
- `domain/LegacyImportBatch.java`, `LegacyImportBatchRepository.java`, `LegacyOriginBranch.java`, `LegacyOriginBranchRepository.java`
- `application/LegacyFileParser.java` (pure, no Spring), `LegacyRow.java` (record), `LegacyImportService.java` (preview/commit), `LegacyShipmentWriter.java` (per-row transactional insert)
- `api/LegacyImportController.java`, `api/dto/*`
Modified: `modules/shipment/domain/Shipment.java` (2 fields), `modules/company/domain/PermissionModule.java`, dashboard/commission queries (Task 5).
Frontend `frontend/src/app/features/legacy-import/` (page, service, routes) + nav/route registration.

---

### Task 1: Schema, permission, entities

**Files:** Create `db/migration/V145__legacy_consignment_import.sql`, `V146__company_admin_legacy_import_permissions.sql`, the four domain classes; Modify `Shipment.java`, `PermissionModule.java`.

**Interfaces — Produces:**
- `Shipment.legacyImport: boolean` (column `legacy_import`, default false), `Shipment.legacyBatchId: UUID` (`legacy_batch_id`, nullable BINARY(16)). Builder defaults false/null; both `updatable=false`.
- `LegacyImportBatch` (company-owned): `fileName`, `fileHash`, `newCount`, `existingCount`, `invalidCount`, `status` (`COMMITTED`), `createdBy`.
- `LegacyOriginBranchRepository.findAllByCompanyId(UUID): List<LegacyOriginBranch>`; `LegacyOriginBranch{originKey:String, branchId:UUID}` unique `(company_id, origin_key)`.
- `PermissionModule.LEGACY_IMPORT("Legacy Import", <free display order>)`.

- [ ] **Step 1:** Write test `LegacyImportMigrationIT`-style (follow existing Flyway/`@DataJpaTest` pattern in `src/test`) asserting a `Shipment` saved with `legacyImport=true` round-trips and default is false.
- [ ] **Step 2:** Run `mvn -q -Dtest=LegacyImport* test` — expect FAIL (columns missing).
- [ ] **Step 3:** Write V145 (tables `legacy_import_batches`, `legacy_origin_branch`, `ALTER TABLE shipments ADD legacy_import TINYINT(1) NOT NULL DEFAULT 0, ADD legacy_batch_id BINARY(16) NULL`, index on `(company_id, legacy_import)`; plus `permissions` rows `LEGACY_IMPORT_CREATE/_READ` per V106 insert style). V146 = V107-style COMPANY_ADMIN backfill for module `LEGACY_IMPORT`.
- [ ] **Step 4:** Add entity fields/classes, enum value. Run tests — PASS. `mvn -q test` full still green.
- [ ] **Step 5:** Commit `feat(legacy-import): schema, permission module, entities`.

### Task 2: File parser

**Files:** Create `application/LegacyRow.java`, `application/LegacyFileParser.java`; Test `LegacyFileParserTest` (fixture: 3-row HTML table copied from the real file head, a real `.xlsx` built in-test with POI).

**Interfaces — Produces:**
```java
public record LegacyRow(int rowNo, String consignmentNo, LocalDate date, String consignor,
                        String consignee, String from, String to, int packages,
                        BigDecimal weight, BigDecimal total, String error) {}
public final class LegacyFileParser {
    public static List<LegacyRow> parse(String fileName, byte[] bytes);   // never throws on bad rows
}
```
Rules: sniff bytes — starts (after BOM/whitespace) with `<table`/`<` -> HTML-table path (regex over `<tr>`/`<td|th>`, unescape entities, trim); else POI `WorkbookFactory` + `DataFormatter`. Header-name matching case-insensitive on: Date, Consignment No, Consignor, Consignee, From, To, Package, Actual Weight, Total; missing required header -> `BusinessRuleException("Missing column: X")`. Row error (non-null `error`, other fields best-effort) for: blank consignment no, date not `dd/MM/yyyy`, non-numeric weight/total, packages < 1 -> treated as 1 (not error). Consignment no max 30 chars else error. Reject file > 10 MB / > 20,000 rows with `BusinessRuleException`.

- [ ] **Step 1:** Tests: `parsesHtmlTableExport` (row 1 => `SO912C2627215`, 2026-11-15, RIYANSH MULTITRADE PVT LTD, BHOSARI→CHINCHWAD, pkg 1, 3.82, 57.30); `parsesXlsx`; `badDateYieldsRowError`; `blankConsignmentYieldsRowError`; `missingHeaderRejectsFile`; `trimsConsigneeTrailingSpace`.
- [ ] **Step 2:** Run — FAIL. **Step 3:** Implement. **Step 4:** Run — PASS. **Step 5:** Commit `feat(legacy-import): xls/html/xlsx parser`.

### Task 3: Preview + origin mapping

**Files:** Create `application/LegacyImportService.java` (preview part), DTOs; Test `LegacyImportServicePreviewTest`.

**Interfaces — Produces:**
```java
enum RowOutcome { NEW, EXISTING, INVALID }
record PreviewRow(int rowNo, String consignmentNo, RowOutcome outcome, String reason,
                  List<String> differences, String origin) {}
record OriginSummary(String originKey, int newRows, UUID mappedBranchId) {}
record PreviewResult(String fileHash, int total, int newCount, int existingCount, int differsCount,
                     int invalidCount, List<OriginSummary> origins, List<PreviewRow> rows) {}
PreviewResult LegacyImportService.preview(String fileName, byte[] bytes);        // writes nothing
void LegacyImportService.saveOriginMapping(String originText, UUID branchId);    // validates branch in company, allowBooking
```
Classification: duplicate consignment no within file -> every occurrence INVALID "Duplicate in file"; else lookup `ShipmentRepository.findByCompanyIdAndTrackingNumber` (add a batch `findAllByCompanyIdAndTrackingNumberIn` if N+1 shows) -> EXISTING (differences compare bookingDate, receiverName, numberOfPackages, actualWeight, net total from `ShipmentCharge`, toCity — all case-insensitive/trimmed) else NEW. `originKey = from.trim().toUpperCase()`; unmapped origins return `mappedBranchId=null`.

- [ ] **Step 1:** Tests: `newVsExisting`, `existingWithSameValuesHasNoDifferences`, `existingWithDifferentWeightListsDifference`, `duplicateInFileBothInvalid`, `originKeyNormalisesWhitespaceAndCase`, `previewWritesNothing` (counts of shipments/batches unchanged), `saveOriginMappingRejectsForeignCompanyBranch`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit `feat(legacy-import): preview + origin mapping`.

### Task 4: Commit writer

**Files:** Create `application/LegacyShipmentWriter.java`; extend `LegacyImportService`; Test `LegacyImportCommitTest`.

**Interfaces — Produces:**
```java
record CommitResult(UUID batchId, int created, int skippedExisting, int invalid, List<PreviewRow> rows) {}
CommitResult LegacyImportService.commit(String fileName, byte[] bytes);
// LegacyShipmentWriter: @Transactional(propagation = REQUIRES_NEW)
UUID LegacyShipmentWriter.insert(LegacyRow row, UUID branchId, UUID serviceTypeId, UUID paymentModeId, UUID batchId);
```
Behaviour: refuse (`BusinessRuleException`, nothing written) if any NEW row's origin unmapped, or company lacks a PAID payment mode (`PaymentModeRepository.findByCodeWithinCompany("PAID", companyId)`) or any service type (first by code). Create the batch row first, process rows in order, one `REQUIRES_NEW` tx per row; per-row failure (incl. unique-constraint race) -> INVALID/EXISTING in result, others continue. Writer persists Shipment (per Global Constraints; `fromCity=origin text`, `toCity=To`, `currentLocationId=bookingBranchId`, `expectedDeliveryDate=bookingDate`, `legacyImport=true`, `legacyBatchId`), one `ShipmentCharge` (freight = net = Total, rest zero; follow `ShipmentCharge` not-null defaults), one `ShipmentStatusHistory` (null -> DELIVERED, remarks `Imported from legacy system (batch <id>)`). Does NOT call `applyInvariants()`. Audit once per batch (`AuditService`, new action `LEGACY_IMPORT_COMMITTED`). Batch counts updated at end.

- [ ] **Step 1:** Tests: `commitCreatesDeliveredHistoryShipment` (assert status, numbers, flags, 1 charge, 1 history); `commitTwiceIsIdempotent` (second `created==0`, `skippedExisting==n`); `existingShipmentUntouched` (version unchanged); `noEventsPublished` (spy `ApplicationEventPublisher`, `verifyNoInteractions`); `walletUntouched` (wallet balance equal before/after); `refusesWhenOriginUnmapped`; `refusesWhenNoPaidMode` (nothing written); `zeroTotalAndZeroWeightImport` (declaredValue 1.00, weight 0 stored); `oneBadRowDoesNotStopOthers`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit `feat(legacy-import): history-only commit`.

### Task 5: Keep legacy rows out of live work

**Files:** Modify the queries found by `grep -rn "ShipmentStatus\.\|status" --include=*Repository.java --include=*Specifications.java` feeding: dashboard counts (`DashboardServiceImpl`), daily WhatsApp summary (`communication`), commission/billing jobs (`customerbilling`, `finance`), POD review queue. Test per touched query.

Rule: operational/aggregate queries that count or act on in-flight or money-bearing shipments add `legacy_import = false`; shipment list, search, tracking and reports stay unfiltered. DELIVERED + `commissionCredited=true` + `podApproved=true` already keeps most jobs away — each touched query needs a test proving a legacy row is excluded; each deliberately untouched one is listed in the commit message.

- [ ] **Step 1:** Enumerate (write the list into the commit message). **Step 2:** For each, test with one legacy + one normal shipment — legacy excluded — expect FAIL. **Step 3:** Add filter. **Step 4:** Run `mvn -q test` PASS. **Step 5:** Commit `feat(legacy-import): exclude legacy rows from operational counts and jobs`.

### Task 6: REST API

**Files:** Create `api/LegacyImportController.java`; Test `LegacyImportControllerTest` (MockMvc, follow `DistrictLevelFreightController` tests).

Endpoints under `/api/v1/legacy-import` (all `hasRole(COMPANY_ADMIN)` + authority): `POST /preview` (multipart `file`) -> `PreviewResult`; `PUT /origin-map` body `{origin, branchId}`; `POST /commit` multipart -> `CommitResult`; `GET /batches` -> list; `GET /batches/{id}/report.csv` (columns: rowNo, consignmentNo, outcome, reason, differences) — CSV cells starting `= + - @` prefixed with `'`. Reject non-xls/xlsx/html content or > 10 MB with 400.

- [ ] **Step 1:** Tests: `previewRequiresCompanyAdmin` (BRANCH_MANAGER 403), `previewReturnsCounts`, `commitHappyPath`, `csvEscapesFormulaCells`, `oversizeFileRejected`. **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5:** Commit `feat(legacy-import): REST API`.

### Task 7: Frontend

**Files:** Create `frontend/src/app/features/legacy-import/{legacy-import.service.ts,legacy-import.page.ts,legacy-import.routes.ts}` + spec; register route and COMPANY_ADMIN-only nav item (follow nav-scoping + `menu_items` pattern used for Business Customer).

Page: file picker -> `preview` -> tiles (New/Existing/Differs/Invalid) -> origin table (branch select per unmapped origin, calls `PUT /origin-map`) -> row table with outcome filter -> Commit button disabled until zero unmapped origins and `newCount>0` -> result panel + CSV download. Use signals; id→label maps as signals (OnPush gotcha); use `effect()` not constructor for input-derived state.

- [ ] **Step 1:** vitest spec: `commitDisabledUntilAllOriginsMapped`, `showsServerErrorOnRefusedCommit`, `filtersRowsByOutcome`. **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** `npm test` + `ng build` PASS. **Step 5:** Commit `feat(legacy-import): upload and review UI`.

### Task 8: Live verification and docs

- [ ] **Step 1:** Boot verify backend on :8082 (profile `local`, env vars, `Root@1234`), frontend on :4300; login via DEV QUICK FILL as COMPANY_ADMIN (ashwin@amazinglpl.com / Verify@1234).
- [ ] **Step 2:** Upload `~/Downloads/Consignments_20261118_161117.xls`: expect 2869 rows, NEW 2869 (none exist today), map 15 origins, commit; DB check `select count(*) from shipments where legacy_import=1` = 2869, all DELIVERED, wallet transactions count unchanged, no `whatsapp` message rows added.
- [ ] **Step 3:** Re-upload same file: NEW 0, EXISTING 2869, differs 0; commit creates 0.
- [ ] **Step 4:** Spot-check a legacy shipment in Shipment List/Tracking and confirm dashboards' in-flight counts unchanged. Keep rows (fixtures).
- [ ] **Step 5:** Update `MEMORY/CHANGELOG.md`, `MEMORY/AI_CONTEXT.md`, add `MEMORY/modules/legacy-import.md`; commit `docs: legacy import module`.

---
## Self-review
Spec coverage: upload/formats (T2), lookup+classes (T3), per-origin map (T3, T7), preview/commit (T3, T4), history-only/no side effects (T4), batch/legacy marker (T1), dashboards exclusion (T5), CSV report (T6), idempotency (T4, T8), UI (T7), live verify (T8). Types: `PreviewRow`/`CommitResult`/`LegacyRow` names consistent across tasks.
