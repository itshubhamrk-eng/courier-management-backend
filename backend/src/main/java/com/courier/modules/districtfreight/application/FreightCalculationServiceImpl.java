package com.courier.modules.districtfreight.application;

import com.courier.modules.customerpricing.application.PricingZoneService;
import com.courier.modules.customerpricing.application.PricingZoneView;
import com.courier.modules.customerpricing.application.ZoneRateService;
import com.courier.modules.districtfreight.domain.BranchLookupPort;
import com.courier.modules.districtfreight.domain.DistrictFreightStatus;
import com.courier.modules.districtfreight.domain.DistrictLevelFreight;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightRepository;
import com.courier.modules.districtfreight.domain.PincodeCoverageLookupPort;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Freight calculation for Shipment Booking, backed entirely by District Level Freight's
 * own rate setup ({@link DistrictLevelFreightRepository}). One class, two callers —
 * {@code DistrictLevelFreightController}'s live-preview endpoint and {@code
 * ShipmentServiceImpl}'s authoritative booking-time call — so the rate-card lookup never
 * gets duplicated between a "preview" and a "real" implementation.
 *
 * <p>Sequence: Destination Pincode -&gt; {@link PincodeCoverageLookupPort} -&gt;
 * Destination District -&gt; From Station ({@link BranchLookupPort}) -&gt; {@link
 * DistrictLevelFreightRepository#findByCompanyIdAndBranchIdAndDistrictId} -&gt; {@link
 * DistrictLevelFreight#matchWeightSlab} -&gt; Base Freight -&gt; ODA check -&gt; Total
 * Freight. Every failure throws {@link BusinessRuleException} with a message fit to show
 * the booking clerk directly — never a silent fallback to some other freight source.
 */
@Service
@RequiredArgsConstructor
public class FreightCalculationServiceImpl implements FreightCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final String READ = "isAuthenticated()";

    private final DistrictLevelFreightRepository repository;
    private final BranchLookupPort branchLookup;
    private final PincodeCoverageLookupPort coverageLookup;
    private final PricingZoneService zoneService;
    private final ZoneRateService zoneRateService;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public FreightCalculationResult calculate(UUID bookingBranchId, String destinationPincode,
                                               UUID destinationAreaId, BigDecimal chargeableWeight,
                                               UUID businessCustomerId, BigDecimal declaredValue) {
        UUID companyId = requireCompany();

        if (bookingBranchId == null) {
            throw new BusinessRuleException("A From Station (booking branch) is required to calculate freight.");
        }
        if (destinationPincode == null || destinationPincode.isBlank()) {
            throw new BusinessRuleException("A destination pincode is required to calculate freight.");
        }
        if (chargeableWeight == null || chargeableWeight.signum() <= 0) {
            throw new BusinessRuleException("Shipment weight must be greater than zero to calculate freight.");
        }

        BranchLookupPort.BranchRef branch = branchLookup.findBranch(bookingBranchId, companyId)
                .orElseThrow(() -> new BusinessRuleException("No such booking branch: " + bookingBranchId));
        if (!branch.active()) {
            throw new BusinessRuleException(
                    "Branch %s is inactive and cannot be used as a From Station.".formatted(branch.branchCode()));
        }

        PincodeCoverageLookupPort.CoverageRef coverage = (destinationAreaId == null
                ? coverageLookup.findByPincode(destinationPincode)
                : coverageLookup.findByPincodeAndArea(destinationPincode, destinationAreaId))
                .orElseThrow(() -> new BusinessRuleException(
                        "Pincode %s is not on file or has no resolvable district — cannot calculate freight."
                                .formatted(destinationPincode)));
        if (!coverage.serviceable()) {
            throw new BusinessRuleException(
                    "Pincode %s is not serviceable.".formatted(coverage.pincodeCode()));
        }
        if (!coverage.districtActive()) {
            throw new BusinessRuleException(
                    "District %s is inactive and cannot be used as a destination.".formatted(coverage.districtCode()));
        }

        if (businessCustomerId != null) {
            FreightCalculationResult zonePriced = priceFromCustomerZone(businessCustomerId, branch, coverage,
                    chargeableWeight, declaredValue);
            if (zonePriced != null) {
                return zonePriced;
            }
        }

        DistrictLevelFreight freight = repository
                .findByCompanyIdAndBranchIdAndDistrictId(companyId, branch.branchId(), coverage.districtId())
                .filter(row -> row.getStatus() == DistrictFreightStatus.ACTIVE)
                .orElseThrow(() -> new BusinessRuleException(
                        "No District Level Freight configuration exists for %s -> %s. "
                                .formatted(branch.branchCode(), coverage.districtName())
                                + "Set one up under District Level Freight before booking this lane."));

        DistrictLevelFreight.SlabMatch slab = freight.matchWeightSlab(chargeableWeight)
                .orElseThrow(() -> new BusinessRuleException(
                        "Weight %s KG is outside the configured 1-2000 KG range for %s -> %s."
                                .formatted(chargeableWeight.toPlainString(), branch.branchCode(),
                                        coverage.districtName())));

        BigDecimal baseFreight = chargeableWeight.multiply(slab.ratePerKg())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean odaApplies = freight.isOdaApplicable() && coverage.odaApplicable();
        BigDecimal odaCharge = odaApplies
                ? freight.getOdaCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalFreight = baseFreight.add(odaCharge);

        return new FreightCalculationResult(freight.getId(), branch.branchId(), branch.branchCode(),
                branch.branchName(), coverage.districtId(), coverage.districtCode(), coverage.districtName(),
                coverage.pincodeId(), coverage.pincodeCode(), coverage.cityName(), chargeableWeight, slab.label(),
                slab.ratePerKg(), baseFreight, odaApplies, odaCharge, totalFreight);
    }

    /** The customer's zone rate card for this destination, or null when no ACTIVE zone of
     *  theirs covers it (the caller then falls back to District Level Freight). */
    private FreightCalculationResult priceFromCustomerZone(UUID customerId, BranchLookupPort.BranchRef branch,
                                                           PincodeCoverageLookupPort.CoverageRef coverage,
                                                           BigDecimal chargeableWeight, BigDecimal declaredValue) {
        PricingZoneView zone = zoneService.resolve(customerId, coverage.districtId(), coverage.cityId())
                .orElse(null);
        if (zone == null) {
            return null;
        }
        ZoneRateService.Quote quote = zoneRateService.calculate(new ZoneRateService.QuoteCommand(
                customerId, coverage.districtId(), coverage.cityId(), chargeableWeight, declaredValue,
                coverage.odaApplicable(), null));
        BigDecimal oda = quote.odaCharge().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal extras = quote.docketCharge().add(quote.mathadiCharge()).add(quote.packCharge())
                .add(quote.fuelSurcharge()).add(quote.fovCharge()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new FreightCalculationResult(null, branch.branchId(), branch.branchCode(), branch.branchName(),
                coverage.districtId(), coverage.districtCode(), coverage.districtName(), coverage.pincodeId(),
                coverage.pincodeCode(), coverage.cityName(), quote.chargeableWeight(),
                "Zone " + quote.zoneName(), quote.ratePerKg(), quote.freight(), coverage.odaApplicable()
                && oda.signum() > 0, oda, quote.freight().add(oda), quote.zoneName(), extras);
    }

    private UUID requireCompany() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Freight calculation must be performed by "
                        + "a user of that company."));
    }
}
