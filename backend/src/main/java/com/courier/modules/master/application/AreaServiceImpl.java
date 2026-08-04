package com.courier.modules.master.application;

import com.courier.modules.master.application.command.AreaCommand;
import com.courier.modules.master.domain.Area;
import com.courier.modules.master.domain.AreaRepository;
import com.courier.modules.master.domain.City;
import com.courier.modules.master.domain.CityRepository;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.PincodeRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Areas, within a city. "One Area belongs to one City" is the {@code cityId} column. */
@Slf4j
@Service
public class AreaServiceImpl extends AbstractMasterDataService<Area> implements AreaService {

    /**
     * Global list: only a super admin may change the geography every company shares.
     * A company admin editing {@code PUNE} would be editing it for everyone.
     */
    private static final String WRITE = "hasRole('" + Roles.SUPER_ADMIN + "')";

    /** Anyone signed in reads it — a booking clerk needs the map to book anything. */
    private static final String READ = "isAuthenticated()";

    private final CityRepository cities;
    private final PincodeRepository pincodes;

    public AreaServiceImpl(AreaRepository areas,
                           CityRepository cities,
                           PincodeRepository pincodes,
                           MasterUniquenessChecker uniqueness,
                           AuditService auditService) {
        super(areas, uniqueness, auditService, "Area", MasterTable.AREAS);
        this.cities = cities;
        this.pincodes = pincodes;
    }

    @Override
    protected boolean global() {
        return true;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Area create(AreaCommand command) {
        UUID companyId = requireCompany();
        requireCity(command.cityId(), companyId, true);

        Area area = new Area();
        applyCommonFields(area, command.code(), command.name(), command.description(),
                command.displayOrder());
        area.setCityId(command.cityId());
        return createEntity(area);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Area update(UUID id, AreaCommand command) {
        UUID companyId = requireCompany();
        return updateEntity(id, command.expectedVersion(), area -> {
            boolean reparented = !Objects.equals(area.getCityId(), command.cityId());
            requireCity(command.cityId(), companyId, reparented);

            applyCommonFields(area, null, command.name(), command.description(),
                    command.displayOrder());
            area.setCityId(command.cityId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Area getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<Area> search(MasterDataCriteria criteria, Pageable pageable) {
        return doSearch(criteria, pageable);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public void delete(UUID id) {
        doDelete(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Area activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Area deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(Area area, UUID companyId, UUID excludeId) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("city_id", area.getCityId());
        scope.put("name", area.getName());
        requireAvailable(companyId, excludeId, scope, "name", area.getName());
    }

    @Override
    protected void requireActivatable(Area area, UUID companyId) {
        requireCity(area.getCityId(), companyId, true);
    }

    @Override
    protected void requireDeletable(Area area, UUID companyId) {
        long dependants = pincodes.countByCompanyIdAndAreaId(companyId, area.getId());
        if (dependants > 0) {
            throw new BusinessRuleException(
                    "%s still has %d pincode(s). Delete or move them first."
                            .formatted(area.getName(), dependants));
        }
    }

    @Override
    protected Map<String, Object> snapshot(Area area) {
        Map<String, Object> values = super.snapshot(area);
        values.put("cityId", String.valueOf(area.getCityId()));
        return values;
    }

    private void requireCity(UUID cityId, UUID companyId, boolean mustBeActive) {
        if (cityId == null) {
            throw new BusinessRuleException("An area must belong to a city.");
        }
        City city = cities.findByIdWithinCompany(cityId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No city of this company has id %s.".formatted(cityId)));
        if (mustBeActive && !city.isActive()) {
            throw new BusinessRuleException(
                    "City %s is inactive, so nothing new may be filed under it."
                            .formatted(city.getCode()));
        }
    }
}
