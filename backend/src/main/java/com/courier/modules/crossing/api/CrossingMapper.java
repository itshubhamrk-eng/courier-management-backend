package com.courier.modules.crossing.api;

import com.courier.modules.crossing.api.dto.CrossingResponse;
import com.courier.modules.crossing.api.dto.CrossingSearchRequest;
import com.courier.modules.crossing.domain.CrossingDetail;
import com.courier.modules.crossing.domain.CrossingDetailCriteria;
import org.springframework.stereotype.Component;

@Component
public class CrossingMapper {

    public CrossingDetailCriteria toCriteria(CrossingSearchRequest search) {
        if (search == null) {
            return CrossingDetailCriteria.none();
        }
        return new CrossingDetailCriteria(null, search.shipmentId(), search.branchId(), search.status());
    }

    public CrossingResponse toResponse(CrossingDetail c) {
        return new CrossingResponse(
                c.getId(), c.getCompanyId(), c.getShipmentId(), c.getSequenceOrder(), c.getBranchId(),
                c.getStatus(), c.getCharge(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt(),
                c.getVersion());
    }
}
