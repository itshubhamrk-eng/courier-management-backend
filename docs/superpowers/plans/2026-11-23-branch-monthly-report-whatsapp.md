# Branch Monthly Report over WhatsApp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Vendor Audit Report with wallet figures, render a per-branch PDF statement, and send it to each branch manager's WhatsApp automatically on the 1st of each month and on demand from buttons on the report.

**Architecture:** One shared calculation (`BranchStatement`) feeds the report table, the PDF and the CSV. A new `branchreport` module builds the statement and PDF and implements a communication-owned port (`BranchStatementPort`), the same seam `DailyBusinessMetricsPort` uses. Communication owns the log table, job, send, retry and receipts. The PDF is uploaded to Meta's media endpoint and sent by media id. Nothing is stored.

**Tech Stack:** Spring Boot 3 / Java 21, JPA + Flyway (MySQL), OpenPDF, Meta WhatsApp Cloud API via `RestClient`, Angular (signals, OnPush, standalone), JUnit 5 + Mockito, vitest.

**Spec:** `docs/superpowers/specs/2026-11-23-branch-monthly-report-whatsapp-design.md`

## Global Constraints

- Month = previous calendar month in `Asia/Kolkata`; job ticks from 09:00 IST on day 1 (cron `0 0/10 9-20 1 * *`, zone `${app.communication.branch-report.zone:Asia/Kolkata}`).
- Max 3 send attempts per (company, branch, period, recipient); unique key on those four.
- PDF commission and DRS figures come from credited wallet ledger rows (`WalletTransaction`), never recomputed from the current DRS rate. PENDING payments are excluded (existing wallet rule).
- Report endpoints gated like the report: `COMPANY_ADMIN`, `FINANCE_USER`, `ACCOUNTS`. A manager receives only their own branch's PDF.
- Every query company-scoped explicitly (`companyId` predicate, not only the Hibernate filter). Scheduler paths run inside `CompanyContext.runAs(companyId, ...)` and use unguarded `*Unguarded` service methods (the guarded ones are `@PreAuthorize`, and a scheduler thread has no caller).
- Module boundaries: communication must not import shipment/finance. It only sees `communication.domain.BranchStatementPort`.
- New Flyway migration: run `ls backend/src/main/resources/db/migration | sort -V | tail -3` right before writing it (V152 was last on 2026-11-23); use the next free number.
- Manager phones: every active `BRANCH_MANAGER` user with `users.branch_id` = the branch and a non-blank `phone`.
- WhatsApp template event `BRANCH_MONTHLY_REPORT`, WhatsApp only, name `branch_monthly_report`, category UTILITY, language `en_US`, body variables `{{branchName}}`, `{{month}}`; body must not start or end with a variable.
- After each task run `cd backend && mvn -q -o test -Dtest='<task tests>'`; commit per task; update `MEMORY/CHANGELOG.md` and `MEMORY/AI_CONTEXT.md` in the last task.

## Review Focus

1. Branch manager has no phone, or phone is garbage (`abc`, 5 digits): skipped and shown as "no phone", never an exception, never a Meta 400 that burns attempts.
2. A branch with zero activity in the month: still gets a PDF full of zeros (opening = closing balance), no division or empty-list crash.
3. A branch with no wallet row yet: statement shows 0 balances, does not throw.
4. Month boundaries: a transaction at 00:00:00 IST on the 1st belongs to the new month; 1 Jan run reports December of the previous year; February (28/29 days).
5. Two ticks racing, or the app restarting mid-run: the unique key means exactly one send per manager per month; a FAILED row retries, a SENT/DELIVERED row never re-sends unless the user confirms a manual resend.
6. A manager of two branches gets two PDFs, each with only its own branch's data; nothing from another company ever appears.
7. Thousands of ledger rows in the appendix: PDF paginates, memory stays flat (stream rows into the document, do not build a giant string).

---

## File Structure

