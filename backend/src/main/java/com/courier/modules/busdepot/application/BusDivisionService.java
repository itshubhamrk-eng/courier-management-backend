package com.courier.modules.busdepot.application;

import com.courier.modules.busdepot.domain.BusDepotStatus;
import com.courier.modules.busdepot.domain.BusDivision;

import java.util.List;
import java.util.UUID;

/** MSRTC division master. Writes COMPANY_ADMIN; reads any authenticated user. */
public interface BusDivisionService {

    record DivisionInput(String divisionCode, String divisionName) {
    }

    /** A division with how many depots/stations sit under it. */
    record DivisionView(BusDivision division, long depotCount, long stationCount) {
    }

    DivisionView create(DivisionInput input);

    DivisionView update(UUID id, DivisionInput input, Long expectedVersion);

    DivisionView getById(UUID id);

    List<DivisionView> search(BusDepotStatus status, String search);

    DivisionView activate(UUID id);

    DivisionView deactivate(UUID id);

    /** Refused while any depot/station still belongs to the division. */
    void delete(UUID id);
}
