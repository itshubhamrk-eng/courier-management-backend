package com.courier.modules.districtfreight.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single freight-calculation entry point District Level Freight exposes — used by
 * both {@code DistrictLevelFreightController}'s live-preview endpoint and {@code
 * ShipmentServiceImpl}'s authoritative booking-time calculation, so the rate-card lookup
 * itself lives in exactly one place.
 *
 * <p>Calculation sequence: Destination Pincode -&gt; Pincode Coverage -&gt; Destination
 * District -&gt; From Station -&gt; District Level Freight Rate -&gt; Applicable Weight
 * Slab -&gt; Base Freight -&gt; ODA check -&gt; Total Freight. The COMPLETE weight prices
 * at exactly one slab's rate, never a progressive split across slabs.
 */
public interface FreightCalculationService {

    /**
     * @param bookingBranchId  the "From Station" — a company branch
     * @param destinationPincode the destination pincode typed/selected at booking
     * @param destinationAreaId the specific Area of that pincode the operator picked from
     *        its Area dropdown, if any — resolves District/ODA off that exact
     *        {@code master_pincode_areas} link instead of the pincode's single legacy
     *        {@code area_id}. Null falls back to the legacy pincode-wide resolution.
     * @param chargeableWeight the shipment's already-computed chargeable weight, in KG
     * @throws com.courier.shared.exception.BusinessRuleException weight is not positive,
     *         weight exceeds the configured maximum (2000 KG), the destination pincode is
     *         not on file / not serviceable / has no resolvable district, or no District
     *         Level Freight configuration exists for this From Station + District
     */
    FreightCalculationResult calculate(UUID bookingBranchId, String destinationPincode, UUID destinationAreaId,
                                        BigDecimal chargeableWeight);
}