| File | Responsibility |
|---|---|
| `finance/application/WalletService.java` + `WalletServiceImpl.java` | `periodSummary`, `periodSummaryUnguarded`, `transactionsBetween` |
| `finance/domain/WalletTransactionRepository.java` | aggregate + ordered-list queries for a window |
| `shipment/domain/VendorAuditRow.java`, `ShipmentServiceImpl.java`, DTO, mapper | five new wallet fields, `vendorAuditUnguarded` |
| `frontend/.../vendor-audit-report.ts`, `shipment.model.ts` | new columns, tiles, CSV, buttons |
| `branchreport/domain/BranchStatement.java` | statement record |
| `branchreport/application/BranchStatementService.java` (+Impl) | builds statements from the two sources |
| `branchreport/application/BranchStatementPdf.java` | OpenPDF renderer |
| `branchreport/infrastructure/BranchStatementAdapter.java` | implements `BranchStatementPort` |
| `branchreport/api/BranchReportController.java` | download / send / send-all / status |
| `communication/domain/BranchStatementPort.java` | seam, owned by communication |
| `communication/domain/BranchReportLog(.Repository).java` + migration | send ledger |
| `communication/application/BranchReportServiceImpl.java`, `BranchReportJob.java` | send, retry, schedule |
| provider + template files | document upload/send, `BRANCH_MONTHLY_REPORT` seed |

---

### Task 1: Wallet period figures

**Files:**
- Modify: `finance/application/WalletService.java`, `WalletServiceImpl.java`, `finance/domain/WalletTransactionRepository.java`
- Test: `backend/src/test/java/com/courier/modules/finance/application/WalletPeriodSummaryTest.java`

**Interfaces:**
- Produces:
  - `record PeriodSummary(BigDecimal openingBalance, BigDecimal closingBalance, long rechargeCount, BigDecimal rechargeAmount, BigDecimal commissionCredited, BigDecimal commissionDebited, BigDecimal drsChargeCredited, BigDecimal bookingDebited)` nested in `WalletService`
  - `PeriodSummary periodSummary(UUID branchId, Instant from, Instant to)` (`@PreAuthorize(AUTHENTICATED)`, same branch-access rule as `summarise`)
  - `PeriodSummary periodSummaryUnguarded(UUID companyId, UUID branchId, Instant from, Instant to)` (no auth; scheduler path)
  - `List<WalletTransaction> transactionsBetween(UUID companyId, UUID branchId, Instant from, Instant to)` ascending by `createdAt`, PENDING payments excluded; unguarded, javadoc says system path only

