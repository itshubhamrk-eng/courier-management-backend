package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.domain.DistrictFreightLevel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The authoritative outcome of {@link FreightCalculationService#calculate} — every field
 * Shipment Booking's own "Freight Calculation" section displays, plus what {@code
 * ShipmentServiceImpl} needs to feed the freight amount into the existing commission
 * calculation unchanged.
 */
public record FreightCalculationResult(
        UUID matchedFreightId,
        UUID bookingBranchId,
        String bookingBranchCode,
        String bookingBranchName,
        UUID districtId,
        String districtCode,
        String districtName,
        UUID destinationPincodeId,
        String destinationPincode,
        String destinationCityName,
        BigDecimal chargeableWeight,
        String weightSlabLabel,
        BigDecimal ratePerKg,
        BigDecimal baseFreight,
        boolean odaApplicable,
        BigDecimal odaCharge,
        BigDecimal totalFreight,
        /** Name of the TBB Customer pricing zone that priced this booking — null when
         *  District Level Freight did (the default). */
        String customerZoneName,
        /** The zone rate card's non-freight lines (docket + mathadi + pack + fuel % + FOV %),
         *  which the booking must carry as Other Charges. Zero for District Level Freight. */
        BigDecimal customerExtraCharges,
        /** Which District Level Freight row priced this: the district's own, or the state-level
         *  fallback. DISTRICT for zone-priced bookings too (no state fallback involved). */
        DistrictFreightLevel rateLevel,
        /** The destination's state name — set for every District Level Freight pricing. */
        String stateName
) {
    /** Pre-state shape (customer zone included) — district level, state unknown. */
    public FreightCalculationResult(UUID matchedFreightId, UUID bookingBranchId, String bookingBranchCode,
                                    String bookingBranchName, UUID districtId, String districtCode,
                                    String districtName, UUID destinationPincodeId, String destinationPincode,
                                    String destinationCityName, BigDecimal chargeableWeight, String weightSlabLabel,
                                    BigDecimal ratePerKg, BigDecimal baseFreight, boolean odaApplicable,
                                    BigDecimal odaCharge, BigDecimal totalFreight, String customerZoneName,
                                    BigDecimal customerExtraCharges) {
        this(matchedFreightId, bookingBranchId, bookingBranchCode, bookingBranchName, districtId, districtCode,
                districtName, destinationPincodeId, destinationPincode, destinationCityName, chargeableWeight,
                weightSlabLabel, ratePerKg, baseFreight, odaApplicable, odaCharge, totalFreight, customerZoneName,
                customerExtraCharges, DistrictFreightLevel.DISTRICT, null);
    }

    /** District Level Freight's shape — no customer zone involved. */
    public FreightCalculationResult(UUID matchedFreightId, UUID bookingBranchId, String bookingBranchCode,
                                    String bookingBranchName, UUID districtId, String districtCode,
                                    String districtName, UUID destinationPincodeId, String destinationPincode,
                                    String destinationCityName, BigDecimal chargeableWeight, String weightSlabLabel,
                                    BigDecimal ratePerKg, BigDecimal baseFreight, boolean odaApplicable,
                                    BigDecimal odaCharge, BigDecimal totalFreight) {
        this(matchedFreightId, bookingBranchId, bookingBranchCode, bookingBranchName, districtId, districtCode,
                districtName, destinationPincodeId, destinationPincode, destinationCityName, chargeableWeight,
                weightSlabLabel, ratePerKg, baseFreight, odaApplicable, odaCharge, totalFreight, null,
                BigDecimal.ZERO);
    }
}
