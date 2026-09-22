package com.courier.modules.crossing.domain;

import java.util.UUID;

/**
 * @param companyId  always set by the service, never taken from the request
 * @param shipmentId optional — narrow to one shipment's exceptions
 * @param hubBranchId optional — narrow to one hub
 * @param status     optional
 */
public record ShipmentExceptionCriteria(UUID companyId, UUID shipmentId, UUID hubBranchId,
                                         ShipmentExceptionStatus status) {

    public static ShipmentExceptionCriteria none() {
        return new ShipmentExceptionCriteria(null, null, null, null);
    }

    public ShipmentExceptionCriteria scopedTo(UUID enforcedCompanyId) {
        return new ShipmentExceptionCriteria(enforcedCompanyId, shipmentId, hubBranchId, status);
    }
}
