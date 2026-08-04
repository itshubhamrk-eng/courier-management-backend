package com.courier.modules.master.application;

import com.courier.modules.master.application.command.StateCommand;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.CountryRepository;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.modules.master.domain.State;
import com.courier.modules.master.domain.StateRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hierarchy rules, exercised through the state list. Districts, cities, areas and
 * pincodes repeat the same shape one level down.
 *
 * <p>The distinction worth defending is between "the parent must exist in this company" —
 * always, because that is the company boundary — and "the parent must be active" — only
 * when it is being set or changed, or the child is being activated.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StateServiceImplTest {

    /**
     * The geography lists are global (V12), so every row belongs to the platform, not to
     * whichever company the caller happens to be in. Binding a random id here would test
     * a path production no longer takes.
     */
    private static final UUID TENANT = GlobalMasters.PLATFORM_COMPANY_ID;

    @Mock private StateRepository states;
    @Mock private CountryRepository countries;
    @Mock private DistrictRepository districts;
    @Mock private MasterUniquenessChecker uniqueness;
    @Mock private AuditService auditService;

    private StateServiceImpl service;
    private Country india;

    @BeforeEach
    void setUp() {
        service = new StateServiceImpl(states, countries, districts, uniqueness, auditService);
        CompanyContext.setCompanyId(TENANT);
        signedIn();

        india = new Country();
        india.setCode("INDIA");
        india.setName("India");
        india.setCompanyId(TENANT);

        when(states.saveAndFlush(any(State.class))).thenAnswer(i -> i.getArgument(0));
        when(countries.findByIdWithinCompany(india.getId(), TENANT)).thenReturn(Optional.of(india));
        when(uniqueness.isCodeTaken(anyString(), any(), any(), anyString())).thenReturn(false);
        when(uniqueness.isTaken(anyString(), any(), any(), anyMap())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a state is created under an active country of the same company")
    void createsUnderActiveParent() {
        State created = service.create(command("MH", "Maharashtra", india.getId(), 3L));

        assertThat(created.getCountryId()).isEqualTo(india.getId());
        assertThat(created.getCode()).isEqualTo("MH");
    }

    @Test
    @DisplayName("a country id from another company is refused as unknown")
    void foreignParentRefused() {
        // findByIdWithinCompany returns empty for a foreign row, so nothing distinguishes
        // "another company's country" from "no such country" in the answer.
        UUID foreign = UUID.randomUUID();
        when(countries.findByIdWithinCompany(foreign, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command("MH", "Maharashtra", foreign, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No country of this company");

        verify(states, never()).save(any());
    }

    @Test
    @DisplayName("nothing new may be filed under an inactive country")
    void inactiveParentRefusedOnCreate() {
        india.deactivate();

        assertThatThrownBy(() -> service.create(command("MH", "Maharashtra", india.getId(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive");
    }

    @Test
    @DisplayName("editing a state under an inactive country still works if the parent is unchanged")
    void inactiveParentAllowedWhenNotReparenting() {
        // Otherwise correcting a typo in a state whose country was deactivated last week
        // would be impossible.
        india.deactivate();
        State existing = existing("MH", "Maharashtra", india.getId());
        when(states.findByIdWithinCompany(existing.getId(), TENANT)).thenReturn(Optional.of(existing));

        State updated = service.update(existing.getId(),
                new StateCommand(null, "Maharashtra State", null, 0, india.getId(), "27", 2L));

        assertThat(updated.getName()).isEqualTo("Maharashtra State");
    }

    @Test
    @DisplayName("moving a state to a different country requires that country to be active")
    void reparentingRequiresActiveTarget() {
        State existing = existing("MH", "Maharashtra", india.getId());
        when(states.findByIdWithinCompany(existing.getId(), TENANT)).thenReturn(Optional.of(existing));

        Country nepal = new Country();
        nepal.setCode("NEPAL");
        nepal.setName("Nepal");
        nepal.setCompanyId(TENANT);
        nepal.deactivate();
        when(countries.findByIdWithinCompany(nepal.getId(), TENANT)).thenReturn(Optional.of(nepal));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new StateCommand(null, "Maharashtra", null, 0, nepal.getId(), null, 2L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive");
    }

    @Test
    @DisplayName("a state name is unique within its country, not within the company")
    void nameUniqueWithinParent() {
        // Two countries may each have a "Western Province"; refusing the second would be
        // wrong, and the scope of the check is what makes the difference.
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("country_id", india.getId());
        scope.put("name", "Maharashtra");
        when(uniqueness.isTaken(eq(MasterTable.STATES), eq(TENANT), isNull(), eq(scope)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(command("MH2", "Maharashtra", india.getId(), null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("a state with districts cannot be deleted")
    void deleteRefusedWithChildren() {
        State existing = existing("MH", "Maharashtra", india.getId());
        when(states.findByIdWithinCompany(existing.getId(), TENANT)).thenReturn(Optional.of(existing));
        when(districts.countByCompanyIdAndStateId(TENANT, existing.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still has 2 district(s)");
    }

    @Test
    @DisplayName("a state cannot be activated while its country is inactive")
    void activationChecksParent() {
        india.deactivate();
        State existing = existing("MH", "Maharashtra", india.getId());
        existing.deactivate();
        when(states.findByIdWithinCompany(existing.getId(), TENANT)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.activate(existing.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive");
    }

    // ---------------------------------------------------------------- helpers

    private void signedIn() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), TENANT, "admin@legacy.test",
                Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static StateCommand command(String code, String name, UUID countryId, Long version) {
        return new StateCommand(code, name, null, 0, countryId, null, version);
    }

    private static State existing(String code, String name, UUID countryId) {
        State state = new State();
        state.setCode(code);
        state.setName(name);
        state.setCountryId(countryId);
        state.setCompanyId(TENANT);
        state.setVersion(2L);
        return state;
    }
}