- [ ] **Step 1: Write failing tests** in `WalletPeriodSummaryTest` (Mockito on the repository, or `@DataJpaTest` if the existing finance tests use it). Assertions with a fixed ledger (WRC 1000 CR, SBK 300 DR, COM 50 CR, a DRS credit 20 CR, a PENDING recharge 500, one row 1 ms before `from`, one exactly at `to`): `openingBalance` = balanceAfter of the last row before `from`; `rechargeAmount` = 1000, `rechargeCount` = 1 (PENDING excluded); `commissionCredited` = 50; `drsChargeCredited` = 20; `bookingDebited` = 300; row at `to` excluded, row at `from` included. Separate tests: branch with no wallet returns all zeros; branch with no rows in window has opening = closing = last known balance.
- [ ] **Step 2: Run, expect FAIL** (`mvn -q -o test -Dtest=WalletPeriodSummaryTest`).
- [ ] **Step 3: Implement** the three methods and repository queries. Opening balance = `balanceAfter` of the latest row before `from` (0 when none); closing = `balanceAfter` of the latest row before `to` (equals opening when the window is empty). Sum by `subTransactionType` (`WRC`, `COM` split by `transactionType`, the DRS credit sub type or reference type used by `DrsChargeCreditCommand`, `SBK`).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(finance): wallet period summary for statements`.

---

### Task 2: Vendor Audit Report gains wallet columns

**Files:**
- Modify: `shipment/domain/VendorAuditRow.java`, `shipment/application/ShipmentService.java` + `ShipmentServiceImpl.java:1041`, `shipment/api/dto/VendorAuditRowResponse.java`, `ShipmentMapper.java`, `frontend/src/app/core/models/shipment.model.ts:570`, `frontend/src/app/features/reports/vendor-audit-report.ts`
- Test: `ShipmentServiceImplVendorAuditTest` (extend existing vendor-audit test file if present), `vendor-audit-report.spec.ts`

**Interfaces:**
- Consumes: `WalletService.periodSummary` / `periodSummaryUnguarded` (Task 1)
- Produces: `VendorAuditRow` gains `BigDecimal openingBalance, closingBalance, rechargeAmount, drsChargeCredited` and `long rechargeCount` (appended, nullable `BigDecimal`s when the criteria has no booking date range); `List<VendorAuditRow> vendorAuditUnguarded(ShipmentCriteria criteria)` on `ShipmentService` (javadoc: scheduler/system path, caller must bind a company); `vendorAudit` delegates to it.

- [ ] **Step 1: Failing backend test**: with `bookingDateFrom`/`bookingDateTo` set and a mocked `WalletService` returning a known `PeriodSummary`, each row carries those five fields; without a date range they are null; existing fields unchanged (regression assertion on one old field).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Convert dates to `Instant` using the IST zone. Add the five fields to record, DTO and mapper. Extract the body of `vendorAudit` into `vendorAuditUnguarded`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Frontend.** Add the fields to the `VendorAuditRow` interface; add table columns (Opening Balance, Recharges, DRS Charge Credited, Closing Balance), two stat tiles (Total Recharged, Total DRS Credited), CSV columns. Spec: with rows carrying the new fields the tiles sum them and the CSV header contains the four names. Run `cd frontend && npx ng test --include='**/vendor-audit-report.spec.ts' --watch=false`.
- [ ] **Step 6: Commit** `feat(reports): wallet figures on the Vendor Audit Report`.

---

### Task 3: Statement model and PDF

**Files:**
- Create: `branchreport/domain/BranchStatement.java`, `branchreport/application/BranchStatementService.java`, `BranchStatementServiceImpl.java`, `BranchStatementPdf.java`
- Modify: `backend/pom.xml` (add `com.github.librepdf:openpdf`, pin a version at least 2 weeks old)
- Test: `branchreport/application/BranchStatementServiceImplTest.java`, `BranchStatementPdfTest.java`

**Interfaces:**
- Consumes: `ShipmentService.vendorAuditUnguarded` (Task 2), `WalletService.periodSummaryUnguarded`, `transactionsBetween` (Task 1), `BranchDirectoryPort`-style lookup of branch name/code
- Produces:
  - `record BranchStatement(UUID branchId, String branchCode, String branchName, LocalDate from, LocalDate to, VendorAuditRow audit, WalletService.PeriodSummary wallet, List<WalletTransaction> transactions)`
  - `List<BranchStatement> BranchStatementService.buildAll(UUID companyId, LocalDate from, LocalDate to)` (one `vendorAuditUnguarded` call for the whole company)
  - `Optional<BranchStatement> BranchStatementService.build(UUID companyId, UUID branchId, LocalDate from, LocalDate to)`
  - `byte[] BranchStatementPdf.render(BranchStatement s, String companyName)`

- [ ] **Step 1: Failing tests.** Service: a branch with no shipments and no ledger rows still returns a statement with zero audit row and zero wallet (use a zero `VendorAuditRow`); `buildAll` calls `vendorAuditUnguarded` exactly once; a branch of another company never appears. PDF: output starts with `%PDF`; extracted text (OpenPDF `PdfTextExtractor`) contains the branch name, the month label, the closing balance formatted `1,234.50`, and every transaction number; with 5,000 transactions the page count is greater than 1 and rendering finishes in under 5 s.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Page 1: header (company, branch, period, generated on), summary table per category (recharges, booking debits, booking commission, delivery commission, DRS charge, ODA, other charges, COD/to-pay, orders booked/delivered/cancelled, opening and closing balance). Appendix: transactions table (date IST, transaction no, type label from `SubTransactionType`, reference, debit, credit, balance), header row repeated per page. Write rows straight into the `Document`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(branchreport): branch statement and PDF`.

