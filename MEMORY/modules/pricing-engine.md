# Pricing Engine

New package `com.courier.modules.pricing`. **No migration, no table, no persistence.**
A reusable, stateless service that prices a shipment — Strategy + Factory over the same
Route/Rate data Rate Master already owns. Built to be called by Shipment Booking,
Quotation, the mobile app and any future integration; deliberately does **not** depend on
`modules.shipment` (which does not exist yet) or on anything Rate Master's own
`POST /rates/calculate` doesn't already depend on.

## Why this exists alongside Rate Master's own `calculate`

Rate Master's `POST /rates/calculate` already prices a shipment against `actualWeight`
with a fixed formula. This module is a *superset*, not a duplicate:

- **Volumetric weight.** Rate Master matches on `actualWeight` only. Pricing Engine takes
  Length/Width/Height, computes volumetric weight (`L x W x H / divisor`), and matches on
  `chargeableWeight = MAX(actual, volumetric)` — a bulky-but-light parcel prices correctly
  here and would under-price on Rate Master's endpoint.
- **Serviceability.** Rate Master never looks at a pincode. Pricing Engine validates that
  both the pickup and delivery pincode are serviceable before pricing anything.
- **Configurable, pluggable charge lines.** Rate Master always charges every surcharge the
  rate carries. Pricing Engine can switch Fuel/ODA/Insurance/Discount on or off per
  deployment, and each charge line is its own `ChargeCalculator` Strategy — a caller that
  needs a different combination (or a future promotional strategy) does not touch
  `RateServiceImpl`.
- **Discount and configurable rounding**, neither of which Rate Master's fixed formula has.

Both endpoints stay live: Rate Master's is used directly by its own screens (the Rate
Calculator page/dialog), this module's is the seam Shipment Booking is meant to call.

## Flow

```
Validate Route (RouteValidation)
  -> Validate Serviceability + Booking fields (BookingValidation)
  -> Validate a Rate exists for the combination (RateValidation)
  -> Validate Weight > 0 (WeightValidation)
  -> Calculate Volumetric Weight (domain.VolumetricCalculator)
  -> Calculate Chargeable Weight = MAX(actual, volumetric) (domain.ChargeableWeightCalculator)
  -> Execute Charge Calculators (Freight, Fuel, Handling, ODA, Insurance, GST, Discount, RoundOff)
  -> Return Charge Breakup
```

`PricingEngineImpl.calculate` runs `WeightValidation` first in code (cheapest check, no
I/O), then `RouteValidation`, `BookingValidation`, `RateValidation` in the document's order,
then the weight calculators, then hands off to a `PricingStrategy` the `PricingFactory`
resolves.

**Two honesty notes on the flow, both because the module's own class lists don't quite
match its own flow diagram:**

1. The flow lists five steps before "Execute Charge Calculators" (Route, Serviceability,
   Rate, Volumetric, Chargeable) but the "Validation Module" section names exactly four
   classes (`RouteValidation`, `RateValidation`, `WeightValidation`, `BookingValidation`) —
   no `ServiceabilityValidation`. Serviceability is folded into `BookingValidation`
   alongside service-type/package-type/payment-mode existence and the booking-date default,
   since all three are "can this booking happen" checks distinct from the lane (Route) and
   the tariff (Rate).
2. "Validate Rate" (flow step 3) runs **before** the weight is known, so it can only
   confirm a rate card exists for the Route + Service Type + Package Type + Payment Mode
   combination (`RateValidation`, reusing `RateService.findActiveCandidates` — see below) —
   not that a slab covers the chargeable weight. That slab match (exact / floored /
   overage / gap) happens inside `calculator.FreightCalculator`, once chargeable weight is
   known, which is also where "Weight Slab Not Found" is thrown. This separates "Rate Not
   Found" (no card at all) from "Weight Slab Not Found" (a card exists, nothing covers this
   weight) — two different entries in the module's own Error Handling list.

## Two small seams added to already-shipped modules

Same "smallest addition, not a duplicate" pattern `RouteService.findByBranches` followed
for Rate Master:

