package com.courier.modules.master.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Routes between two branches. */
public interface RouteRepository extends MasterDataRepository<Route> {

    Optional<Route> findByCompanyIdAndBookingBranchIdAndDeliveryBranchId(
            UUID companyId, UUID bookingBranchId, UUID deliveryBranchId);

    List<Route> findByCompanyIdAndBookingBranchIdOrderByNameAsc(UUID companyId, UUID bookingBranchId);
}