---

### Task 4: WhatsApp document upload and send

**Files:**
- Modify: `communication/application/provider/WhatsAppProvider.java`, `infrastructure/MetaWhatsAppProvider.java`, `LogOnlyWhatsAppProvider.java`, `communication/domain/CommunicationEventType.java`, `DefaultCommunicationTemplates.java`, `StarterTemplates.java`, `TemplateSampleValues.java`, `CommunicationTemplateServiceImpl.java` (preview branch)
- Test: `MetaWhatsAppProviderDocumentTest.java`, `DefaultCommunicationTemplatesTest.java`

**Interfaces:**
- Produces:
  - `String WhatsAppProvider.uploadDocument(byte[] pdf, String fileName, WhatsAppCredentials c)` returns Meta media id (`POST /{phoneNumberId}/media`, multipart: `messaging_product=whatsapp`, `type=application/pdf`, `file`)
  - `WhatsAppMessage` gains `String documentMediaId, String documentFileName` (existing constructors keep working, both null) and the Meta payload adds `header` component `{type:"document", document:{id, filename}}` when set
  - `CommunicationEventType.BRANCH_MONTHLY_REPORT`; seed `branch_monthly_report` per Global Constraints: body `Monthly statement for {{branchName}} - {{month}}. Your branch report PDF is attached. - SKRA`
  - `StarterTemplates.isAllowed` treats `BRANCH_MONTHLY_REPORT` as WhatsApp-only, like the daily summary
  - Log-only provider returns a `log-whatsapp-<uuid>` id for both methods

- [ ] **Step 1: Failing tests** with a `MockRestServiceServer`: upload sends multipart with the three fields and returns the id from `{"id":"123"}`; a send with `documentMediaId` produces a `header` component with `type:document`; a Meta error body maps to `ProviderSendException` with Meta's own message (reuse `metaError`). Seed test: `BRANCH_MONTHLY_REPORT` + SMS/EMAIL rejected, WhatsApp allowed; seed body neither starts nor ends with a variable.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Seeding is lazy (`getOrSeedDefaults`), so existing companies pick the new template up on next read.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Meta template creation, decision point.** A template with a DOCUMENT header needs a `header_handle` sample from Meta's resumable upload API, which needs the Meta app id (new `appId` in the WhatsApp channel config). Implement that only if it is under about 60 lines. Otherwise record in `MEMORY/` that the template is created once in WhatsApp Manager (name `branch_monthly_report`, header Document, the seeded body) and approval is picked up by the existing Refresh status button. Tell the user which was chosen.
- [ ] **Step 6: Commit** `feat(communication): WhatsApp document send and branch report template`.

---

### Task 5: Send ledger, receipts, dashboard

**Files:**
- Create: migration `V<next>__branch_report_log.sql`, `communication/domain/BranchReportLog.java`, `BranchReportLogRepository.java`
- Modify: `WhatsAppReceiptServiceImpl.java`, `CommunicationDashboardServiceImpl.java`
- Test: `BranchReportLogReceiptTest.java` (extend `WhatsAppReceiptServiceImplTest`), `CommunicationDashboardServiceImplTest.java`

