package com.courier.modules.master.application;

import com.courier.modules.master.application.command.CountryCommand;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.CountryRepository;
import com.courier.modules.master.domain.MasterDataCriteria;
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

import java.util.Map;
import java.util.UUID;

/** Countries. The root of the geography hierarchy. */
@Slf4j
@Service
public class CountryServiceImpl extends AbstractMasterDataService<Country> implements CountryService {

    /**
     * Global list: only a super admin may change the geography every company shares.
     * A company admin editing {@code PUNE} would be editing it for everyone.
     */
    private static final String WRITE = "hasRole('" + Roles.SUPER_ADMIN + "')";

    /** Anyone signed in reads it — a booking clerk needs the map to book anything. */
    private static final String READ = "isAuthenticated()";

    private final CountryRepository countries;
    private final StateRepository states;

    public CountryServiceImpl(CountryRepository countries,
                              StateRepository states,
                              MasterUniquenessChecker uniqueness,
                              AuditService auditService) {
        super(countries, uniqueness, auditService, "Country", MasterTable.COUNTRIES);
        this.countries = countries;
        this.states = states;
    }

    @Override
    protected boolean global() {
        return true;
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Country create(CountryCommand command) {
        Country country = new Country();
        applyCommonFields(country, command.code(), command.name(), command.description(),
                command.displayOrder());
        applySpecific(country, command);
        return createEntity(country);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Country update(UUID id, CountryCommand command) {
        return updateEntity(id, command.expectedVersion(), country -> {
            applyCommonFields(country, null, command.name(), command.description(),
                    command.displayOrder());
            applySpecific(country, command);
        });
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Country getById(UUID id) {
        return doGetById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize(READ)
    public Page<Country> search(MasterDataCriteria criteria, Pageable pageable) {
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
    public Country activate(UUID id) {
        return doActivate(id);
    }

    @Override
    @Transactional
    @PreAuthorize(WRITE)
    public Country deactivate(UUID id) {
        return doDeactivate(id);
    }

    // ---------------------------------------------------------------------------- rules

    @Override
    protected void validateBeforeSave(Country country, UUID companyId, UUID excludeId) {
        requireAvailable(companyId, excludeId, Map.of("name", country.getName()),
                "name", country.getName());
    }

    @Override
    protected void requireDeletable(Country country, UUID companyId) {
        long dependants = states.countByCompanyIdAndCountryId(companyId, country.getId());
        if (dependants > 0) {
            // Refused rather than cascaded. Cascading a delete down five levels of
            // geography from one click is the kind of thing nobody expects until it has
            // already happened to their production data.
            throw new BusinessRuleException(
                    "%s still has %d state(s). Delete or move them first."
                            .formatted(country.getName(), dependants));
        }
    }

    @Override
    protected Map<String, Object> snapshot(Country country) {
        Map<String, Object> values = super.snapshot(country);
        values.put("isoCode2", country.getIsoCode2());
        values.put("isoCode3", country.getIsoCode3());
        values.put("dialCode", country.getDialCode());
        values.put("currencyCode", country.getCurrencyCode());
        return values;
    }

    private void applySpecific(Country country, CountryCommand command) {
        country.setIsoCode2(command.isoCode2());
        country.setIsoCode3(command.isoCode3());
        country.setDialCode(command.dialCode());
        country.setCurrencyCode(command.currencyCode());
    }
}
