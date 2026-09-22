package com.courier.modules.crossing.api;

import com.courier.modules.crossing.api.dto.ExceptionSearchRequest;
import com.courier.modules.crossing.api.dto.HubDashboardResponse;
import com.courier.modules.crossing.api.dto.ShipmentExceptionResponse;
import com.courier.modules.crossing.application.HubDashboardStats;
import com.courier.modules.crossing.domain.ShipmentException;
import com.courier.modules.crossing.domain.ShipmentExceptionCriteria;
import org.springframework.stereotype.Component;

@Component
public class HubOperationsMapper {

    public ShipmentExceptionCriteria toCriteria(ExceptionSearchRequest search) {
        if (search == null) {
            return ShipmentExceptionCriteria.none();
        }
        return new ShipmentExceptionCriteria(null, search.shipmentId(), search.hubBranchId(), search.status());
    }

    public ShipmentExceptionResponse toResponse(ShipmentException e) {
        return new ShipmentExceptionResponse(
                e.getId(), e.getShipmentId(), e.getHubBranchId(), e.getExceptionType(), e.getStatus(),
                e.getRemarks(), e.getRaisedBy(), e.getRaisedAt(),
                e.getResolvedBy(), e.getResolvedAt(), e.getResolutionRemarks(), e.getTicketId());
    }

    public HubDashboardResponse toResponse(HubDashboardStats stats) {
        return new HubDashboardResponse(stats.todaysInbound(), stats.pendingInScan(), stats.shipmentsAtHub(),
                stats.pendingSorting(), stats.readyForDispatch(), stats.dispatchedToday(),
                stats.pendingExceptions(), stats.todaysLoadSheets());
    }
}
