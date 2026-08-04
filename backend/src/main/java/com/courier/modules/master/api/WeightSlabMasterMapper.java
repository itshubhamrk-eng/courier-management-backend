package com.courier.modules.master.api;

import com.courier.modules.master.api.dto.CreateWeightSlabRequest;
import com.courier.modules.master.api.dto.UpdateWeightSlabRequest;
import com.courier.modules.master.api.dto.WeightSlabResponse;
import com.courier.modules.master.application.command.WeightSlabCommand;
import com.courier.modules.master.domain.WeightSlab;
import com.courier.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Wire contract to application/domain types for weight slabs. */
@Component
public class WeightSlabMasterMapper {

    public WeightSlabCommand toCommand(CreateWeightSlabRequest r) {
        return new WeightSlabCommand(r.code(), r.name(), r.description(), r.displayOrder(),
                r.minWeight(), r.maxWeight(), r.weightUnit(), null);
    }

    public WeightSlabCommand toCommand(UpdateWeightSlabRequest r) {
        return new WeightSlabCommand(null, r.name(), r.description(), r.displayOrder(),
                r.minWeight(), r.maxWeight(), r.weightUnit(), r.version());
    }

    public WeightSlabResponse toResponse(WeightSlab w) {
        return new WeightSlabResponse(w.getId(), w.getCompanyId(), w.getCode(), w.getName(),
                w.getDescription(), w.getStatus(), w.getDisplayOrder(),
                w.getMinWeight(), w.getMaxWeight(), w.getWeightUnit(),
                w.getCreatedBy(), w.getCreatedAt(), w.getUpdatedBy(), w.getUpdatedAt(), w.getVersion());
    }

    public PageResponse<WeightSlabResponse> toPage(Page<WeightSlab> page) {
        return PageResponse.from(page, this::toResponse);
    }
}
