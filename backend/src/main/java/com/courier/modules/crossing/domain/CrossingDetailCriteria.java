package com.courier.modules.crossing.domain;

import java.util.UUID;

/**
 * @param companyId  always set by the service, never taken from the request
 * @param shipmentId optional — narrow to one shipment's crossing
 * @param branchId   optional — narrow to one crossing branch/hub
 * @param status     optional
 */
public record CrossingDetailCriteria(UUID companyId, UUID shipmentId, UUID branchId, CrossingStatus status) {

    public static CrossingDetailCriteria none() {
        return new CrossingDetailCriteria(null, null, null, null);
    }

    public CrossingDetailCriteria scopedTo(UUID enforcedCompanyId) {
        return new CrossingDetailCriteria(enforcedCompanyId, shipmentId, branchId, status);
    }
}
