# District Freight Copy-From-Branch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** COMPANY_ADMIN copies one branch's active district freight rows to other branches, skipping districts the target already has.

**Architecture:** New `copyFromBranch` use case on `DistrictLevelFreightService` (one transaction), exposed at `POST /api/v1/district-level-freight/copy`; Angular dialog launched from the list page.

**Tech Stack:** Spring Boot / JPA / Mockito (backend), Angular standalone + signals + vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-11-26-district-freight-copy-from-branch-design.md`

## Global Constraints

- COMPANY_ADMIN only (`WRITE` constant in `DistrictLevelFreightServiceImpl`); no BRANCH_MANAGER access.
- Skip = any existing row for (company, target branch, district), **including soft-deleted** (unique key `uk_district_freight_combo` ignores `deleted`; entity has `@SQLRestriction("deleted = false")`, so derived queries cannot see deleted rows).
- Copy only source rows with status ACTIVE and not deleted; new rows start ACTIVE; identical six slab rates, `odaApplicable`, `odaCharge`.
- Source in targets → `BusinessRuleException`; empty targets → `BusinessRuleException`; source and every target must be active company branches (`requireBranch`).
- A source district now inactive in the district master is not copied (counts as skipped).
- Overwrite, per-district selection, rate adjustment are out of scope.

## Review Focus

- Target has a soft-deleted row for a district: skipped, no unique-key crash.
- Duplicate ids in `targetBranchIds`: processed once.
- Source has zero active rows: all counts 0, no error.
- Target branch inactive or foreign-company: whole request rejected before any row is written.
- Re-running the same copy: second run copies 0, skips all.

---

### Task 1: Backend copy use case

**Files:**
- Modify: `backend/src/main/java/com/courier/modules/districtfreight/domain/DistrictLevelFreightRepository.java`
- Modify: `backend/src/main/java/com/courier/shared/audit/domain/AuditAction.java` (add `DISTRICT_FREIGHT_COPIED` after `DISTRICT_FREIGHT_IMPORTED`; no DB enum migration needed — confirm the audit column is a string by checking how `DISTRICT_FREIGHT_IMPORTED` was added in git history)
- Create: `backend/src/main/java/com/courier/modules/districtfreight/application/CopyDistrictFreightResult.java`
- Modify: `.../application/DistrictLevelFreightService.java`, `.../application/DistrictLevelFreightServiceImpl.java`
- Test: `backend/src/test/java/com/courier/modules/districtfreight/application/DistrictLevelFreightServiceImplTest.java`

**Interfaces:**
- Produces: `record CopyDistrictFreightResult(List<TargetResult> targets)` with nested `record TargetResult(UUID branchId, String branchCode, int copied, int skipped)`.
- Produces: `CopyDistrictFreightResult DistrictLevelFreightService.copyFromBranch(UUID sourceBranchId, List<UUID> targetBranchIds)`.
- Produces repository: `List<DistrictLevelFreight> findByCompanyIdAndBranchIdAndStatus(...)` (exists) and new native `Set<UUID>`-returning lookup `findDistrictIdsIncludingDeleted(UUID companyId, UUID branchId)` — native query selecting `district_id` bytes; convert with `TimeOrderedUuid` (mirror `isComboTaken`'s default-method-over-byte[] pattern).

- [ ] **Step 1: Failing tests** in `DistrictLevelFreightServiceImplTest` (Mockito, follow existing setup): 
  - `copyFromBranch_copiesRowsMissingOnTarget`: source has districts A,B; target has A → result copied=1 skipped=1, `repository.save` called once with branchId=target, districtId=B, same six rates + ODA, status ACTIVE.
  - `copyFromBranch_skipsDistrictsWithSoftDeletedTargetRow`: existing-ids lookup returns A (deleted row) → A skipped.
  - `copyFromBranch_rejectsSourceInTargets` and `_rejectsEmptyTargets`: `BusinessRuleException`, `save` never called.
  - `copyFromBranch_rejectsInactiveTarget`: `BusinessRuleException`, `save` never called for any target.
  - `copyFromBranch_dedupesTargetIds`: same target id twice → one `TargetResult`.
  - `copyFromBranch_recordsOneAuditEntryWithCounts`: `auditService.record(eq(AuditAction.DISTRICT_FREIGHT_COPIED), ...)` once per target.
- [ ] **Step 2: Run** `cd backend && mvn -q test -Dtest=DistrictLevelFreightServiceImplTest` — expect compile failure / FAIL.
- [ ] **Step 3: Implement** `copyFromBranch` (`@Transactional @PreAuthorize(WRITE)`): validate all branches first (via `requireBranch`), then per target: load existing district ids (native incl. deleted), for each active source row not in that set and whose district is active via `districtLookup`, build new `DistrictLevelFreight` using `applyRates` + `applyInvariants`, `saveAll`; audit per target with `{sourceBranchCode, targetBranchCode, copied, skipped}`; log info.
- [ ] **Step 4: Run** same test command — expect PASS.
- [ ] **Step 5: Commit** `feat(district-freight): copy rates from another branch (service)`.

### Task 2: REST endpoint

**Files:**
- Create: `.../api/dto/CopyDistrictLevelFreightRequest.java` — `record(@NotNull UUID sourceBranchId, @NotEmpty List<@NotNull UUID> targetBranchIds)`
- Create: `.../api/dto/CopyDistrictLevelFreightResponse.java` — `record(List<TargetResponse> targets)`, `TargetResponse(UUID branchId, String branchCode, int copied, int skipped)`
- Modify: `.../api/DistrictLevelFreightController.java` (add `@PostMapping("/copy")`, `@Operation` doc mirroring `create`, returns `ApiResponse<CopyDistrictLevelFreightResponse>`), `.../api/DistrictLevelFreightMapper.java` (result → response)

**Interfaces:** Consumes `DistrictLevelFreightService.copyFromBranch` (Task 1).

- [ ] **Step 1:** Add DTOs, mapper method, controller method; place `/copy` mapping so it does not collide with `/{id}`.
- [ ] **Step 2:** `cd backend && mvn -q -DskipTests compile` — expect BUILD SUCCESS.
- [ ] **Step 3:** Verify live per project rule (verification ports only, never :4200/real backend; see memory `local-dev-environment`, `verify-stack-dev-profile-cors-gotcha`): boot backend on :8082, log in as COMPANY_ADMIN, `curl POST /api/v1/district-level-freight/copy` copying a branch with rates to a branch without; expect `copied>0`; repeat → `copied=0 skipped=N`; as BRANCH_MANAGER expect 403. Keep fixtures in dev DB.
- [ ] **Step 4: Commit** `feat(district-freight): POST /copy endpoint`.

### Task 3: Frontend dialog

**Files:**
- Modify: `frontend/src/app/core/config/api-endpoints.ts` (add `districtLevelFreightCopy: '/district-level-freight/copy'`), `frontend/src/app/core/models/district-level-freight.model.ts` (add `CopyDistrictFreightRequest`, `CopyDistrictFreightResponse` mirroring Task 2 DTOs), `.../district-level-freight.service.ts` (`copy(body)`)
- Create: `frontend/src/app/features/district-level-freight/components/district-freight-copy-dialog.ts` (pattern: `district-freight-import-dialog.ts` — standalone, OnPush, signals, `app-dialog` panel; source `UiSelect`, target multi-select of branches excluding source, result chips per target after submit)
- Modify: `district-freight-list.ts` (add `Copy from branch` stroked button next to Import, shown only when `can().create`; `openCopy()` opens dialog like `openImport()` and reloads list on close)
- Test: `components/district-freight-copy-dialog.spec.ts`

**Interfaces:** Consumes `POST /district-level-freight/copy` (Task 2). Branch options come from the same source the list page's branch filter uses (`MasterDataService`/existing branch options there).

- [ ] **Step 1: Failing spec** (vitest, follow `district-freight-form.spec.ts`): submit disabled until source and ≥1 target chosen; target options exclude the chosen source; success shows copied/skipped per target.
- [ ] **Step 2:** `cd frontend && npx ng test --watch=false` — expect FAIL.
- [ ] **Step 3:** Implement dialog, service method, endpoint const, model types, list button.
- [ ] **Step 4:** Run tests — expect PASS; `npx ng build` — expect success.
- [ ] **Step 5:** Verify in browser on :4300 (dev quick fill login; `verify-from-main-tree-origin-header-proxy` if :4300 belongs to another worktree): open dialog, copy, see counts, list shows new rows.
- [ ] **Step 6: Commit** `feat(district-freight): copy-from-branch dialog`.

### Task 4: Docs

- [ ] Update `MEMORY/AI_CONTEXT.md` current-version note and `CHANGELOG.md` (2026-11-26 entry) per repo rule.
- [ ] Commit `docs: district freight copy-from-branch`.
