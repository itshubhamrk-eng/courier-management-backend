package com.courier.modules.districtfreight.application;

import com.courier.modules.districtfreight.application.command.CreateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.application.command.UpdateDistrictLevelFreightCommand;
import com.courier.modules.districtfreight.domain.DistrictLevelFreight;
import com.courier.modules.districtfreight.domain.DistrictLevelFreightCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Use cases for District Level Freight rate setup.
 *
 * <p><b>Audiences</b>, enforced by {@code @PreAuthorize} on the implementation —
 * {@code COMPANY_ADMIN} writes, any authenticated company user reads, mirroring
 * {@code RateService} exactly.
 */
public interface DistrictLevelFreightService {

    DistrictLevelFreight create(CreateDistrictLevelFreightCommand command);

    DistrictLevelFreight update(UUID id, UpdateDistrictLevelFreightCommand command);

    DistrictLevelFreight getById(UUID id);

    Page<DistrictLevelFreight> search(DistrictLevelFreightCriteria criteria, Pageable pageable);

    /** Soft delete. Nothing in this codebase references a row yet (rate-setup only, not
     *  wired into booking), so it is always permitted once the row itself resolves. */
    void delete(UUID id);

    DistrictLevelFreight activate(UUID id);

    DistrictLevelFreight deactivate(UUID id);
}
