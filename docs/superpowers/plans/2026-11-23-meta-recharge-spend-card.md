# Meta recharge button + WhatsApp spend card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dashboard shows WhatsApp spend this month vs an admin-set budget (amber >=80%, red >=100%) plus a "Recharge on Meta" button.

**Architecture:** New `WhatsAppSpendService` in the communication module reuses `WhatsAppProvider.fetchPricing` (same call as rate sync) and the WHATSAPP row's `configJson` for `businessAccountId` and new `monthlyBudget`. One GET endpoint on the dashboard controller. Frontend adds a card to the dashboard and a budget field to Channel Settings.

**Tech Stack:** Spring Boot (Java, JUnit/Mockito), Angular signals + vitest.

**Spec:** `docs/superpowers/specs/2026-11-23-meta-recharge-spend-card-design.md`

## Global Constraints

- No migration. Budget key is `monthlyBudget` in WHATSAPP `configJson`, saved via `CommunicationSettingService.upsert`.
- Period: 1st of current month 00:00 Asia/Kolkata to now.
- Levels: `NOT_CONFIGURED`, `NO_BUDGET`, `OK` (<80%), `WARN` (>=80% and <100%), `OVER` (>=100%).
- Copy says "spend vs budget"; never "balance".
- Recharge URL constant: `https://business.facebook.com/billing_hub/payment_settings`, opened `_blank` with `noopener`.
- Read guard: `hasRole(COMPANY_ADMIN) or hasAuthority('NOTIFICATION_READ')` (verify the exact read authority used by `CommunicationDashboardServiceImpl` and match it).
- Meta failure must not break the dashboard: returned as `note`, HTTP 200.

## Review Focus

- Budget blank/0/negative/non-numeric -> `NO_BUDGET`, no divide-by-zero.
- Exactly 80% -> `WARN`; exactly 100% -> `OVER`; 79.99% -> `OK`.
- No Business Account ID / token -> `NOT_CONFIGURED`, not an error.
- Meta returns no points / null currency -> spent 0, level per budget.
- `cost` null on a point is treated as 0.

---

### Task 1: Spend service + level rules (backend)

**Files:**
- Create: `backend/src/main/java/com/courier/modules/communication/application/WhatsAppSpendService.java`
- Create: `backend/src/main/java/com/courier/modules/communication/application/WhatsAppSpendServiceImpl.java`
- Test: `backend/src/test/java/com/courier/modules/communication/application/WhatsAppSpendServiceImplTest.java`

**Interfaces:**
- Produces: `WhatsAppSpendService.Spend current()` and
  `record Spend(String level, String currency, BigDecimal spent, BigDecimal budget, BigDecimal percentUsed, Instant periodStart, Instant periodEnd, String note)`.
- Produces (package-visible static, for tests): `static String levelFor(BigDecimal spent, BigDecimal budget)`.
- Consumes: `CommunicationSettingRepository.findByCompanyIdAndChannel`, `CommunicationConfigJson.read`, `WhatsAppProvider.fetchPricing`, `WhatsAppCredentials.hasBusinessAccount()`.

- [ ] **Step 1: Write failing tests** in `WhatsAppSpendServiceImplTest` (copy mock setup style from `WhatsAppRateSyncServiceImplTest`):
  - `levelFor` : (79.99,100)->`OK`, (80,100)->`WARN`, (99.99,100)->`WARN`, (100,100)->`OVER`, (150,100)->`OVER`, (50,null)/(50,0)/(50,-5)->`NO_BUDGET`.
  - `current_sumsAllPointsCost_nullCostAsZero`: points cost 40, null, 25.5 -> `spent == 65.5`, `percentUsed == 65.50` with budget 100, level `OK`.
  - `current_noSetting_returnsNotConfigured`, `current_missingBusinessAccountId_returnsNotConfigured` (note names Channel Settings).
  - `current_nonNumericBudget_isNoBudget` (`monthlyBudget="abc"`).
  - `current_metaFails_returnsNoteNotException`: provider throws `ProviderSendException("(#200) permission")` -> level `NO_BUDGET` or per budget, `spent` null, `note` contains the message.
  - `current_periodStartIsFirstOfMonthIst`: assert `periodStart` is 00:00 IST on day 1 (use a fixed `Clock`).