**Interfaces:**
- Produces: table `branch_report_log(company_id, branch_id, period_from DATE, period_to DATE, recipient, status, provider_message_id, error_message, attempt_count, last_attempt_at, sent_at, delivered_at, read_at, + base audit columns)`, unique `(company_id, branch_id, period_from, period_to, recipient)`; entity `BranchReportLog` (same shape and company filter as `DailyReportLog`); repository methods `findByCompanyIdAndBranchIdAndPeriodFromAndPeriodToAndRecipient(...)`, `findFirstByProviderMessageId(String)`, `findAllByCompanyIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID, Instant, Instant)`, `findAllByCompanyIdAndPeriodFromAndPeriodTo(UUID, LocalDate, LocalDate)` (status column on the report page).

- [ ] **Step 1: Failing tests.** Receipt: a `delivered` then `read` receipt for a `BranchReportLog` message id moves SENT to DELIVERED and sets `readAt`; a `failed` receipt after delivery is ignored (same rules as the other three tables). Dashboard: one DELIVERED and one FAILED branch-report row in the window raise `totalSent` by 2, `totalDelivered` by 1, `totalFailed` by 1 (extend the existing `whatsAppExtras` helper).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Add a fourth lookup in `WhatsAppReceiptServiceImpl.apply` and a fourth source in `whatsAppExtras`. Migration also needs no company seed (template is lazy).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(communication): branch report send ledger on the dashboard`.

---

### Task 6: Send service, port, adapter, job

**Files:**
- Create: `communication/domain/BranchStatementPort.java`, `communication/application/BranchReportService.java`, `BranchReportServiceImpl.java`, `BranchReportJob.java`, `branchreport/infrastructure/BranchStatementAdapter.java`
- Test: `BranchReportServiceImplTest.java`, `BranchReportJobTest.java`, `BranchStatementAdapterTest.java`

**Interfaces:**
- Produces:
  - `BranchStatementPort`: `record BranchTarget(UUID branchId, String branchName, List<String> managerPhones)`; `List<BranchTarget> targets(UUID companyId, LocalDate from, LocalDate to)`; `Optional<GeneratedStatement> generate(UUID companyId, UUID branchId, LocalDate from, LocalDate to)` with `record GeneratedStatement(byte[] pdf, String fileName, String branchName, String monthLabel)`
  - `BranchReportService`: `int sendReportsFor(LocalDate from, LocalDate to)` (all companies, scheduler); `SendOutcome sendForBranch(UUID branchId, LocalDate from, LocalDate to, boolean force)` and `List<SendOutcome> sendForAllBranches(LocalDate from, LocalDate to, boolean force)` (manual, current company); `record SendOutcome(UUID branchId, String branchName, String result)` with result one of `SENT`, `NO_PHONE`, `ALREADY_SENT`, `TEMPLATE_NOT_APPROVED`, `FAILED`
  - `BranchReportJob.run()` computes the previous calendar month in the configured zone and calls `sendReportsFor`

- [ ] **Step 1: Failing tests** (Mockito, mirror `DailyReportServiceImplTest`): two managers with phones get two sends and two log rows; a manager with blank or non-numeric phone gives `NO_PHONE` and no provider call; a row already SENT is skipped unless `force`; `force` resend reuses the same row and increments `attemptCount`; row with `attemptCount == 3` and FAILED is skipped by the scheduler; template `PENDING` at Meta gives `TEMPLATE_NOT_APPROVED` with no attempt counted; provider throwing `ProviderSendException` marks FAILED with the message and increments attempts; one branch failing does not stop the others; job run on 2027-01-01 passes `2026-12-01` to `2026-12-31`; on 2026-03-01 passes `2026-02-01` to `2026-02-28`; adapter returns only `BRANCH_MANAGER` users of that branch with a phone.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**, following `DailyReportServiceImpl` (`claim` on the unique key, `CompanyContext.runAs`, `isApprovedOrUnknownAtMeta`, `WhatsAppPayloadBuilder` for the body, then `uploadDocument` + send with `documentMediaId`). Body variables `branchName`, `month` (`MMMM yyyy`). The adapter calls `BranchStatementService` (Task 3) and the user repository.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(communication): monthly branch report job and sender`.

