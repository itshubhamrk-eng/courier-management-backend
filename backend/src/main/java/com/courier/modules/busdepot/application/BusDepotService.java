package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDepotType;

import java.util.List;
import java.util.UUID;

/** Bus depot / station master use cases. Writes are COMPANY_ADMIN; reads any authenticated user. */
public interface BusDepotService {

    /** Editable fields; {@code branchId} null means "not assigned to any branch / vendor". */
    record DepotInput(String depotCode, String depotName, BusDepotType depotType, UUID branchId,
                      UUID divisionId, UUID parentDepotId, String address, String city, String district, String state, String pincode,
                      String contactPerson, String contactMobile) {
    }

    BusDepotView create(DepotInput input);

    BusDepotView update(UUID id, DepotInput input, Long expectedVersion);

    BusDepotView getById(UUID id);

    List<BusDepotView> search(BusDepotStatus status, BusDepotType type, UUID branchId, UUID divisionId, String search);

    BusDepotView activate(UUID id);

    BusDepotView deactivate(UUID id);

    void delete(UUID id);
}