- [ ] **Step 2:** Run `cd backend && mvn -q -Dtest=WhatsAppSpendServiceImplTest test`; expect compile FAIL.
- [ ] **Step 3: Implement** both classes per Interfaces. Inject a `java.time.Clock` (add a `@Bean` only if none exists; check first). `@PreAuthorize` per Global Constraints. Percent = spent/budget*100, 2 decimals HALF_UP.
- [ ] **Step 4:** Re-run the test; expect PASS.
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/courier/modules/communication/application/WhatsAppSpend* backend/src/test/java/com/courier/modules/communication/application/WhatsAppSpendServiceImplTest.java
git commit -m "feat(communication): WhatsApp monthly spend vs budget service"
```

### Task 2: Endpoint (backend)

**Files:**
- Create: `backend/src/main/java/com/courier/modules/communication/api/dto/WhatsAppSpendResponse.java` (same fields as `Spend`)
- Modify: `backend/src/main/java/com/courier/modules/communication/api/CommunicationDashboardController.java`

**Interfaces:**
- Consumes: `WhatsAppSpendService.current()`.
- Produces: `GET /api/v1/communication/dashboard/whatsapp-spend` -> `ApiResponse<WhatsAppSpendResponse>`.

- [ ] **Step 1:** Add `WhatsAppSpendResponse` record and the `@GetMapping("/whatsapp-spend")` method with an `@Operation` summary saying it is spend vs budget, not balance.
- [ ] **Step 2:** `cd backend && mvn -q -DskipTests compile`; expect success.
- [ ] **Step 3:** Add a controller/web test only if sibling controllers have one; otherwise skip.
- [ ] **Step 4: Commit** `feat(communication): GET dashboard/whatsapp-spend`.

### Task 3: Frontend model, service, budget field

**Files:**
- Modify: `frontend/src/app/core/models/communication.model.ts` (add `WhatsAppSpend` interface mirroring the response; `level` union of the five levels)
- Modify: `frontend/src/app/core/config/api-endpoints.ts` only if a new constant is needed (reuse `API.communicationDashboard`)
- Modify: `frontend/src/app/features/communication/communication.service.ts` (add `whatsAppSpend()` -> `GET ${API.communicationDashboard}/whatsapp-spend`)
- Modify: `frontend/src/app/features/communication/channel-settings.ts` (add field `{ key: 'monthlyBudget', label: 'Monthly WhatsApp budget', placeholder: 'e.g. 5000 — dashboard warns at 80% and 100%' }` after `rateCurrency` in the WHATSAPP list, and `monthlyBudget: ['']` in the form at the line with `rateCurrency: ['']`)
- Test: `frontend/src/app/features/communication/communication.service.spec.ts`

- [ ] **Step 1:** Add spec `whatsAppSpend calls GET /dashboard/whatsapp-spend` following existing spec style.
- [ ] **Step 2:** Run `cd frontend && npx ng test --watch=false`; expect that spec FAIL.
- [ ] **Step 3:** Implement model, service method, settings field/form control.
- [ ] **Step 4:** Re-run; expect PASS. Confirm `monthlyBudget` round-trips (loads and saves) the way `rateCurrency` does.
- [ ] **Step 5: Commit** `feat(communication): spend API client and monthly budget setting`.

### Task 4: Dashboard card + Recharge button

**Files:**
- Modify: `frontend/src/app/features/communication/dashboard-view.ts` (add pure `spendView(spend): { tone: 'success'|'warning'|'danger'|'neutral'; barPercent: number; headline: string; hint: string }`)
- Modify: `frontend/src/app/features/communication/communication-dashboard.ts` (load spend in `load()` independent of the main dashboard call; card + button in template)
- Test: `frontend/src/app/features/communication/dashboard-view.spec.ts`

**Interfaces:**
- Consumes: `CommunicationService.whatsAppSpend()`, `WhatsAppSpend`.
- Produces: `spendView`; `const META_BILLING_URL = 'https://business.facebook.com/billing_hub/payment_settings'` exported from `dashboard-view.ts`.

- [ ] **Step 1: Write failing tests** for `spendView`: `OK`->tone success; `WARN`->warning; `OVER`->danger with `barPercent` capped at 100; `NO_BUDGET`->neutral with hint pointing to Channel Settings; `NOT_CONFIGURED`->neutral with note; headline contains currency and amounts and never the word "balance".
- [ ] **Step 2:** Run `npx ng test --watch=false`; expect FAIL.
- [ ] **Step 3:** Implement `spendView`; add card (title "WhatsApp spend this month", progress bar, amount vs budget, hint, "as reported by Meta") and a `Recharge on Meta` `app-button` that calls `window.open(META_BILLING_URL, '_blank', 'noopener')`. Spend load failure (HTTP error) hides the card quietly; it must not block the rest of the dashboard.
- [ ] **Step 4:** Re-run tests; expect PASS. `npx ng build` (or project build script) succeeds.
- [ ] **Step 5: Commit** `feat(communication): WhatsApp spend card and Recharge on Meta button on dashboard`.

### Task 5: Live verification

- [ ] **Step 1:** Boot verify stack per memory (`:8082` profile `local`, `:4300` with Origin-header proxy; never touch `:4200`/real backend port). Log in via DEV QUICK FILL.
- [ ] **Step 2:** On `/communication/settings` set Monthly budget 1000, save, reload, value persists.
- [ ] **Step 3:** On `/communication/dashboard` card renders; button opens Meta billing in a new tab. With test number expect spend 0 / `OK` (or Meta's note if token lacks permission).
- [ ] **Step 4:** Force amber/red by setting a tiny budget only if spend > 0; otherwise cover via the unit tests and say so.
- [ ] **Step 5:** Update `MEMORY/CHANGELOG` per repo rule; stop verify servers. Do not commit unrelated `backend/tok3`.