- **`RateService.findActiveCandidates(routeId, serviceTypeId, packageTypeId, paymentModeId,
  bookingDate)`** — every ACTIVE rate for one combination whose effective window covers
  the date. `RateServiceImpl.calculate` itself was refactored to call this rather than
  duplicate the repository query + `coversDate` filter it already had; `mvn test` for
  `RateServiceImplTest` needed no changes because the mocked repository call underneath is
  unchanged.
- **`PincodeService.findByCode(String code)`** — a pincode is a global master (`V12`); the
  application layer had no lookup by the raw postal code a booking screen or an
  integration actually holds, only `getById(UUID)`. Implemented with the same
  `CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, ...)` wrapper `doGetById`
  already uses for every other global-list read.

## Freight Factor fallback (0.20.7)

Direct user request, arriving through Shipment Booking: "while shipment booking check if
route rate is available or not if not then calculate charges based on company level
weight and distance and book shipment order." `PricingEngineImpl.calculate` wraps
`RouteValidation` → `BookingValidation` → `RateValidation` → the charge-calculator chain
in one `try`, catching a new `RouteRateUnavailableException` (a `BusinessRuleException`
subtype thrown from `RouteValidation`/`RateValidation`/`FreightCalculator` — no route, an
inactive route, no active rate for the combination, or no weight slab covering the
chargeable weight) and falling back to one call to `FreightFactorService.calculate`
(`com.courier.modules.freight` — the standalone distance×weight×factor grid, 0.20.0),
resolving the branch pair's road distance the same way that module's own Calculate card
does. The fallback `PricingResult` carries `matchedRoute`/`matchedRate` both null and
every Rate-Master-only line (fuel/handling/ODA/insurance/GST/discount/round-off) at zero
— `netAmount` is the freight figure alone, since Freight Factor deliberately carries none
of those. A gap in the Freight Factor grid itself, or an unresolvable branch-pair
distance, still fails the call with its own exception — there is no third fallback tier.

**Deliberately lives here, not in `ShipmentServiceImpl`** — it shipped there first
(0.20.6), then moved (0.20.7) once live testing showed the frontend's own live pricing
preview (`POST /pricing/calculate`, called directly by `shipment-create.ts`'s debounced
`schedulePricing`) surfaced the raw "No route runs..." refusal instead of a quote, because
it calls this engine directly and never went through Shipment Booking's own catch. Putting
the fallback in the engine itself means every caller — Shipment Booking, the live preview,
any future Quotation/mobile consumer — gets it for free.

**Two real bugs found live, not by the mocked unit tests, while wiring this up:**

1. **`UnexpectedRollbackException`** on the first live booking attempt, even though the
   exception was caught correctly. `RouteServiceImpl.findByBranches` is
   `@Transactional(readOnly = true)` and *joins* the caller's own transaction (default
   `REQUIRED` propagation) — a nested `@Transactional` method throwing marks the **whole
   shared transaction** rollback-only the instant it throws, regardless of whether the
   caller catches the exception afterward; only the method that physically opened the
   transaction can un-mark it, and nothing in Spring lets a caller do that after the fact.
   Fixed with `@Transactional(readOnly = true, noRollbackFor =
   RouteRateUnavailableException.class)` on `findByBranches` alone — `RateValidation`'s
   "no active rate" throw and `FreightCalculator`'s "no weight slab" throw are both plain,
   non-`@Transactional` components, so they don't cross this boundary and needed no fix.
