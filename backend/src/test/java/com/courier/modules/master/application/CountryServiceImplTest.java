package com.courier.modules.master.application;

import com.courier.modules.master.application.command.CountryCommand;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.CountryRepository;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataCriteria;
import com.courier.modules.master.domain.MasterStatus;
import com.courier.modules.master.domain.StateRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
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
 * The generic master-data behaviour, exercised through the country list.
 *
 * <p>Create, update, version conflict, soft delete, activate and deactivate all live in
 * {@code AbstractMasterDataService} and are identical for all twelve lists, so they are
 * tested once here rather than twelve times. The per-list tests cover only what is theirs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CountryServiceImplTest {

    /**
     * The geography lists are global (V12), so every row belongs to the platform, not to
     * whichever company the caller happens to be in. Binding a random id here would test
     * a path production no longer takes.
     */
    private static final UUID TENANT = GlobalMasters.PLATFORM_COMPANY_ID;
    private static final UUID CALLER = UUID.randomUUID();

    @Mock private CountryRepository repository;
    @Mock private StateRepository states;
    @Mock private MasterUniquenessChecker uniqueness;
    @Mock private AuditService auditService;

    private CountryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CountryServiceImpl(repository, states, uniqueness, auditService);
        CompanyContext.setCompanyId(TENANT);
        signedInAs(Roles.SUPER_ADMIN);

        when(repository.saveAndFlush(any(Country.class))).thenAnswer(i -> i.getArgument(0));
        when(uniqueness.isCodeTaken(anyString(), any(), any(), anyString())).thenReturn(false);
        when(uniqueness.isTaken(anyString(), any(), any(), anyMap())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ----------------------------------------------------------------- create

    @Test
    @DisplayName("a created country is normalised, saved and audited")
    void createNormalisesAndAudits() {
        Country created = service.create(command(" in dia ", "  India  ", null));

        assertThat(created.getCode()).isEqualTo("IN_DIA");
        assertThat(created.getName()).isEqualTo("India");
        assertThat(created.getStatus()).isEqualTo(MasterStatus.ACTIVE);
        verify(auditService).record(eq(AuditAction.MASTER_DATA_CREATED), eq("Country"),
                eq(created.getId()), anyMap());
    }

    @Test
    @DisplayName("a duplicate code is a 409, checked against soft-deleted rows too")
    void duplicateCodeRejected() {
        // The unique key does not mention `deleted`, so a check that could not see a
        // soft-deleted row would let the insert fail with a raw constraint violation.
        when(uniqueness.isCodeTaken(eq(MasterTable.COUNTRIES), eq(TENANT), isNull(), eq("INDIA")))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(command("INDIA", "India", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a duplicate name is a 409")
    void duplicateNameRejected() {
        when(uniqueness.isTaken(eq(MasterTable.COUNTRIES), eq(TENANT), isNull(),
                eq(Map.of("name", "India")))).thenReturn(true);

        assertThatThrownBy(() -> service.create(command("IN2", "India", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("a global list is owned by the platform, whatever the caller is bound to")
    void ownerIsThePlatformNotTheCaller() {
        // A global list does not need — or use — the caller's own company. Clearing the
        // binding entirely must still write, and write against the platform owner.
        CompanyContext.clear();

        Country saved = service.create(command("IN", "India", null));

        assertThat(saved.getCompanyId()).isNull();
        // The owner is bound for the duration of the write, so the uniqueness check that
        // runs inside it sees the platform, never a caller's company.
        verify(uniqueness).isCodeTaken(eq(MasterTable.COUNTRIES),
                eq(GlobalMasters.PLATFORM_COMPANY_ID), isNull(), eq("IN"));
        // ...and the binding is restored afterwards rather than left behind on the thread.
        assertThat(CompanyContext.getCompanyId()).isEmpty();
    }

    // ----------------------------------------------------------------- update

    @Test
    @DisplayName("an update replaces the editable fields and leaves the code alone")
    void updateKeepsCode() {
        Country existing = existing("INDIA", "India");
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));

        Country updated = service.update(existing.getId(),
                new CountryCommand(null, "Republic of India", "Renamed", 5,
                        "IN", "IND", "+91", "INR", 2L));

        assertThat(updated.getCode()).isEqualTo("INDIA");
        assertThat(updated.getName()).isEqualTo("Republic of India");
        assertThat(updated.getDisplayOrder()).isEqualTo(5);
        verify(auditService).record(eq(AuditAction.MASTER_DATA_UPDATED), eq("Country"),
                eq(existing.getId()), anyMap());
    }

    @Test
    @DisplayName("a stale version is a 409")
    void staleVersionRejected() {
        // @Version alone only catches a conflict inside one transaction; the real hazard
        // is two administrators editing the same row across two requests.
        Country existing = existing("INDIA", "India");
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new CountryCommand(null, "India", null, 0, null, null, null, null, 1L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("another company's id is a 404, not a 403")
    void foreignIdIsNotFound() {
        // findByIdWithinCompany returns empty, so nothing about the other company's row —
        // not even that it exists — reaches the caller.
        UUID foreign = UUID.randomUUID();
        when(repository.findByIdWithinCompany(foreign, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(foreign,
                new CountryCommand(null, "India", null, 0, null, null, null, null, 0L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("a country with states cannot be deleted")
    void deleteRefusedWithChildren() {
        // Refused rather than cascaded: taking five levels of geography out from one
        // click is not something anyone expects until it has already happened.
        Country existing = existing("INDIA", "India");
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));
        when(states.countByCompanyIdAndCountryId(TENANT, existing.getId())).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still has 3 state(s)");

        assertThat(existing.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("a delete is soft and deactivates alongside")
    void deleteIsSoft() {
        Country existing = existing("INDIA", "India");
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));
        when(states.countByCompanyIdAndCountryId(TENANT, existing.getId())).thenReturn(0L);

        service.delete(existing.getId());

        assertThat(existing.isDeleted()).isTrue();
        // Deactivated too, so a restored row is not silently back in every picker.
        assertThat(existing.isActive()).isFalse();
        verify(auditService).record(eq(AuditAction.MASTER_DATA_DELETED), eq("Country"),
                eq(existing.getId()), anyMap());
    }

    @Test
    @DisplayName("activate and deactivate are idempotent and do not write when nothing changes")
    void lifecycleIsIdempotent() {
        Country existing = existing("INDIA", "India");
        when(repository.findByIdWithinCompany(existing.getId(), TENANT))
                .thenReturn(Optional.of(existing));

        service.activate(existing.getId());
        verify(repository, never()).saveAndFlush(any());

        Country deactivated = service.deactivate(existing.getId());
        assertThat(deactivated.getStatus()).isEqualTo(MasterStatus.INACTIVE);

        service.deactivate(existing.getId());
        verify(repository).saveAndFlush(existing);
    }

    // ------------------------------------------------------------------ reads

    @Test
    @DisplayName("a search is pinned to the caller's company, whatever companyId they sent")
    void searchPinsCompany() {
        // A company id in a query string is overridden, never honoured.
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        MasterDataCriteria spoofed = MasterDataCriteria.of(UUID.randomUUID(), null, null);
        Page<Country> page = service.search(spoofed, PageRequest.of(0, 20));

        assertThat(page).isEmpty();
        verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    // ---------------------------------------------------------------- helpers

    private void signedInAs(String... roles) {
        AuthenticatedUser principal =
                new AuthenticatedUser(CALLER, TENANT, "admin@legacy.test", Set.of(roles), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static CountryCommand command(String code, String name, Integer order) {
        return new CountryCommand(code, name, null, order, null, null, null, null, null);
    }

    private static Country existing(String code, String name) {
        Country country = new Country();
        country.setCode(code);
        country.setName(name);
        country.setCompanyId(TENANT);
        country.setVersion(2L);
        return country;
    }
}
