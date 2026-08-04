package com.courier.modules.master.application;

import com.courier.modules.master.application.command.DistrictCommand;
import com.courier.modules.master.domain.CityRepository;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.State;
import com.courier.modules.master.domain.StateRepository;
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

/** Districts, within a state. Same parent rules as {@link StateServiceImpl}. */
@Slf4j
@Service
public class DistrictServiceImpl extends AbstractMasterDataService<District> implements DistrictService {

    /**
     * Global list: only a super admin may change the geography every company shares.
     * A company admin editing {@code PUNE} would be editing it for everyone.
     */
    private static final String WRITE = "hasRole('" + Roles.SUPER_ADMIN + "')";

    /** Anyone signed in reads it — a booking clerk needs the map to book anything. */
    private static final String READ = "isAuthenticated()";

    private final StateRepository states;
    private final CityRepository cities;

    public DistrictServiceImpl(DistrictRepository districts,
                               StateRepository states,
                               CityRepository cities,
                               MasterUniquenessChecker uniqueness,
                               AuditService auditService) {
        super(districts, uniqueness, auditService, "District", MasterTable.DISTRICTS);
        this.states = states;
        this.cities = cities;
    }

    @Override
    protected boolean global() {
        return true;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public District create(DistrictCommand command) {
        UUID companyId = requireCompany();
        requireState(command.stateId(), companyId, true);

        District district = new District();
        applyCommonFields(district, command.code(), command.name(), command.description(),
                command.displayOrder());
        district.setStateId(command.stateId());
        return createEntity(district);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public District update(UUID id, DistrictCommand command) {
        UUID companyId = requireCompany();
        return updateEntity(id, command.expectedVersion(), district -> {
            boolean reparented = !Objects.equals(district.getStateId(), command.stateId());
            requireState(command.stateId(), companyId, reparented);

            applyCommonFields(district, null, command.name(), command.description(),
                    command.displayOrder());
            district.setStateId(command.stateId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public District getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<District> search(MasterDataCriteria criteria, Pageable pageable) {
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
    public District activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public District deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(District district, UUID companyId, UUID excludeId) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("state_id", district.getStateId());
        scope.put("name", district.getName());
        requireAvailable(companyId, excludeId, scope, "name", district.getName());
    }

    @Override
    protected void requireActivatable(District district, UUID companyId) {
        requireState(district.getStateId(), companyId, true);
    }

    @Override
    protected void requireDeletable(District district, UUID companyId) {
        long dependants = cities.countByCompanyIdAndDistrictId(companyId, district.getId());
        if (dependants > 0) {
            throw new BusinessRuleException(
                    "%s still has %d cit%s. Delete or move them first."
                            .formatted(district.getName(), dependants, dependants == 1 ? "y" : "ies"));
        }
    }

    @Override
    protected Map<String, Object> snapshot(District district) {
        Map<String, Object> values = super.snapshot(district);
        values.put("stateId", String.valueOf(district.getStateId()));
        return values;
    }

    private void requireState(UUID stateId, UUID companyId, boolean mustBeActive) {
        if (stateId == null) {
            throw new BusinessRuleException("A district must belong to a state.");
        }
        State state = states.findByIdWithinCompany(stateId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No state of this company has id %s.".formatted(stateId)));
        if (mustBeActive && !state.isActive()) {
            throw new BusinessRuleException(
                    "State %s is inactive, so nothing new may be filed under it."
                            .formatted(state.getCode()));
        }
    }
}
