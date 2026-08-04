package com.courier.modules.pricing.api;

import com.courier.modules.pricing.api.dto.ChargeBreakup;
import com.courier.modules.pricing.api.dto.PricingRequest;
import com.courier.modules.pricing.api.dto.PricingResponse;
import com.courier.modules.pricing.application.PricingResult;
import com.courier.modules.pricing.application.command.PricingCommand;
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
                request.discountAmount());
    }

    public PricingResponse toResponse(PricingResult result) {
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

        return new PricingResponse(
                result.matchedRoute().getBookingBranchId(),
                result.matchedRoute().getDeliveryBranchId(),
                result.matchedRoute().getId(),
                result.matchedRoute().getCode(),
                result.matchedRate().getId(),
                result.matchedRate().getRateCode(),
                result.matchedRate().getRateName(),
                result.actualWeight(),
                result.volumetricWeight(),
                result.chargeableWeight(),
                result.matchedRate().getWeightUnit().name(),
                breakup);
    }
}
