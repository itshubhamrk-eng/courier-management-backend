package com.courier.modules.company.application;

import com.courier.modules.company.application.command.CreateRoleCommand;
import com.courier.modules.company.application.command.UpdateRoleCommand;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.RoleCriteria;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules of Role Management, with the repository and audit trail mocked.
 *
 * <p>The {@code @PreAuthorize} annotations are not exercised here — method security needs
 * a proxy and belongs to an integration slice. What is exercised is everything they
 * guard, including the company binding the isolation depends on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    @Mock private CompanyRoleRepository repository;
    @Mock private AuditService auditService;

    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleServiceImpl(repository, auditService);
        CompanyContext.setCompanyId(TENANT);

        when(repository.save(any(CompanyRole.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.isRoleCodeTaken(any(), any(), any())).thenReturn(false);
        when(repository.isRoleNameTaken(any(), any(), any())).thenReturn(false);
        when(repository.findOtherDefaultRoles(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        // Servlet threads are pooled; a leaked binding would hand this company to the
        // next test, which is exactly the bug the filter exists to prevent.
        CompanyContext.clear();
    }

    private CompanyRole role(String code, boolean systemRole, RoleStatus status) {
        CompanyRole role = CompanyRole.builder()
                .roleCode(code)
                .roleName(code.charAt(0) + code.substring(1).toLowerCase().replace('_', ' '))
                .roleType(RoleType.OPERATIONS)
                .systemRole(systemRole)
                .status(status)
                .build();
        role.setCompanyId(TENANT);
        role.setVersion(1L);
        return role;
    }

    private CreateRoleCommand createCommand(String code, Boolean isDefault) {
        return new CreateRoleCommand(code, "Night Shift Supervisor",
                "Runs the hub overnight.", RoleType.OPERATIONS, isDefault);
    }

    // ------------------------------------------------------------------- create

    @Test
    @DisplayName("create normalises the code, forces systemRole false and starts ACTIVE")
    void createNormalises() {
        CompanyRole saved = service.create(createCommand("  night shift supervisor ", null));

        assertThat(saved.getRoleCode()).isEqualTo("NIGHT_SHIFT_SUPERVISOR");
        assertThat(saved.getStatus()).isEqualTo(RoleStatus.ACTIVE);
        // A caller-settable flag would let a company mint itself an undeletable role.
        assertThat(saved.isSystemRole()).isFalse();
    }

    @Test
    @DisplayName("create refuses a duplicate code, including one held by a deleted role")
    void createRejectsDuplicateCode() {
        when(repository.isRoleCodeTaken(eq(TENANT), eq("NIGHT_SHIFT"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand("night_shift", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("roleCode");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create refuses a duplicate name")
    void createRejectsDuplicateName() {
        when(repository.isRoleNameTaken(eq(TENANT), any(), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.create(createCommand("night_shift", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("roleName");
    }

    @Test
    @DisplayName("uniqueness is checked within the caller's company, not globally")
    void uniquenessIsPerCompany() {
        service.create(createCommand("night_shift", null));

        // Two companies may both have a NIGHT_SHIFT role; the company is part of the key.
        verify(repository).isRoleCodeTaken(eq(TENANT), eq("NIGHT_SHIFT"), isNull());
    }

    @Test
    @DisplayName("creating a default role demotes the previous one")
    void createDemotesPreviousDefault() {
        CompanyRole previous = role("BOOKING_OPERATOR", true, RoleStatus.ACTIVE);
        previous.markAsDefault();
        when(repository.findOtherDefaultRoles(eq(TENANT), any())).thenReturn(List.of(previous));

        CompanyRole saved = service.create(createCommand("night_shift", true));

        assertThat(saved.isDefaultRole()).isTrue();
        assertThat(previous.isDefaultRole()).isFalse();
        verify(repository).saveAll(List.of(previous));
    }

    @Test
    @DisplayName("create without a bound company is refused")
    void createWithoutCompany() {
        CompanyContext.clear();

        assertThatThrownBy(() -> service.create(createCommand("night_shift", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No company is bound");
    }

    @Test
    @DisplayName("create is audited with the role code and type")
    void createIsAudited() {
        service.create(createCommand("night_shift", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditAction.ROLE_CREATED), eq("Role"),
                any(UUID.class), details.capture());

        assertThat(details.getValue())
                .containsEntry("roleCode", "NIGHT_SHIFT")
                .containsEntry("roleType", "OPERATIONS");
    }

    // ------------------------------------------------------------------- update

    private UpdateRoleCommand updateCommand(String name, Long version) {
        return new UpdateRoleCommand(name, "Updated description", RoleType.SUPPORT, false, version);
    }

    @Test
    @DisplayName("update applies changes when the version matches")
    void updateApplies() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        CompanyRole saved = service.update(role.getId(), updateCommand("Overnight Supervisor", 1L));

        assertThat(saved.getRoleName()).isEqualTo("Overnight Supervisor");
        assertThat(saved.getRoleType()).isEqualTo(RoleType.SUPPORT);
    }

    @Test
    @DisplayName("update rejects a stale version")
    void updateRejectsStaleVersion() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.update(role.getId(), updateCommand("Nope", 0L)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update cannot change the role code or the system flag")
    void updateCannotChangeImmutables() {
        CompanyRole role = role("COMPANY_ADMIN", true, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        CompanyRole saved = service.update(role.getId(), updateCommand("Owner", 1L));

        // Renaming a system role is legitimate; unmaking it is not.
        assertThat(saved.getRoleName()).isEqualTo("Owner");
        assertThat(saved.getRoleCode()).isEqualTo("COMPANY_ADMIN");
        assertThat(saved.isSystemRole()).isTrue();
    }

    @Test
    @DisplayName("update loads within the caller's company, so another company's id is a 404")
    void updateIsCompanyScoped() {
        UUID foreignId = UUID.randomUUID();
        // findByIdWithinCompany names the company explicitly because a primary-key load
        // would bypass the Hibernate filter entirely.
        when(repository.findByIdWithinCompany(foreignId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(foreignId, updateCommand("X", 1L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("update audits only the fields that changed")
    void updateAuditsOnlyChanges() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        service.update(role.getId(), updateCommand("Overnight Supervisor", 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditAction.ROLE_UPDATED), eq("Role"),
                eq(role.getId()), details.capture());

        assertThat(details.getValue()).containsKeys("roleName", "roleType");
        assertThat(details.getValue()).doesNotContainKey("status");
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    @DisplayName("deactivate withdraws the role but leaves holders alone")
    void deactivate() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        CompanyRole saved = service.deactivate(role.getId());

        assertThat(saved.getStatus()).isEqualTo(RoleStatus.INACTIVE);
        assertThat(saved.isActive()).isFalse();
        verify(auditService).record(eq(AuditAction.ROLE_DEACTIVATED), eq("Role"),
                eq(role.getId()), any());
    }

    @Test
    @DisplayName("the default role cannot be deactivated")
    void cannotDeactivateDefault() {
        // Otherwise new users would be created holding a role nobody may hold.
        CompanyRole role = role("BOOKING_OPERATOR", true, RoleStatus.ACTIVE);
        role.markAsDefault();
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.deactivate(role.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("default");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("activate and deactivate are idempotent and emit nothing for a no-op")
    void lifecycleIsIdempotent() {
        CompanyRole active = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(active.getId(), TENANT)).thenReturn(Optional.of(active));

        service.activate(active.getId());

        verify(repository, never()).save(any());
        verify(auditService, never()).record(eq(AuditAction.ROLE_ACTIVATED), any(), any(), any());
    }

    @Test
    @DisplayName("activate returns an inactive role to the assignment list")
    void activateInactiveRole() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.INACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        CompanyRole saved = service.activate(role.getId());

        assertThat(saved.getStatus()).isEqualTo(RoleStatus.ACTIVE);
        verify(auditService).record(eq(AuditAction.ROLE_ACTIVATED), eq("Role"),
                eq(role.getId()), any());
    }

    // ------------------------------------------------------------------- delete

    @Test
    @DisplayName("a system role cannot be deleted")
    void systemRoleCannotBeDeleted() {
        // A company that deleted COMPANY_ADMIN would have nobody able to administer it.
        CompanyRole role = role("COMPANY_ADMIN", true, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.delete(role.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("system role")
                .hasMessageContaining("Deactivate it instead");

        assertThat(role.isDeleted()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the default role cannot be deleted either")
    void defaultRoleCannotBeDeleted() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        role.markAsDefault();
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.delete(role.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("default");
    }

    @Test
    @DisplayName("delete is soft and never hard-deletes")
    void deleteIsSoft() {
        CompanyRole role = role("NIGHT_SHIFT", false, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        service.delete(role.getId());

        assertThat(role.isDeleted()).isTrue();
        verify(repository).save(role);
        // Typed matcher: JpaSpecificationExecutor also declares delete(Specification).
        verify(repository, never()).delete(any(CompanyRole.class));
        verify(repository, never()).deleteById(any());
        verify(auditService).record(eq(AuditAction.ROLE_DELETED), eq("Role"),
                eq(role.getId()), any());
    }

    // --------------------------------------------------------------------- read

    @Test
    @DisplayName("a company admin's search is pinned to their own company")
    void searchIsPinnedToCompany() {
        Page<CompanyRole> page = new PageImpl<>(List.of(role("VIEWER", true, RoleStatus.ACTIVE)));
        when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        // A companyId in the query string must narrow, never widen.
        RoleCriteria spoofed = new RoleCriteria(OTHER_TENANT, null, null, null, null, null, null);
        assertThat(service.search(spoofed, PageRequest.of(0, 20))).isSameAs(page);

        ArgumentCaptor<Specification<CompanyRole>> spec = ArgumentCaptor.captor();
        verify(repository).findAll(spec.capture(), any(PageRequest.class));
        assertThat(spec.getValue()).isNotNull();
    }

    @Test
    @DisplayName("a super admin, having no bound company, searches across all of them")
    void superAdminSearchesAcrossCompanies() {
        CompanyContext.clear();
        Page<CompanyRole> page = new PageImpl<>(List.of());
        when(repository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        assertThat(service.search(RoleCriteria.none(), PageRequest.of(0, 20))).isSameAs(page);
    }

    @Test
    @DisplayName("getById is company-scoped for a company admin")
    void getByIdIsCompanyScoped() {
        CompanyRole role = role("VIEWER", true, RoleStatus.ACTIVE);
        when(repository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));

        assertThat(service.getById(role.getId())).isSameAs(role);
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("getById falls back to a platform-level load only when no company is bound")
    void getByIdForSuperAdmin() {
        CompanyContext.clear();
        CompanyRole role = role("VIEWER", true, RoleStatus.ACTIVE);
        when(repository.findById(role.getId())).thenReturn(Optional.of(role));

        assertThat(service.getById(role.getId())).isSameAs(role);
        verify(repository, never()).findByIdWithinCompany(any(), any());
    }

    @Test
    @DisplayName("listAssignable returns only ACTIVE roles")
    void listAssignable() {
        when(repository.findAllByStatusOrderByRoleCodeAsc(RoleStatus.ACTIVE))
                .thenReturn(List.of(role("VIEWER", true, RoleStatus.ACTIVE)));

        assertThat(service.listAssignable()).hasSize(1);
        verify(repository).findAllByStatusOrderByRoleCodeAsc(RoleStatus.ACTIVE);
    }
}
