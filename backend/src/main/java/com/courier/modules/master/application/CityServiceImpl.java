package com.courier.modules.master.application;

import com.courier.modules.master.application.command.CityCommand;
import com.courier.modules.master.domain.AreaRepository;
import com.courier.modules.master.domain.City;
import com.courier.modules.master.domain.CityRepository;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.modules.master.domain.MasterDataCriteria;
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

/** Cities, within a district. */
@Slf4j
@Service
public class CityServiceImpl extends AbstractMasterDataService<City> implements CityService {

    /**
     * Global list: only a super admin may change the geography every company shares.
     * A company admin editing {@code PUNE} would be editing it for everyone.
     */
    private static final String WRITE = "hasRole('" + Roles.SUPER_ADMIN + "')";

    /** Anyone signed in reads it — a booking clerk needs the map to book anything. */
    private static final String READ = "isAuthenticated()";

    private final DistrictRepository districts;
    private final AreaRepository areas;

    public CityServiceImpl(CityRepository cities,
                           DistrictRepository districts,
                           AreaRepository areas,
                           MasterUniquenessChecker uniqueness,
                           AuditService auditService) {
        super(cities, uniqueness, auditService, "City", MasterTable.CITIES);
        this.districts = districts;
        this.areas = areas;
    }

    @Override
    protected boolean global() {
        return true;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public City create(CityCommand command) {
        UUID companyId = requireCompany();
        requireDistrict(command.districtId(), companyId, true);

        City city = new City();
        applyCommonFields(city, command.code(), command.name(), command.description(),
                command.displayOrder());
        city.setDistrictId(command.districtId());
        city.setMetro(Boolean.TRUE.equals(command.metro()));
        city.setCityTier(command.cityTier());
        return createEntity(city);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public City update(UUID id, CityCommand command) {
        UUID companyId = requireCompany();
        return updateEntity(id, command.expectedVersion(), city -> {
            boolean reparented = !Objects.equals(city.getDistrictId(), command.districtId());
            requireDistrict(command.districtId(), companyId, reparented);

            applyCommonFields(city, null, command.name(), command.description(),
                    command.displayOrder());
            city.setDistrictId(command.districtId());
            city.setMetro(Boolean.TRUE.equals(command.metro()));
            city.setCityTier(command.cityTier());
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public City getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<City> search(MasterDataCriteria criteria, Pageable pageable) {
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
    public City activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public City deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(City city, UUID companyId, UUID excludeId) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("district_id", city.getDistrictId());
        scope.put("name", city.getName());
        requireAvailable(companyId, excludeId, scope, "name", city.getName());
    }

    @Override
    protected void requireActivatable(City city, UUID companyId) {
        requireDistrict(city.getDistrictId(), companyId, true);
    }

    @Override
    protected void requireDeletable(City city, UUID companyId) {
        long dependants = areas.countByCompanyIdAndCityId(companyId, city.getId());
        if (dependants > 0) {
            throw new BusinessRuleException(
                    "%s still has %d area(s). Delete or move them first."
                            .formatted(city.getName(), dependants));
        }
    }

    @Override
    protected Map<String, Object> snapshot(City city) {
        Map<String, Object> values = super.snapshot(city);
        values.put("districtId", String.valueOf(city.getDistrictId()));
        values.put("metro", city.isMetro());
        values.put("cityTier", city.getCityTier());
        return values;
    }

    private void requireDistrict(UUID districtId, UUID companyId, boolean mustBeActive) {
        if (districtId == null) {
            throw new BusinessRuleException("A city must belong to a district.");
        }
        District district = districts.findByIdWithinCompany(districtId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No district of this company has id %s.".formatted(districtId)));
        if (mustBeActive && !district.isActive()) {
            throw new BusinessRuleException(
                    "District %s is inactive, so nothing new may be filed under it."
                            .formatted(district.getCode()));
        }
    }
}