2. **`NullPointerException` in `PricingMapper.toResponse`**, hit only by the standalone
   `POST /pricing/calculate` endpoint (Shipment Booking's own path never called this
   mapper with a fallback result until the fix above even reached it) — it unconditionally
   called `result.matchedRoute().getBookingBranchId()` etc., which NPEs when
   `matchedRoute` is null. Fixed by null-checking `matchedRoute`/`matchedRate` throughout
   and sourcing `bookingBranchId`/`deliveryBranchId` from the original `PricingCommand`
   (now a required second parameter to `toResponse`) instead of the absent matched route;
   `weightUnit` defaults to `WeightUnit.KG` (Freight Factor's own always-kg convention)
   when there's no matched rate to read a unit off of.

**Verified live end to end, twice** — first over raw HTTP, then through the actual browser
UI as `pune@gmail.com` (`BRANCH_MANAGER`) — against real MySQL (`SERVER_PORT=8081`, schema
`V30`): a Freight Factor cell (100–200 km/0–10 kg/factor 7.50) covering the real, cached
148.728 km distance between `PUNE`/`MUMBAI_GEOTEST` (no route runs between them) priced a
3 kg quote at exactly **22.50** (`7.50 × 3`) in both the live pricing preview panel and the
final persisted charge, booked clean from the real **New Shipment** page
(`PUNE-000012`/`26080000018`) — confirmed via the API that the charge row's
`matched_route_id`/`matched_rate_id` are both `NULL`. A same-session regression booking on
the real PUNE→LATUR route/rate confirmed the normal path is undisturbed — real, non-null
matched ids. `mvn test` 709 → 711 (`PricingEngineImplTest` gained the two fallback cases;
`ShipmentServiceImplTest` needed none, since it no longer knows Freight Factor exists).
Full detail in `CHANGELOG.md` 0.20.6/0.20.7 and `MEMORY/modules/freight-factor.md`.

### Editable, increase-only Freight Factor override (0.24.0)

Direct request, arriving through Shipment Booking again: "show applied freight factor and
it should be editable, only should be increse freight factore." `PricingCommand` gained
`freightFactorOverride` (nullable); `priceByDistanceAndWeight` reads the grid's own matched
cell as before, but now accepts a caller override **only when `>=` that matched value** —
a smaller override throws `BusinessRuleException` ("Freight factor can only be increased —
matched X, requested Y.") before anything is repriced. When accepted (or when no override
is supplied, in which case it defaults to the matched cell), `freight = effectiveFactor *
chargeableWeight` and GST/net amount are computed off that — so raising the factor changes
freight, GST and net amount together, never freight alone. `PricingResult` gained
`appliedFreightFactor` (the effective factor actually priced with — null on the normal
Route/Rate path, which never uses a freight factor at all) — surfaced through
`PricingResponse`/`ShipmentChargeResponse` (`shipment_charges.applied_freight_factor`,
`V35`) so a caller can show what was applied without re-deriving it. `PricingRequest`/
`CreateShipmentRequest`/`UpdateShipmentRequest` all gained the same
`freightFactorOverride` field, threaded straight through — the live preview
(`POST /pricing/calculate`) and the actual booking share the identical validation, same
"one seam, every caller gets it" reasoning 0.20.7 already established. See
`MEMORY/modules/shipment-booking.md`'s frontend section for how `shipment-create.ts`
surfaces the input. `mvn test` green — `PricingEngineImplTest` gained two new cases (the
refusal, the successful raise) plus a `getFactor()` stub on the existing fallback test's
mock (needed now that the no-override path also reads it, to populate
`appliedFreightFactor`). Full detail in `CHANGELOG.md` 0.24.0.

## Strategy + Factory

- **`ChargeCalculator`** (Strategy, one per charge line) — `type()`, `order()`,
  `isEnabled(context)`, `calculate(context)`. Eight Spring-bean implementations:
  `FreightCalculator` (10), `FuelCalculator` (20), `HandlingCalculator` (30),
  `ODAChargeCalculator` (40), `InsuranceCalculator` (50), `GSTCalculator` (60),
  `DiscountCalculator` (70), `RoundOffCalculator` (80). A disabled calculator contributes
  **zero**, it is never skipped — `PricingContext.charge(type)` always has an entry once
  the chain has run, so a later calculator reading an earlier one's line never sees `null`.
  `FreightCalculator` is the one calculator with a side effect: it runs the slab match
  against `context.candidates()` and `context.chargeableWeight()` (the exact/floored/
  overage/gap algorithm ported from `RateServiceImpl.calculate`, unchanged) and sets
  `context.matchedRate` — every calculator after it reads that rate's own surcharge field.
- **`PricingStrategy`** — one level above `ChargeCalculator`: decides *how* the lines are
  run and combined into a `PricingResult`. `StandardPricingStrategy` (the only
  implementation, `@Order(LOWEST_PRECEDENCE)`) sorts the injected `List<ChargeCalculator>`
  by `order()` and runs them in sequence. The seam exists for a future strategy
  (promotional, surge) to run a different combination without `PricingEngineImpl` or any
  calculator changing.
- **`PricingFactory`** — resolves the `PricingStrategy` for a context by trying every
  registered strategy (Spring `@Order`) and returning the first whose `supports(context)`
  is true. Today `StandardPricingStrategy.supports` always returns true, so it is always
  the fallback match; a higher-precedence strategy registered later would be tried first.

## Weight

Three single-purpose classes in `domain`, none doing I/O:

- **`WeightCalculator.normalise`** — scales `actualWeight` to 3 decimals (matches
  `rate.domain.Rate`'s weight-column precision). Purely a normaliser; `Actual weight > 0`
  is `WeightValidation`'s job and runs first.
- **`VolumetricCalculator.calculate(length, width, height, divisor)`** — `L x W x H /
  divisor`, zero when any dimension is missing (dimensions are optional input; a shipment
  with none captured prices on actual weight alone, not refused).
- **`ChargeableWeightCalculator.calculate(actual, volumetric)`** — `MAX(actual,
  volumetric)`. This is the courier-industry meaning of "chargeable weight" and is a
  **different quantity** from `rate.application.RateCalculationResult.chargeableWeight`,
  which is the weight billed *after* Rate Master's own slab/overage arithmetic on top of
  this one. Two modules, two things that happen to share a name.

## Configuration

`PricingProperties` (`@ConfigurationProperties(prefix = "pricing")`, bound in
`application.yml`, registered in `CourierApplication`), converted once per request to the
immutable `domain.PricingConfiguration` `PricingEngineImpl` builds a `PricingContext` from:

| Property | Default | Calculator gated |
|---|---|---|
| `pricing.volumetric-divisor` | `5000` | `VolumetricCalculator` |
| `pricing.fuel-enabled` | `true` | `FuelCalculator` |
| `pricing.oda-enabled` | `true` | `ODAChargeCalculator` |
| `pricing.insurance-enabled` | `true` | `InsuranceCalculator` (also needs `declaredValue > 0`) |
| `pricing.discount-enabled` | `true` | `DiscountCalculator` (also needs a discount on the request) |
| `pricing.rounding-rule` | `NEAREST_ONE` | `RoundOffCalculator` |

Handling and GST have no toggle — the module's own Configuration list only names Fuel,
Insurance, ODA and Discounts, and GST is statutory, not a company preference.
`RoundingRule` (`domain`) is `NONE` / `NEAREST_ONE` / `NEAREST_FIVE` / `NEAREST_TEN`, each
`HALF_UP`. Company-level overrides were not asked for and are not built — every request
today uses the one deployment-wide default.

## The charge sequence, end to end

```
subtotalBeforeGst   = Freight + Fuel + Handling + ODA + Insurance
totalBeforeDiscount = subtotalBeforeGst + GST            (GST on the subtotal above)
totalBeforeRoundOff = totalBeforeDiscount - Discount      (discount on freight+surcharges+GST)
netAmount           = totalBeforeRoundOff + RoundOff      (RoundOff can be negative)
```

`PricingContext` accumulates each line in an `EnumMap<ChargeType, BigDecimal>` and exposes
the three running subtotals as methods, so a calculator (or a test) never re-derives a sum
another calculator already owns.

## Honesty note: two input fields the module's own list didn't cover

- **`discountPercentage` / `discountAmount`** — the documented Input list has no discount
  field, but Discount is a required Output line, so a caller needs a way to ask for one.
  Optional on `PricingRequest`; percentage wins when both are supplied; the result is
  clamped to `[0, totalBeforeDiscount]`. Same pattern Rate Master's optional `bookingDate`
  followed for the same reason (a documented rule with no field to check it against).
- **`declaredValue`** *is* in the documented Input list but has no corresponding rule in
  the module's own Business Rules section; used here to gate `InsuranceCalculator` — a
  shipment with nothing declared has nothing to insure.

## REST API

`POST /api/v1/pricing/calculate`, `isAuthenticated()` — the same read tier Rate Master's
own calculator uses; every branch role that books a shipment needs a quote. No new
permission codes: the module's own spec has no Permissions section, and gating it more
tightly than Rate Master's calculate would work against "reusable by Mobile App / API /
Future Integrations." `PricingMapper` is the one place `Route`/`Rate` (other modules'
domain entities, legitimately read inside `PricingContext` the same way
`RateServiceImpl` already reads `Route`) get converted to plain values before crossing the
wire.

## Verified by running it (2026-07-30, MySQL 8.0.46, temporary instance `SERVER_PORT=8083`,
against the shared dev database — the user's own 8081/4200 instances untouched)

The dev database had **no geography seeded at all** (accepted gap, same one every module's
verification note already records) — built the minimal chain (one state, district, city,
area, three pincodes: `411001` and `400008` serviceable, `411099` deliberately not) as
`ravi@legacy.test` (`SUPER_ADMIN`) before anything could be priced. Left in place as
fixtures, per the project's keep-test-data rule.

Over HTTP as `asha@legacy.test` (`COMPANY_ADMIN`, `LEGACY_CO`), against the existing
`PNQ_BOM` route and `RATE-PNQ-BOM-STD` / `RATE-PNQ-BOM-STD-5-10` rates Rate Master's own
verification pass left behind:

- **Exact slab, actual weight wins** (2.5 kg, dimensions giving a smaller 1.2 kg
  volumetric): freight 100.00, fuel 10.00, handling 5.00, GST 20.70 — pre-round total
  **135.70**, matching Rate Master's own curl/UI verification of the identical inputs
  verbatim, then rounds to **136.00** (round off +0.30).
- **Volumetric weight wins** (1 kg actual, `50x40x30` cm -> 12.000 kg volumetric):
  chargeable weight 12.000, matched the 5-10 kg slab's overage arithmetic
  (`180 + ceil(2/0.5)*25 = 280.00`), matching the exact number `RateServiceImplTest`'s own
  overage unit test asserts for the same math — net **348.00**.
- **Serviceability refusal**: pickup pincode `411099` (deliberately unserviceable) — 422,
  `"Pickup pincode 411099 is not serviceable."`
- **Invalid weight**: `actualWeight: 0` — 400, bean validation (`must be greater than
  zero`), caught before any service call.
- **Unknown service type id** — 422, `"No such service type: <id>"`.
- **Discount**: 10% on a 135.70 pre-discount total — 13.57 discount, rounds to net
  **122.00** (round off -0.13), by hand-checked arithmetic.
- **Anonymous** — 401. **`SUPER_ADMIN`** (no company of their own, only the platform
  binding) — refused with "No route runs...", the same practical outcome as every other
  "platform never touches a company's operational records" check, though this endpoint's
  tier is `isAuthenticated()` like Rate Master's own calculate, not a `COMPANY_ADMIN`-only
  gate.
- `GET /v3/api-docs` — 200; the Pricing Engine tag and `/api/v1/pricing/calculate` are
  registered and documented.

**Not exercised**: a genuine weight-slab gap (the dev fixtures' `GAP-LOW`/`GAP-HIGH` pair
now has `RATE-UI-TEST` filling the middle, left over from Rate Master's own verification
pass, so there is no real gap left in this combination today) — covered instead by
`FreightCalculatorTest.gapBetweenSlabsRejected` against hand-built candidates. A
`BRANCH_MANAGER`/`BOOKING_OPERATOR`-scoped token (no such user exists in the dev fixtures
yet — the same long-standing gap every module's verification note already records), and
the still-missing `RIVAL_CO` cross-company check (this module makes no company-owned
writes of its own to leak, but the Route/Rate/Pincode reads underneath it inherit the same
gap those modules already carry).

## Testing

55 backend unit tests: 3 domain (weight/volumetric/rounding), 4 validation, 8 calculator
classes covering their own toggle + arithmetic, 3 on the real `StandardPricingStrategy`
wired with all eight real calculators together (the "grand total" end-to-end check —
Freight through Net Amount, and that a disabled line contributes zero rather than being
skipped), 2 on `PricingFactoryImpl`, 1 end-to-end on `PricingEngineImpl` (mocked
validations + factory, asserting the wiring order and that dimensions produce the right
chargeable weight). `mvn test` moved 573 -> 627. No frontend work — the module's Definition
of Done does not ask for a UI, unlike every prior module.
