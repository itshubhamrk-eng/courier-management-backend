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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
 */
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
        UUID serviceTypeId = context.command().serviceTypeId();
        if (serviceTypeId == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        UUID companyId = CompanyContext.requireCompanyId();
        List<Charge> charges = chargeRepository.findAll(ChargeSpecifications.matching(
                new ChargeCriteria(Set.of(serviceTypeId), Set.of(ChargeStatus.ACTIVE), null)));

        BigDecimal weight = context.chargeableWeight();
        BigDecimal distanceKm = context.matchedRoute() == null ? null : context.matchedRoute().getDistanceKm();
        BigDecimal freight = context.charge(ChargeType.FREIGHT);

        BigDecimal total = BigDecimal.ZERO;
        for (Charge charge : charges) {
            List<ChargeSetting> settings = chargeSettingRepository
                    .findByCompanyIdAndChargeIdAndStatus(companyId, charge.getId(), ChargeStatus.ACTIVE);
            for (ChargeSetting setting : settings) {
                if (!applies(setting, weight, distanceKm)) {
                    continue;
                }
                total = total.add(valueOf(setting, freight));
                break;
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
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

    /** Half-open {@code [from, to)}, the same slab convention {@code ChargeSetting} itself documents. */
    private boolean inRange(BigDecimal value, BigDecimal from, BigDecimal to) {
        return value.compareTo(from) >= 0 && value.compareTo(to) < 0;
    }
}
