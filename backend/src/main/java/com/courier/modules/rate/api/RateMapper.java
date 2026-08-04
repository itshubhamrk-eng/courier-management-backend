package com.courier.modules.rate.api;

import com.courier.modules.rate.api.dto.CreateRateRequest;
import com.courier.modules.rate.api.dto.RateCalculationRequest;
import com.courier.modules.rate.api.dto.RateCalculationResponse;
import com.courier.modules.rate.api.dto.RateResponse;
import com.courier.modules.rate.api.dto.RateSearchRequest;
import com.courier.modules.rate.api.dto.RateSummaryResponse;
import com.courier.modules.rate.api.dto.UpdateRateRequest;
import com.courier.modules.rate.application.RateCalculationResult;
import com.courier.modules.rate.application.command.CreateRateCommand;
import com.courier.modules.rate.application.command.RateCalculationCommand;
import com.courier.modules.rate.application.command.UpdateRateCommand;
import com.courier.modules.rate.domain.Rate;
import com.courier.modules.rate.domain.RateCriteria;
import org.springframework.stereotype.Component;

/** Wire contract <-> application/domain types for rates. */
@Component
public class RateMapper {

    public CreateRateCommand toCommand(CreateRateRequest r) {
        return new CreateRateCommand(
                r.rateCode(), r.rateName(), r.routeId(), r.serviceTypeId(), r.packageTypeId(),
                r.paymentModeId(), r.minimumWeight(), r.maximumWeight(), r.weightUnit(),
                r.baseRate(), r.additionalWeight(), r.additionalWeightRate(), r.minimumCharge(),
                r.fuelSurcharge(), r.handlingCharge(), r.odaCharge(), r.insuranceCharge(),
                r.gstPercentage(), r.effectiveFrom(), r.effectiveTo());
    }

    public UpdateRateCommand toCommand(UpdateRateRequest r) {
        return new UpdateRateCommand(
                r.rateName(), r.routeId(), r.serviceTypeId(), r.packageTypeId(), r.paymentModeId(),
                r.minimumWeight(), r.maximumWeight(), r.weightUnit(),
                r.baseRate(), r.additionalWeight(), r.additionalWeightRate(), r.minimumCharge(),
                r.fuelSurcharge(), r.handlingCharge(), r.odaCharge(), r.insuranceCharge(),
                r.gstPercentage(), r.effectiveFrom(), r.effectiveTo(), r.version());
    }

    public RateCriteria toCriteria(RateSearchRequest r) {
        RateSearchRequest safe = r == null ? RateSearchRequest.empty() : r;
        return new RateCriteria(safe.routeId(), safe.serviceTypeId(), safe.packageTypeId(),
                safe.paymentModeId(), safe.status(), safe.search());
    }

    public RateCalculationCommand toCommand(RateCalculationRequest r) {
        return new RateCalculationCommand(r.bookingBranchId(), r.deliveryBranchId(),
                r.serviceTypeId(), r.packageTypeId(), r.paymentModeId(), r.actualWeight(),
                r.bookingDate());
    }

    public RateResponse toResponse(Rate r) {
        return new RateResponse(
                r.getId(), r.getCompanyId(), r.getRateCode(), r.getRateName(),
                r.getRouteId(), r.getServiceTypeId(), r.getPackageTypeId(), r.getPaymentModeId(),
                r.getMinimumWeight(), r.getMaximumWeight(), r.getWeightUnit(),
                r.getBaseRate(), r.getAdditionalWeight(), r.getAdditionalWeightRate(),
                r.getMinimumCharge(), r.getFuelSurcharge(), r.getHandlingCharge(),
                r.getOdaCharge(), r.getInsuranceCharge(), r.getGstPercentage(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getStatus(),
                r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedBy(), r.getUpdatedAt(), r.getVersion());
    }

    public RateSummaryResponse toSummary(Rate r) {
        return new RateSummaryResponse(
                r.getId(), r.getRateCode(), r.getRateName(),
                r.getRouteId(), r.getServiceTypeId(), r.getPackageTypeId(), r.getPaymentModeId(),
                r.getMinimumWeight(), r.getMaximumWeight(), r.getWeightUnit(),
                r.getBaseRate(), r.getStatus(), r.getEffectiveFrom(), r.getEffectiveTo(), r.getVersion());
    }

    public RateCalculationResponse toResponse(RateCalculationResult result) {
        Rate matched = result.matchedRate();
        return new RateCalculationResponse(
                matched.getId(), matched.getRateCode(), matched.getRateName(),
                result.chargeableWeight(), matched.getWeightUnit(),
                result.freight(), result.fuelSurcharge(), result.handlingCharge(),
                result.odaCharge(), result.insuranceCharge(), matched.getGstPercentage(),
                result.gstAmount(), result.totalAmount());
    }
}
