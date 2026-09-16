package com.courier.modules.pricing.application.calculator;

import com.courier.modules.charge.domain.Charge;
import com.courier.modules.charge.domain.ChargeCriteria;
import com.courier.modules.charge.domain.ChargeRepository;
import com.courier.modules.charge.domain.ChargeSetting;
import com.courier.modules.charge.domain.ChargeSettingRepository;
import com.courier.modules.charge.domain.ChargeSlabType;
import com.courier.modules.charge.domain.ChargeSpecifications;
import com.courier.modules.charge.domain.ChargeStatus;
import com.courier.modules.charge.domain.ChargeValueType;
import com.courier.modules.pricing.application.PricingContext;
import com.courier.modules.pricing.domain.ChargeType;
import com.courier.shared.company.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sums every ACTIVE {@code com.courier.modules.charge.domain.Charge} configured against
 * this booking's service type (e.g. "Hamali") — a {@code SLAB} charge setting
 * contributes the band covering this shipment's chargeable weight and/or the matched
 * route's distance, a {@code FACTOR} setting always contributes its flat/percentage
 * value. At most one setting per charge applies (the {@code charge} module's own overlap
 * validation guarantees no two ACTIVE settings under one charge cover the same band).
 *
 * <p>Reads the {@code charge} module's repositories directly, bypassing
 * {@code ChargeServiceImpl}/{@code ChargeSettingServiceImpl}'s {@code COMPANY_ADMIN}-only
 * gate — the same pattern the rest of this engine already uses toward other modules'
 * domain layer (routes, rates, company settings): booking a shipment is a
 * {@code WRITERS}-tier operation (also {@code BRANCH_MANAGER}/{@code OPERATOR}), not
 * admin-only, even though authoring a {@code Charge} itself is.
 *
 * <p>{@code PERCENTAGE}-type charge values are taken as a percentage of {@code FREIGHT} —
 * the only base available at this point in the chain (Freight runs at order 10; this
 * runs at 55, right after Insurance) — an assumption made in the absence of any
 * documented base in the {@code charge} module itself, since no {@code PERCENTAGE}
 * charge has been authored yet. Revisit if that turns out wrong once one is.
 *
 * <p>{@code distanceKm} is resolved directly off {@code AddressDistanceService
 * .resolveBranchDistance(bookingBranchId, deliveryBranchId)} — never off {@code
 * context.matchedRoute()}. A matched {@code Route} only exists when Rate Master's own
 * Route/Rate lookup succeeded; most lanes price through District Level Freight's own
 * fallback instead (see {@code PricingEngineImpl}'s Freight Factor fallback), which never
 * sets {@code matchedRoute} at all — a KM-slab (or BOTH-slab) {@code ChargeSetting} would
 * silently never match on any of those bookings otherwise, exactly the same gap Freight
 * Factor's own distance resolution already had to work around. Same graceful-null
 * treatment as that fallback: a same-branch pair, an ungeocoded branch, or any other
 * {@code BusinessRuleException} from the lookup degrades to "no KM known" rather than
 * blocking the booking — a KM/BOTH-slab charge setting simply doesn't match that shipment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicableChargesCalculator implements ChargeCalculator {

    private final ChargeRepository chargeRepository;
    private final ChargeSettingRepository chargeSettingRepository;

    @Override
    public ChargeType type() {
        return ChargeType.APPLICABLE_CHARGES;
    }

    @Override
    public int order() {
        return 55;
    }

    @Override
    public boolean isEnabled(PricingContext context) {
        return true;
    }

    @Override
    public BigDecimal calculate(PricingContext context) {
        return resolve(context.command().serviceTypeId(), context.chargeableWeight(),
                context.command().totalActualWeight(), context.command().numberOfPackages(),
                context.command().bookingBranchId(), context.command().deliveryBranchId(),
                context.charge(ChargeType.FREIGHT))
                .stream()
                .map(Line::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** One matched {@code Charge}'s own name and the amount it contributed — e.g. "Hamali",
     *  10.00 — kept alongside {@link #calculate} rather than replacing it, since most
     *  callers (the pricing chain itself) only ever need the sum. Used to show a shipment's
     *  applicable charges by name instead of one lumped total. */
    public record Line(String chargeName, BigDecimal amount) {
    }

    /** Plain-parameter core of this calculator — no {@link PricingContext} required, so a
     *  read-time caller (a shipment's own stored booking/delivery branch, chargeable
     *  weight and freight, none of which need a fresh pricing run) can ask for the same
     *  breakdown a booking itself would have gotten, live, without reconstructing one.
     *
     *  <p>{@code chargeableWeight} slab-matches every ordinary charge; {@code
     *  totalActualWeight}/{@code numberOfPackages} only matter for a {@code Charge} with
     *  {@code isQtyLevel} on (e.g. "Hamali") — see {@link Charge#isQtyLevel()} — which
     *  slab-matches on the average per-piece weight instead and pays out per piece. */
    public List<Line> resolve(UUID serviceTypeId, BigDecimal chargeableWeight, BigDecimal totalActualWeight,
                              Integer numberOfPackages, UUID bookingBranchId,
                              UUID deliveryBranchId, BigDecimal freight) {
        if (serviceTypeId == null) {
            return List.of();
        }

        UUID companyId = CompanyContext.requireCompanyId();
        List<Charge> charges = chargeRepository.findAll(ChargeSpecifications.matching(
                new ChargeCriteria(Set.of(serviceTypeId), Set.of(ChargeStatus.ACTIVE), null)));
        if (charges.isEmpty()) {
            return List.of();
        }

        BigDecimal distanceKm = resolveDistanceKm(bookingBranchId, deliveryBranchId);
        BigDecimal perPieceWeight = (totalActualWeight != null && numberOfPackages != null && numberOfPackages > 0)
                ? totalActualWeight.divide(BigDecimal.valueOf(numberOfPackages), 4, RoundingMode.HALF_UP)
                : null;

        // One round trip for every charge's settings instead of one per charge — this
        // runs on every pricing preview keystroke during booking, so N charges used to
        // mean N+1 queries.
        List<UUID> chargeIds = charges.stream().map(Charge::getId).toList();
        Map<UUID, List<ChargeSetting>> settingsByCharge = chargeSettingRepository
                .findByCompanyIdAndChargeIdInAndStatus(companyId, chargeIds, ChargeStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(ChargeSetting::getChargeId));

        List<Line> lines = new java.util.ArrayList<>();
        for (Charge charge : charges) {
            boolean qtyLevel = charge.isQtyLevel() && perPieceWeight != null;
            BigDecimal matchWeight = qtyLevel ? perPieceWeight : chargeableWeight;
            List<ChargeSetting> settings = settingsByCharge.getOrDefault(charge.getId(), List.of());
            for (ChargeSetting setting : settings) {
                if (!applies(setting, matchWeight, distanceKm)) {
                    continue;
                }
                BigDecimal amount = valueOf(setting, freight);
                if (qtyLevel) {
                    amount = amount.multiply(BigDecimal.valueOf(numberOfPackages));
                }
                lines.add(new Line(charge.getChargeName(), amount.setScale(2, RoundingMode.HALF_UP)));
                break;
            }
        }
        return lines;
    }

    /** Distance resolution is disabled for now — lat/long isn't in use, so every
     *  uncached branch pair paid a real Nominatim + OSRM network round trip on every
     *  pricing call (this calculator runs on every booking/preview, not just the
     *  no-route fallback), which was the actual cause of ~10s pricing latency. Always
     *  null until distance-based booking pricing is wired up for real — a KM/BOTH-slab
     *  charge setting simply doesn't match any booking in the meantime. */
    private BigDecimal resolveDistanceKm(UUID bookingBranchId, UUID deliveryBranchId) {
        return null;
    }

    private BigDecimal valueOf(ChargeSetting setting, BigDecimal freight) {
        if (setting.getChargeValueType() == ChargeValueType.PERCENTAGE) {
            return freight.multiply(setting.getChargeValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return setting.getChargeValue();
    }

    private boolean applies(ChargeSetting setting, BigDecimal weight, BigDecimal distanceKm) {
        if (setting.getChargeType() == com.courier.modules.charge.domain.ChargeType.FACTOR) {
            return true;
        }
        ChargeSlabType slabType = setting.getChargeSlabType();
        boolean kgOk = slabType == ChargeSlabType.KM
                || (weight != null && inRange(weight, setting.getFromKg(), setting.getToKg()));
        boolean kmOk = slabType == ChargeSlabType.KG
                || (distanceKm != null && inRange(distanceKm, setting.getFromKm(), setting.getToKm()));
        return kgOk && kmOk;
    }

    /** Closed {@code [from, to]}, both ends inclusive — direct request ("use 1&lt;= w and
     *  w&lt;=20"): a band typed as "1-20" must include 20 itself, matching how a company
     *  admin naturally reads a slab row, not a half-open convention that silently drops the
     *  boundary. {@link ChargeSetting#overlaps} enforces the matching invariant at write
     *  time — adjacent bands must leave a gap (1-20, 21-40), never share a boundary (1-20,
     *  20-40 is now rejected as overlapping, since 20 would match both). */
    private boolean inRange(BigDecimal value, BigDecimal from, BigDecimal to) {
        return value.compareTo(from) >= 0 && value.compareTo(to) <= 0;
    }
}
