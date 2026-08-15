package com.courier.modules.pricing.api;

import com.courier.modules.pricing.api.dto.ChargeBreakup;
import com.courier.modules.pricing.api.dto.PricingRequest;
import com.courier.modules.pricing.api.dto.PricingResponse;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.command.PricingCommand;
import com.courier.modules.rate.domain.WeightUnit;
import org.springframework.stereotype.Component;

/** The only place this module's other-module domain entities ({@code Route}, {@code Rate})
 * get read down to plain values before crossing the wire. */
@Component
public class PricingMapper {

    public PricingCommand toCommand(PricingRequest request) {
        return new PricingCommand(
                request.bookingBranchId(),
                request.deliveryBranchId(),
                request.pickupPincode(),
                request.deliveryPincode(),
                request.serviceTypeId(),
                request.packageTypeId(),
                request.paymentModeId(),
                request.actualWeight(),
                request.length(),
                request.width(),
                request.height(),
                request.declaredValue(),
                request.bookingDate(),
                request.discountPercentage(),
                request.discountAmount(),
                request.freightFactorOverride());
    }

    /**
     * {@code matchedRoute}/{@code matchedRate} are both null when the Pricing Engine fell
     * back to Freight Factor (no route/rate for this lane) — {@code bookingBranchId}/
     * {@code deliveryBranchId} come from the original request instead of the (absent)
     * matched route in that case; {@code weightUnit} defaults to plain kg, the unit
     * Freight Factor itself always prices in.
     */
    public PricingResponse toResponse(PricingCommand command, PricingResult result) {
        ChargeBreakup breakup = new ChargeBreakup(
                result.freight(),
                result.fuelCharge(),
                result.handlingCharge(),
                result.odaCharge(),
                result.insuranceCharge(),
                result.gstAmount(),
                result.discountAmount(),
                result.roundOff(),
                result.netAmount());

        var route = result.matchedRoute();
        var rate = result.matchedRate();

        return new PricingResponse(
                route == null ? command.bookingBranchId() : route.getBookingBranchId(),
                route == null ? command.deliveryBranchId() : route.getDeliveryBranchId(),
                route == null ? null : route.getId(),
                route == null ? null : route.getCode(),
                rate == null ? null : rate.getId(),
                rate == null ? null : rate.getRateCode(),
                rate == null ? null : rate.getRateName(),
                result.actualWeight(),
                result.volumetricWeight(),
                result.chargeableWeight(),
                rate == null ? WeightUnit.KG.name() : rate.getWeightUnit().name(),
                result.appliedFreightFactor(),
                breakup);
    }
}
