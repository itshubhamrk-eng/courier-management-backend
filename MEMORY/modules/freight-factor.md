# Freight Factor

**Status:** DONE, backend + frontend, v0.20.1, 2026-08-13 (backend v0.20.0 same day).
New package `com.courier.modules.freight`, migration `V30`, frontend
`features/freight-factor/`. **Verified live end to end** through the actual browser as
both `COMPANY_ADMIN` and `BRANCH_MANAGER` — see `CHANGELOG.md` 0.20.1.

## What it is

A company-level freight pricing grid, keyed on **distance range x weight range**, each
cell carrying a multiplier ("factor"). `freight = matched factor * weight`. Direct user
request: "company level freight calculation by distance range, weight range and freight
factor" — this is the exact spec the user gave when asked to clarify factor semantics.

**Deliberately independent of Rate Master (`com.courier.modules.rate`) and Pricing Engine
(`com.courier.modules.pricing`)** — the user's own explicit instruction: "this module
should be separate, don't depend on route, don't change existing." Nothing here
references a Route, Service Type, Package Type or Payment Mode. Two pricing mechanisms
now coexist in the codebase on purpose, not by oversight:
- **Rate Master** — Route + Service Type + Package Type + Payment Mode + weight slab ->
  base rate, with overage/fuel/handling/ODA/insurance/GST breakdown.
- **Freight Factor** — distance range x weight range -> a single multiplier, no route
  concept, no charge breakdown beyond the one number.

Neither calls the other directly, except one seam. **(0.20.7)** `PricingEngineImpl` does
now call Freight Factor, but only as a *fallback* — when there's no route/rate for the
lane at all, not as a routine second opinion. See "Wired into Pricing Engine" below.

## Shape

`FreightFactor` (company-owned): `fromKm`/`toKm` and `fromWeight`/`toWeight`, both
half-open `[from, to)` — same convention `rate.domain.Rate`'s weight slab uses — plus
`factor` (`DECIMAL(19,4)`, > 0) and an ACTIVE/INACTIVE lifecycle (`activate`/`deactivate`,
no delete — same "withdraw, never remove" convention as Rate). No code/name column;
nothing external quotes a cell by code.

**Weight is plain kg, no unit column** — deliberately different from `Rate.weightUnit`.
The codebase already treats weight as unitless-kg everywhere in `pricing`/`distance`
(`PricingContext.chargeableWeight`, `AddressDistance` itself), so this module matches
that convention rather than Rate's explicit enum.

**2D overlap rule** — `FreightFactor.overlaps(other)`: true only when the distance ranges
**and** the weight ranges both overlap. Two cells sharing a distance band are fine as long
as their weight bands don't collide, and vice versa — a real difference from Rate's
single-dimension `overlapsWeightRange`, since this is a 2D grid not a 1D slab list.
Enforced in `FreightFactorServiceImpl.requireNoOverlap` on create/update/activate, same
"MySQL has no exclusion constraint, deactivate-then-reactivate is a loophole otherwise"
reasoning `RateServiceImpl`'s own version documents.

## Calculate

`FreightFactorService.calculate(fromBranchId, toBranchId, weight)`:
1. `AddressDistanceService.resolveBranchDistance` (existing 0.19.0 module) — cache-or-
   resolve via OSRM, with the on-demand branch geocode fallback 0.19.3 added. This is the
   **one** forward dependency this module has, on `modules.distance`, through an
   application service interface — not a port, same pattern `RateServiceImpl` uses on
   `modules.master`.
2. Finds the one ACTIVE cell whose distance range covers `distanceKm` and whose weight
   range covers `weight`.
3. No match -> `BusinessRuleException` ("gap in the configured grid"). **No floor/ceiling
   extrapolation** the way Rate's `calculate` handles below-lowest/above-highest — a
   direct grid lookup only, since that's what the user's spec asked for, not Rate's
   overage formula.
4. `freight = factor * weight`, 2dp HALF_UP.

`calculate` is `@Transactional` (not read-only) — `resolveBranchDistance` itself writes
on a cache miss (computes and persists a new `AddressDistance` row), so the calculate
call can be a write from that path even though it never touches `freight_factor` itself.

## API

`/api/v1/freight-factors` — `POST`/`PUT {id}`/`GET {id}`/`GET` (paged, status filter,
sortable on the 5 core fields + status/dates)/`PATCH {id}/activate`/
`PATCH {id}/deactivate`/`POST /calculate`. `COMPANY_ADMIN` for every write, any
authenticated company user for reads and calculate — identical audience split to Rate
Master (`RateController`). No new permission codes.

## Frontend

`features/freight-factor/freight-factor.ts` — one page, not a Rate-Master-style 4-route
wizard (the entity is 5 numeric fields, proportionately smaller). A Calculate card
(mirrors `address-distance/address-distance.ts`'s Resolve card) above a grid table with
inline Add/Edit (`components/freight-factor-form-dialog.ts`, mirrors
`customer/components/address-form-dialog.ts`'s dialog-with-form shape) and
Activate/Deactivate. Write actions are hidden via
`AuthService.roles().includes('COMPANY_ADMIN')` on the same page a `BRANCH_MANAGER`
reads/calculates from — no separate route-level split, since the backend itself puts
reads and writes on the same audience boundary within one resource.

## Wired into Pricing Engine (0.20.6/0.20.7)

Direct request: "while shipment booking check if route rate is available or not if not
then calculate charges based on company level weight and distance and book shipment
order." The fallback lives in `PricingEngineImpl.calculate` itself (moved there in
0.20.7 — see `pricing-engine.md`'s "Freight Factor fallback" section for the full design
and the two real bugs a live user report and live browser testing found and fixed:
an `UnexpectedRollbackException` from a nested `@Transactional` method marking the whole
booking transaction rollback-only even though the exception was caught, and a
`NullPointerException` in `PricingMapper.toResponse` that only the frontend's own live
pricing preview endpoint hit). Every caller of `PricingEngine.calculate` — Shipment
Booking, the frontend's live pricing preview, any future consumer — gets the fallback for
free, not just whichever one remembers to catch the exception itself.

**Verified live end to end**, twice — first over raw HTTP, then through the actual
browser UI as `pune@gmail.com` (`BRANCH_MANAGER`) — against real MySQL (`SERVER_PORT=8081`,
schema `V30`): a cell (100–200 km/0–10 kg/factor 7.50) covering the already-cached
148.728 km distance between `PUNE`/`MUMBAI_GEOTEST` (no route runs between them) priced a
3 kg booking at exactly **22.50** (`7.50 × 3`) in both the live pricing preview and the
persisted charge, booked clean end to end from the real **New Shipment** page
(`PUNE-000012`/`26080000018`) — confirmed via the API that the charge row's
`matched_route_id`/`matched_rate_id` are both `NULL`. Full detail in `CHANGELOG.md`
0.20.6/0.20.7.

## Explicitly not built (out of scope)

- **No frontend unit tests.** Same precedent Address Distance itself set (0.19.1) — an
  accepted gap for a module this size, not an oversight.
