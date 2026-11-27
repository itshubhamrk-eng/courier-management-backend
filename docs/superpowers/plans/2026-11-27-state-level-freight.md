# State-level fallback for District Level Freight — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a From Station set a rate for a whole State, and have booking use it when no district-level rate exists.

**Architecture:** Same table (`district_level_freight`) gets a nullable `state_id`; `district_id` becomes nullable; exactly one is set. Booking tries ACTIVE district row, then ACTIVE state row for the district's state. Create/edit/list/copy/import all learn the "level".

**Tech Stack:** Spring Boot 3 / JPA / Flyway (MySQL), JUnit + Mockito; Angular (standalone, signals, vitest).

**Spec:** `docs/superpowers/specs/2026-11-27-state-level-freight-design.md`

## Global Constraints

- Resolution order at booking: customer zone rate → ACTIVE district row → ACTIVE state row → refuse. District always wins.
- Exactly one of `district_id` / `state_id` per row (entity invariant + DB CHECK). Unique `(company_id, branch_id, district_id)` stays; add unique `(company_id, branch_id, state_id)`.
- Duplicate checks, copy and import must count soft-deleted rows (unique keys ignore `deleted`).
- State rows use the same six slabs and ODA rule as district rows: `freight.isOdaApplicable() && coverage.odaApplicable()`.
- An INACTIVE district row is treated as "not set up" (falls through to the state row).
- Excel `state` column is optional; existing sheets must keep working.
- Geography masters are global: reach them only through this module's ports, via `CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, …)`.
- Migration number: next after `V155` — confirm no newer file exists before creating it.
- Write gate stays `COMPANY_ADMIN`; read gate `isAuthenticated()`. No new permission.
- Do not touch the existing "Print Labels"/branch-wallet/nav work already committed; do not commit `backend/tok3`.

## Review Focus

- Pincode whose district has both a district row and a state row → district rate is used (test in Task 3).
- District row exists but INACTIVE, state row ACTIVE → state rate used (Task 3).
- Neither row exists → refusal message names branch, district and state (Task 3).
- Import row with both `district` and `state` filled, or neither → row ERROR, others unaffected (Task 4).
- Copy to a branch that already has (or soft-deleted) a state row → skipped, never overwritten (Task 2).
- Existing district-only rows/sheets/forms behave exactly as before (Tasks 2, 4, 5).

---

### Task 1: Schema, entity, ports

**Files:**
- Create: `backend/src/main/resources/db/migration/V156__district_freight_state_level.sql`
- Modify: `.../districtfreight/domain/DistrictLevelFreight.java`, `DistrictLevelFreightRepository.java`, `DistrictLookupPort.java`, `PincodeCoverageLookupPort.java`
- Modify: `.../master/domain/StateRepository.java`, `.../master/infrastructure/MasterDistrictFreightDistrictDirectory.java`, `MasterDistrictFreightCoverageDirectory.java`
- Test: `backend/src/test/java/com/courier/modules/districtfreight/domain/DistrictLevelFreightTest.java` (create or extend)

**Interfaces:**
- Produces: `DistrictLevelFreight.stateId` (`UUID`, nullable) with `isStateLevel()`; `applyInvariants()` throws `BusinessRuleException` unless exactly one of district/state set.
- Produces: `DistrictLookupPort.StateRef(UUID stateId, String code, String name, boolean active)` and `findState(UUID)`, `findStates(Collection<UUID>)`, `findStateByName(String)`.
- Produces: `CoverageRef` gains trailing `UUID stateId, String stateName, boolean stateActive` (all callers of the record constructor updated).
- Produces: repository `findByCompanyIdAndBranchIdAndStateId(UUID,UUID,UUID)`, `isStateComboTaken(companyId, branchId, stateId, excludeId)` and `findStateIdsIncludingDeleted(companyId, branchId)` (native, include soft-deleted, mirroring the district versions).

- [ ] **Step 1: Failing tests** in `DistrictLevelFreightTest`: `applyInvariants_rejectsBothDistrictAndState`, `applyInvariants_rejectsNeither`, `applyInvariants_acceptsStateOnly`, `applyInvariants_acceptsDistrictOnly` (assert `BusinessRuleException` / no exception).
- [ ] **Step 2: Run** `mvn -q -Dtest=DistrictLevelFreightTest test` → FAIL (`stateId` undefined).
- [ ] **Step 3: Migration V156:** `district_id` NULL-able; add `state_id BINARY(16) NULL` + FK to the global states master table (confirm its name, likely `master_states`) + `idx_district_freight_state (company_id, state_id)` + `uk_district_freight_state_combo (company_id, branch_id, state_id)` + `CHECK ((district_id IS NULL) <> (state_id IS NULL))`. Existing rows untouched.
- [ ] **Step 4: Entity:** add `stateId` column mapping, make `districtId` nullable, extend the `@Table` unique/index list, add the invariant to `applyInvariants()`.
- [ ] **Step 5: Ports/directories:** add state methods to `DistrictLookupPort` and implement in `MasterDistrictFreightDistrictDirectory` (needs `StateRepository` + new `findByCompanyIdAndNameIgnoreCase(UUID, String)` on `StateRepository`; batch lookup mirrors `findDistricts`). Extend `CoverageRef` and populate state fields in both `findByPincode` and `findByPincodeAndArea` from `district.getStateId()`.
- [ ] **Step 6: Repository:** add the three state queries next to their district counterparts.
- [ ] **Step 7: Run** `mvn -q compile` and the Step 2 test → PASS. Fix every `new CoverageRef(...)` / test double broken by the record change.
- [ ] **Step 8: Commit** `feat(district-freight): schema + ports for state-level rows`.

