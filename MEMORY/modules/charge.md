# Charge & Charge Settings

New, independent package `com.courier.modules.charge`, migration `V63`. Configuration
only — not wired into Shipment Booking, freight, commission or wallet calculation. A
future calculation engine will consume these rows; none exists yet.

## Shape

`charges` (company-owned): `charge_name`, `service_type_id` (plain UUID, no physical FK —
a different module's table, validated through `ServiceTypeService`, the same cross-module
treatment `Rate.serviceTypeId` gets), `status`. Unique on `(company_id, service_type_id,
charge_name)`, not mentioning `deleted` — a deleted charge's name stays reserved, the
project-wide identity convention.

`charge_settings` (company-owned, `company_id` denormalised from the parent like
`RolePermission.companyId`): `charge_id` (**real, same-module FK, RESTRICT** — unlike the
cross-module ids this project leaves as plain columns), `charge_type` (`FACTOR`/`SLAB`),
`charge_slab_type` (`BOTH`/`KG`/`KM`, null for FACTOR), `from_km`/`to_km`/`from_kg`/
`to_kg` (nullable, required subset depends on slab type), `charge_value` +
`charge_value_type` (`AMOUNT`/`PERCENTAGE`), `commission_type` + `commission_value`
(same two-value enum, configuration only), `status`.

**Slab bands are half-open `[from, to)`** — `master.domain.WeightSlab`'s convention, not
`Rate`'s closed-both-ends one, chosen because a KM slab boundary is often a round number
and needs one unambiguous side. `ChargeSetting.overlaps` checks KM and/or KG depending on
`chargeSlabType`; `BOTH` overlaps only when *both* dimensions overlap (a rectangle test).
Only ACTIVE settings under the same charge, of the same slab type, are compared — the
same "deactivate, add an overlapping slab, reactivate" loophole `RateServiceImpl`/
`WeightSlabServiceImpl` already close, closed here too (checked on create, update while
active, and activate).

## Business rules

1. **FACTOR needs no slab fields.** `applySpecificInvariants`-equivalent
   (`ChargeSetting.applyInvariants`) nulls out `chargeSlabType`/all four range fields
   rather than rejecting a caller who left old SLAB data in the request.
2. **SLAB + KG/KM require only their own pair**; the other pair is forced null.
   **SLAB + BOTH requires all four.** `to` must exceed `from` in every required pair.
3. **PERCENTAGE values are capped at 100** for both `chargeValue` and `commissionValue`;
   `AMOUNT` values are only checked non-negative.
4. **A charge cannot be soft-deleted while it still has any live setting** — 422 naming
   the count, the same "a parent with live children cannot be deleted" rule Master Data's
   geography hierarchy enforces. A setting has no children of its own.
5. **`COMPANY_ADMIN` only, both reads and writes.** Unlike Rate Master, no branch role
   reads this today — it is a company's own back-office pricing configuration, the same
   tier Masters/Branches/Settings/Pricing already use (see nav-scoping precedent).

## Permissions

New `PermissionModule.CHARGE` (112, between `RATE_MASTER` 110 and `ROUTE_MASTER` 120),
the `MASTER` shape (`CREATE`/`READ`/`UPDATE`/`DELETE`/`SEARCH`/`IMPORT`/`EXPORT`/
`ACTIVATE`/`DEACTIVATE`, 9 rights). Catalogue moves 231 → 240. `COMPANY_ADMIN` needs no
explicit `role_permissions` row — its set derives from the whole catalogue, same as every
other module. No other role granted anything; nothing reads Charges except a company
admin.

## API

`/api/v1/charges` (`ChargeController`) — full CRUD + activate/deactivate + soft delete,
`GET /{id}` embeds every associated `ChargeSettingResponse` (no separate round-trip for
"show associated charge settings"). `/api/v1/charges/{chargeId}/settings`
(`ChargeSettingController`) — CRUD + activate/deactivate under one charge, unpaged list
(a charge carries at most a handful of bands).

## Frontend (`features/charges`)

Full module, API-only, no mock. Routes `charges`, `charges/new`, `charges/:id`,
`charges/:id/edit` — `new` before `:id`. Nav: a new top-level "Charges" leaf (icon `toll`,
order 2.9, between Address Distance and Masters), `COMPANY_ONLY` throughout.

- **`ChargeForm`**: just name + service type — a setting needs a real `chargeId`, so it is
  managed separately, after the charge exists.
- **`ChargeView`**: detail page, gated action bar (edit/activate/deactivate/delete), and
  the Charge Settings table — add via `ChargeSettingFormDialog`, edit via the same dialog,
  delete/activate/deactivate inline. Same shape `FreightFactorPage`'s grid already uses
  (add-inline vs. edit-in-dialog), except add is also a dialog here since a setting has
  far more conditional fields than a freight-factor cell.
- **`ChargeSettingFormDialog`**: chargeType/chargeSlabType drive which of the four range
  fields render, mirrored from `ChargeSetting.applyInvariants` client-side so a mismatch
  is caught before the round-trip.

**9 new frontend tests** (`charge.service.spec.ts`). `ng build` clean, `tsc --noEmit`
clean.

## Verified

**Backend**: full suite (997 tests) green after the change, including every existing
shipment/freight/pricing/wallet test — nothing outside `com.courier.modules.charge` plus
the permission/audit wiring (`PermissionModule`, `DefaultPermissionCatalog`,
`AuditAction`) and routes/nav was touched. `ChargeSettingTest` covers all four
chargeType/chargeSlabType combinations, both value types' percentage cap, and the overlap
rule (KG, KM, cross-type independence, BOTH's two-dimension requirement).
`ChargeServiceImplTest`/`ChargeSettingServiceImplTest` cover duplicate name, unknown
service type/charge, stale version, delete-guard, idempotent lifecycle, and
overlap-on-create/activate.

**Frontend**: `ng build --configuration production` clean; `tsc --noEmit` clean; full
`ng test` suite green except one pre-existing, unrelated failure (`reports-dashboard` nav
node) confirmed present on `main` before this change too (via `git stash`).

**Not run**: a live HTTP/DB pass (`mvn spring-boot:run` + real MySQL + browser
click-through) — the comprehensive unit/integration coverage above stood in for it this
session. Cross-company isolation is the same two-layer mechanism (`@Filter` +
`findByIdWithinCompany`) every other module in this codebase already proves correct; not
re-verified live here specifically for Charges.

## Still open

- **No calculation engine.** By design — this brief was configuration only. Shipment
  Booking integration is separate, later work.
- **No live HTTP verification pass** (see above) — the next session touching this module
  should do one against the dev DB, the same way Rate Master/Master Data's own memory
  entries record.
