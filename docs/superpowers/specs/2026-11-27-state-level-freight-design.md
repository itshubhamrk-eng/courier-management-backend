# State-level fallback for District Level Freight — design

Date: 2026-11-27. Status: awaiting approval. Approach chosen by the user: **A (same table)**.

## Intent and why

Today a booking is priced only if the From Station has a District Level Freight row for the
destination's exact district; otherwise booking is refused ("No District Level Freight
configuration exists for X -> Y"). Setting up every district of a state one by one is
tedious when one rate covers the whole state. The user wants to set a rate for a **state**
and have booking fall back to it when no district-level rate exists.

Booking resolution order (per From Station):

1. Customer zone rate card (unchanged, still first).
2. ACTIVE District Level Freight row for the destination's district.
3. ACTIVE State-level row for the state that district belongs to.
4. Otherwise refuse, as today (message extended to mention the state).

## Goals

- A rate row can be keyed by **State** instead of District, with identical slabs and ODA.
- Booking tries district first, then state; the result says which level priced it.
- Create/edit, list, **Excel import** and **copy-from-branch** all support state rows.

## Non-goals / anti-goals

- No new rate shape (no per-state slabs different from district slabs).
- No change to customer-zone pricing, commission, or any other pricing source.
- A state row must never silently override an existing district row (district always wins).
- No separate state-freight table, service or screens (that was approach B, rejected).

## Constraints from the code

- `district_level_freight` (V54): `district_id` NOT NULL, unique `(company_id, branch_id, district_id)`,
  FK to `master_districts`. Master geography is global (`GlobalMasters.PLATFORM_COMPANY_ID`),
  reached only through this module's ports (`DistrictLookupPort`, `PincodeCoverageLookupPort`).
- `master_districts.state_id` exists and is NOT NULL, so every district has exactly one state.
- Unique key ignores `deleted`; the copy code already works around this with
  `findDistrictIdsIncludingDeleted`. State rows need the same.
- Entities repeat `@Filter`/`@SQLRestriction`; the next free migration number must be
  checked (`flyway_schema_history` / latest file) — do not assume.
- A new permission is not needed; same COMPANY_ADMIN write / authenticated read gates.

## Design (decided)

**Data.** New migration: `district_id` becomes NULL-able; add `state_id BINARY(16) NULL`
with FK to the global states master; CHECK that exactly one of `district_id`/`state_id`
is set; add `UNIQUE (company_id, branch_id, state_id)`. MySQL treats NULLs as distinct, so
the existing district unique key and the new state unique key coexist without collisions.
Existing rows are untouched (district set, state NULL).

**Entity/commands.** `DistrictLevelFreight` gains `stateId`; an invariant enforces exactly
one of district/state. Create/Update commands carry `districtId` or `stateId` (one only).

**Ports.** `PincodeCoverageLookupPort.CoverageRef` gains the district's `stateId`/`stateName`
(and `stateActive`). A state lookup port (or an extension of `DistrictLookupPort`) provides
`findState(id)`, `findStates(ids)` and `findStateByName(name)` for validation, display and import.

**Booking.** `FreightCalculationServiceImpl`: after the customer-zone attempt, look up the
ACTIVE district row; if absent, look up the ACTIVE state row by `coverage.stateId()`. Slab
match, base freight and ODA calculation are the same code for either row
(`freight.isOdaApplicable() && coverage.odaApplicable()` unchanged). The existing
"district inactive" refusal stays. `FreightCalculationResult` gains a `rateLevel`
(`DISTRICT`/`STATE`) and the state name; the booking preview and the shipment-create
sidebar show "Priced at: District / State". The not-found message becomes:
"No District Level Freight configuration exists for <branch> -> <district> or its state <state>."

**Validation.** Setting a state row requires an active state; duplicates rejected
(including soft-deleted, as for districts). Update may not switch a row's level in a way
that breaks the invariant.

**Copy-from-branch.** Copies state rows as well as district rows. "Already has" is judged
per level: a target skips a state row if it has any row (including soft-deleted) for that
state, and a district row if it has one for that district. Result counts unchanged
(copied/skipped). Existing skip-and-never-overwrite behaviour preserved.

**Excel import.** The sheet gets an optional `state` column. Each row fills `district`
or `state`, never both and never neither (row-level ERROR otherwise). Matching is by name,
case-insensitive; a state name resolves via the state lookup. Same dry-run, upsert and
duplicate-in-file detection, keyed on `(branch, district)` or `(branch, state)`.
The downloadable template/headers and its help text are updated.

**Frontend (`features/district-level-freight`).** Create/edit form: a "Level" choice
(District | State) switching the destination field between the district picker and a
state picker; list: a Level column and a destination column showing district or state
name, plus a Level filter; view page shows the level. The copy dialog and import
dialog text mention state rows. Service/types gain `stateId`, `level`.

## Approaches (considered)

- **A. Same table (chosen).** One shape, one set of screens. Cost: everything that assumed
  every row has a district (mapper, list specs/criteria, list filter by district,
  audit snapshots, the coverage port) needs a deliberate pass.
- **B. Separate state-freight table/service/UI (rejected).** Cleaner keys, but duplicates
  the six slabs, ODA, mapper, list, form, import and copy.

## My calls (not yet confirmed with the user — please check)

1. **Inactive district row falls through to the state row.** Booking uses ACTIVE rows only
   (as it does today), so a district row that was deactivated is treated as "not set up"
   and the state rate applies. The alternative is that a deactivated district row blocks
   booking; I chose fall-through because "not found" is the stated trigger and it avoids a
   deactivated row silently refusing lanes the state rate covers.
2. **State-level ODA** follows the same rule as district: charged when the row has ODA on
   and the destination pincode/area is ODA.
3. **Import column** is named `state`, optional, so existing sheets keep working unchanged.
4. **List filter by district** also needs a state filter; I add a Level filter rather than
   overloading the district one.

## Left to the builder

Exact migration number and constraint names; whether state lookup is a new port or an
extension of `DistrictLookupPort`; UI layout of the Level switch; test layout
(unit tests for resolution order, copy, import; a live check booking a shipment to a
district with only a state row, then adding a district row and confirming it wins).