### Task 2: Service, DTOs, mapper, search, copy

**Files:**
- Modify: `application/command/{Create,Update}DistrictLevelFreightCommand.java` (add `UUID stateId`; district OR state), `api/dto/{Create,Update}DistrictLevelFreightRequest.java` (`districtId` no longer `@NotNull`; add `stateId`), `api/dto/DistrictLevelFreightResponse.java` (add `level`, `stateId`, `stateName`), `api/dto/DistrictLevelFreightSearchRequest.java` + `domain/DistrictLevelFreightCriteria.java` + `DistrictLevelFreightSpecifications.java` (add `stateId` set and a `level` filter), `api/DistrictLevelFreightMapper.java`, `application/DistrictLevelFreightServiceImpl.java`, `api/DistrictLevelFreightController.java` (bind new search params)
- Test: `backend/src/test/java/com/courier/modules/districtfreight/application/DistrictLevelFreightServiceImplTest.java` (create or extend)

**Interfaces:**
- Consumes: Task 1 ports/entity/repository.
- Produces: `DistrictFreightLevel { DISTRICT, STATE }` enum (domain package) and `DistrictLevelFreight.level()`; response field `level` (string enum).

- [ ] **Step 1: Failing tests:** `create_stateRow_savesWithStateAndNoDistrict`; `create_rejectsBothOrNeither` (message "Choose either a district or a state."); `create_duplicateStateForBranch_throwsDuplicateResource` (via `isStateComboTaken`); `create_inactiveState_rejected`; `copy_copiesStateRowsAndSkipsExistingIncludingSoftDeleted` (target already has state S soft-deleted → skipped; has no row for state T → copied; counts copied=1 skipped=1); `copy_stillCopiesDistrictRowsAsBefore`.
- [ ] **Step 2: Run** `mvn -q -Dtest=DistrictLevelFreightServiceImplTest test` → FAIL.
- [ ] **Step 3: Service:** `create`/`update` validate exactly one of district/state, resolve via the matching port (`requireDistrict` / new `requireState`, inactive rejected), check the matching combo check, set the right key and null the other. `snapshot`/audit include `stateId`. `copyFromBranch` iterates all ACTIVE source rows: district rows use `findDistrictIdsIncludingDeleted` + active-district check; state rows use `findStateIdsIncludingDeleted` + active-state check (batch `findStates` once, like districts).
- [ ] **Step 4: Mapper/DTOs/search:** responses carry `level` + names for whichever key is set (batch both lookups per page); criteria/specifications filter `stateId` and `level` (`districtId IS NULL`/`stateId IS NULL`).
- [ ] **Step 5: Run** tests → PASS; `mvn -q test -Dtest='DistrictLevel*'` all green.
- [ ] **Step 6: Commit** `feat(district-freight): create/update/search/copy support state rows`.

### Task 3: Booking resolution

**Files:**
- Modify: `application/FreightCalculationServiceImpl.java`, `application/FreightCalculationResult.java` (add `rateLevel` and `stateName`, keep the existing 17- and 19-arg constructors working by defaulting to `DISTRICT`/null), `api/dto/FreightCalculationResponse.java`, `api/DistrictLevelFreightMapper.java` (`toResponse(FreightCalculationResult)`)
- Test: `backend/src/test/java/com/courier/modules/districtfreight/application/FreightCalculationServiceImplTest.java` (create or extend)

**Interfaces:**
- Consumes: Task 1 `CoverageRef.stateId()`, repository `findByCompanyIdAndBranchIdAndStateId`.
- Produces: `FreightCalculationResult.rateLevel()` (`DistrictFreightLevel`), response JSON `rateLevel`, `stateName`.

