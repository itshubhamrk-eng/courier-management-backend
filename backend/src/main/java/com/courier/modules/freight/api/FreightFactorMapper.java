package com.courier.modules.freight.api;

import com.courier.modules.freight.api.dto.CreateFreightFactorRequest;
import com.courier.modules.freight.api.dto.FreightCalculationRequest;
import com.courier.modules.freight.api.dto.FreightCalculationResponse;
import com.courier.modules.freight.api.dto.FreightFactorResponse;
import com.courier.modules.freight.api.dto.FreightFactorSearchRequest;
import com.courier.modules.freight.api.dto.UpdateFreightFactorRequest;
import com.courier.modules.freight.application.FreightCalculationResult;
import com.courier.modules.freight.application.command.CreateFreightFactorCommand;
import com.courier.modules.freight.application.command.FreightCalculationCommand;
import com.courier.modules.freight.application.command.UpdateFreightFactorCommand;
import com.courier.modules.freight.domain.FreightFactor;
import com.courier.modules.freight.domain.FreightFactorCriteria;
import org.springframework.stereotype.Component;

/** Wire contract <-> application/domain types for freight factors. */
@Component
public class FreightFactorMapper {

    public CreateFreightFactorCommand toCommand(CreateFreightFactorRequest r) {
        return new CreateFreightFactorCommand(r.fromKm(), r.toKm(), r.fromWeight(), r.toWeight(), r.factor());
    }

    public UpdateFreightFactorCommand toCommand(UpdateFreightFactorRequest r) {
        return new UpdateFreightFactorCommand(r.fromKm(), r.toKm(), r.fromWeight(), r.toWeight(),
                r.factor(), r.version());
    }

    public FreightFactorCriteria toCriteria(FreightFactorSearchRequest r) {
        FreightFactorSearchRequest safe = r == null ? FreightFactorSearchRequest.empty() : r;
        return new FreightFactorCriteria(safe.status());
    }

    public FreightCalculationCommand toCommand(FreightCalculationRequest r) {
        return new FreightCalculationCommand(r.fromBranchId(), r.toBranchId(), r.weight());
    }

    public FreightFactorResponse toResponse(FreightFactor f) {
        return new FreightFactorResponse(
                f.getId(), f.getCompanyId(),
                f.getFromKm(), f.getToKm(), f.getFromWeight(), f.getToWeight(),
                f.getFactor(), f.getStatus(),
                f.getCreatedBy(), f.getCreatedAt(), f.getUpdatedBy(), f.getUpdatedAt(), f.getVersion());
    }

    public FreightCalculationResponse toResponse(FreightCalculationResult result) {
        return new FreightCalculationResponse(
                result.matchedFactor().getId(), result.distanceKm(), result.weight(),
                result.matchedFactor().getFactor(), result.freight());
    }
}