---

### Task 7: Endpoints

**Files:**
- Create: `branchreport/api/BranchReportController.java`, DTOs `BranchReportStatusResponse`, `SendOutcomeResponse`
- Test: `BranchReportControllerTest.java` (MockMvc, security on)

**Interfaces:**
- Consumes: Tasks 3, 5, 6.
- Produces (all under `/api/v1/shipments/vendor-audit`, roles `COMPANY_ADMIN`, `FINANCE_USER`, `ACCOUNTS`):
  - `GET /{branchId}/pdf?from&to` returns `application/pdf`, `Content-Disposition: attachment; filename="<branch>-<yyyy-MM>.pdf"`
  - `POST /{branchId}/send?from&to&force` returns `SendOutcomeResponse`
  - `POST /send-all?from&to&force` returns `List<SendOutcomeResponse>`
  - `GET /send-status?from&to` returns per-branch `{branchId, lastStatus, lastSentAt, managerPhoneCount}` from `BranchReportLog`

- [ ] **Step 1: Failing tests.** An `OPERATOR` gets 403 on all four; `from` after `to` gets 400; a branch id from another company gets 404 and never a PDF; `send` without `force` for an already-sent month returns outcome `ALREADY_SENT` (HTTP 200) and sends nothing; PDF response begins with `%PDF`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Reuse the report's role constant; do not widen it.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** `feat(branchreport): download and send endpoints`.

---

### Task 8: Report page buttons

**Files:**
- Modify: `frontend/.../vendor-audit-report.ts`, `shipment.service.ts` (four calls), `shipment.model.ts` (`SendOutcome`, `BranchSendStatus`)
- Test: `vendor-audit-report.spec.ts`

**Interfaces:**
- Consumes: Task 7 endpoints.

- [ ] **Step 1: Failing spec.** Per row: Download PDF and Send on WhatsApp buttons; Send is disabled with "no phone" when `managerPhoneCount` is 0; a row whose `lastStatus` is SENT/DELIVERED asks to confirm before sending again (then calls with `force=true`); Send to all calls `send-all` once and shows a summary toast with counts per result; every `error:` handler calls `notify.error(apiMessage(e, ...))` (the interceptor shows nothing for 400/422); a `TEMPLATE_NOT_APPROVED` outcome shows "Template not approved by Meta yet".
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** with signals (status map must be a signal, not a plain `Map`, per the OnPush gotcha). Status column shows `lastStatus` and time.
- [ ] **Step 4: Run, expect PASS**, then `npx ng build` clean.
- [ ] **Step 5: Commit** `feat(reports): send branch report on WhatsApp from the vendor audit report`.

---

### Task 9: Live verification and docs

- [ ] **Step 1: Boot** the :8082 verify backend (profile `local`, `DB_PORT=3306`, see memory `verify-stack-dev-profile-cors-gotcha`) and :4300 frontend. Never touch :4200 or the real backend port.
- [ ] **Step 2: Numbers.** For one branch and one real month, compare PDF summary totals to raw SQL on `wallet_transactions` and `shipments` (recharges, commission, DRS credited, closing balance). All must match.
- [ ] **Step 3: Click-through.** On `/reports/vendor-audit`: Download PDF opens and reads correctly; Send shows the right outcome for a branch with a phone, one without, and a template that is not approved; Send to all; resend confirmation; dashboard counts move.
- [ ] **Step 4: Docs.** Add entries to `MEMORY/CHANGELOG.md`, `MEMORY/AI_CONTEXT.md`, `MEMORY/modules/communication.md`.
- [ ] **Step 5: Commit** `docs: branch monthly report`. Hand the user the prod deploy commands (migration runs on boot; template must be created and approved at Meta before the 1st).