- [ ] **Step 1: Failing tests** (Mockito on the repository/ports): `districtRowWins_whenBothExist`; `fallsBackToStateRow_whenNoDistrictRow` (rateLevel STATE, slab/ODA computed from the state row); `inactiveDistrictRow_fallsThroughToState`; `neitherRow_refusesNamingBranchDistrictAndState` (message contains "or its state"); `stateRow_odaFollowsPincodeOdaFlag`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** in `calculate`: after the zone attempt, find ACTIVE district row; if absent, find ACTIVE state row by `coverage.stateId()`; if still absent throw the extended `BusinessRuleException` ("No District Level Freight configuration exists for <branch> -> <district> or its state <state>. Set one up under District Level Freight before booking this lane."). Slab match/ODA/total code is shared; `matchedFreightId` = whichever row matched; result carries `rateLevel`.
- [ ] **Step 4: Run** tests → PASS; run `mvn -q -Dtest='ShipmentServiceImplTest,PricingEngineImplTest' test` to confirm the record change broke nothing.
- [ ] **Step 5: Commit** `feat(district-freight): booking falls back to state-level rate`.

### Task 4: Excel import

**Files:**
- Modify: `application/DistrictLevelFreightExcelImportService.java`, `api/dto/ImportRowResult.java` (add `state` label alongside `district`)
- Test: `backend/src/test/java/com/courier/modules/districtfreight/application/DistrictLevelFreightExcelImportServiceTest.java` (extend)

**Interfaces:**
- Consumes: Task 1 `DistrictLookupPort.findStateByName`, repository state queries; Task 2 service `create`/`update` with `stateId`.

- [ ] **Step 1: Failing tests:** `stateRow_createdWhenOnlyStateColumnFilled`; `rowWithBothDistrictAndState_isErrorAndOthersContinue`; `rowWithNeither_isError`; `unknownState_isError` ("No state matches …"); `duplicateStateInFile_isErrorOnSecond`; `legacySheetWithoutStateColumn_stillImportsDistrictRows`; `existingStateRow_isUpdatedNotDuplicated` (dry run and commit).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement:** `state` becomes an optional header (not in `REQUIRED_COLUMNS`; `district` also becomes optional when `state` is present — a row still needs one of the two). Duplicate-in-file key becomes `branch|D:<districtId>` or `branch|S:<stateId>`. Upsert lookup uses the matching repository method. Result rows show whichever label applies.
- [ ] **Step 4: Run** tests → PASS.
- [ ] **Step 5: Commit** `feat(district-freight): Excel import accepts state rows`.

### Task 5: Frontend

**Files:**
- Modify: `frontend/src/app/core/models/district-level-freight.model.ts` (add `level`, `stateId`, `stateName`; requests take `districtId?`/`stateId?`; calc response `rateLevel`, `stateName`), `features/district-level-freight/components/district-freight-form.ts`, `district-freight-table.ts`, `district-freight-list.ts`, `district-freight-view.ts`, `district-freight-edit.ts`, `district-freight-create.ts`, `components/district-freight-import-dialog.ts` (help text/columns), `components/district-freight-copy-dialog.ts` (text), the shipment-create freight sidebar (find via `grep -rn "weightSlabLabel" frontend/src/app/features/shipment`)
- Test: `components/district-freight-form.spec.ts` (extend)

- [ ] **Step 1: Failing form tests:** `level State hides district picker and requires stateId`; `level District keeps existing behaviour`; `edit of a state row hydrates level State with stateId`; payload contains exactly one of `districtId`/`stateId`.
- [ ] **Step 2: Run** `npx ng test --watch=false` (spec file) → FAIL.
- [ ] **Step 3: Implement:** a "Level" select (District | State) in the Route card; State picker from the existing state options (`MasterDataService.options('states')`); table "Destination" column shows name + a Level chip; list gets a Level filter next to the district filter; view page shows the level; import dialog text/columns mention the optional State column and its result table shows the state label; shipment-create sidebar shows "Priced at: District/State" from `rateLevel`.
- [ ] **Step 4: Run** `npx tsc --noEmit -p tsconfig.app.json` and the frontend tests → green (the unrelated `VendorAuditReport` failure is pre-existing).
- [ ] **Step 5: Commit** `feat(district-freight): state level in form, list, import and booking sidebar`.

### Task 6: Live verification and docs

**Files:** `MEMORY/CHANGELOG.md`, `MEMORY/AI_CONTEXT.md` (per the repo's source-of-truth rule).

- [ ] **Step 1:** Boot a throwaway backend on `:8082` with profile `local`, `DB_PORT=3306`, env from the repo `.env` (see the local-dev notes); frontend on `:5173` via the Origin-header proxy. Do not touch `:4200`/`:8100`.
- [ ] **Step 2: Live checks:** (a) create a state row for a branch; book a shipment to a pincode in a district with no district row → priced at State; (b) add a district row for that district → next booking priced at District with a different rate; (c) deactivate the district row → falls back to State again; (d) copy to another branch copies both kinds, re-run skips; (e) import a sheet with one district row, one state row, one row with both (ERROR) → outcomes as expected; (f) legacy sheet still imports.
- [ ] **Step 3:** Update `MEMORY/CHANGELOG.md` and `AI_CONTEXT.md`; stop servers started here and leave test data in the dev DB.
- [ ] **Step 4: Commit** `docs: state-level district freight`.
