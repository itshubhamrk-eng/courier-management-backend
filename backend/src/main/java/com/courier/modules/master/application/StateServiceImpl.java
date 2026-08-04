package com.courier.modules.master.application;

import com.courier.modules.master.application.command.StateCommand;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.CountryRepository;
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

/**
 * States, within a country.
 *
 * <p>Two parent rules, and the difference between them is deliberate:
 * <ul>
 *   <li>The parent must <b>exist in the same company</b> — always. This is the company
 *       boundary: {@code findByIdWithinCompany} returns empty for another company's
 *       country, so a spoofed id is refused rather than linked.</li>
 *   <li>The parent must be <b>active</b> only when it is being set or changed, and when
 *       this state is activated. Otherwise correcting a typo in a state whose country was
 *       deactivated last week would be impossible.</li>
 * </ul>
 */
@Slf4j
@Service
public class StateServiceImpl extends AbstractMasterDataService<State> implements StateService {

    /**
     * Global list: only a super admin may change the geography every company shares.
     * A company admin editing {@code PUNE} would be editing it for everyone.
     */
    private static final String WRITE = "hasRole('" + Roles.SUPER_ADMIN + "')";

    /** Anyone signed in reads it — a booking clerk needs the map to book anything. */
    private static final String READ = "isAuthenticated()";

    private final StateRepository states;
    private final CountryRepository countries;
    private final DistrictRepository districts;

    public StateServiceImpl(StateRepository states,
                            CountryRepository countries,
                            DistrictRepository districts,
                            MasterUniquenessChecker uniqueness,
                            AuditService auditService) {
        super(states, uniqueness, auditService, "State", MasterTable.STATES);
        this.states = states;
        this.countries = countries;
        this.districts = districts;
    }

    @Override
    protected boolean global() {
        return true;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public State create(StateCommand command) {
        UUID companyId = requireCompany();
        requireCountry(command.countryId(), companyId, true);

        State state = new State();
        applyCommonFields(state, command.code(), command.name(), command.description(),
                command.displayOrder());
        state.setCountryId(command.countryId());
        state.setGstStateCode(command.gstStateCode());
        return createEntity(state);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public State update(UUID id, StateCommand command) {
        UUID companyId = requireCompany();
        return updateEntity(id, command.expectedVersion(), state -> {
            boolean reparented = !Objects.equals(state.getCountryId(), command.countryId());
            requireCountry(command.countryId(), companyId, reparented);

            applyCommonFields(state, null, command.name(), command.description(),
                    command.displayOrder());
            state.setCountryId(command.countryId());
            state.setGstStateCode(command.gstStateCode());
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public State getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<State> search(MasterDataCriteria criteria, Pageable pageable) {
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
    public State activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public State deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(State state, UUID companyId, UUID excludeId) {
        // Unique within the parent, not within the company: two countries may each have a
        // "Western Province".
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("country_id", state.getCountryId());
        scope.put("name", state.getName());
        requireAvailable(companyId, excludeId, scope, "name", state.getName());
    }

    @Override
    protected void requireActivatable(State state, UUID companyId) {
        requireCountry(state.getCountryId(), companyId, true);
    }

    @Override
    protected void requireDeletable(State state, UUID companyId) {
        long dependants = districts.countByCompanyIdAndStateId(companyId, state.getId());
        if (dependants > 0) {
            throw new BusinessRuleException(
                    "%s still has %d district(s). Delete or move them first."
                            .formatted(state.getName(), dependants));
        }
    }

    @Override
    protected Map<String, Object> snapshot(State state) {
        Map<String, Object> values = super.snapshot(state);
        values.put("countryId", String.valueOf(state.getCountryId()));
        values.put("gstStateCode", state.getGstStateCode());
        return values;
    }

    private void requireCountry(UUID countryId, UUID companyId, boolean mustBeActive) {
        if (countryId == null) {
            throw new BusinessRuleException("A state must belong to a country.");
        }
        Country country = countries.findByIdWithinCompany(countryId, companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No country of this company has id %s.".formatted(countryId)));
        if (mustBeActive && !country.isActive()) {
            throw new BusinessRuleException(
                    "Country %s is inactive, so nothing new may be filed under it."
                            .formatted(country.getCode()));
        }
    }
}
