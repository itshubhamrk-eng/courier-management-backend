package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySetting;
import com.courier.modules.company.domain.CompanySettingKeys;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.DefaultRoleCatalog;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The default branch-manager role that comes with a branch.
 *
 * <p>What is actually being asserted is that the two halves are <b>idempotent</b>. Branch
 * creation is not a once-per-company event: a courier opens branches for years, and each
 * one runs this. A version that created a role every time would put one row per office in
 * {@code company_roles}, and one that granted every time would fail on the second branch
 * against {@code uk_user_company_roles_user_role}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchRoleProvisioningServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    @Mock private CompanyRoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CompanySettingRepository settingRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private AuditService auditService;

    private BranchRoleProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new BranchRoleProvisioningService(roleRepository, permissionRepository,
                rolePermissionRepository, settingRepository, userRoleRepository, auditService);

        when(roleRepository.save(any(CompanyRole.class))).thenAnswer(invocation -> {
            CompanyRole role = invocation.getArgument(0);
            if (role.getId() == null) {
                role.setId(ROLE_ID);
            }
            role.setCompanyId(COMPANY);
            return role;
        });
        when(permissionRepository.findAllByPermissionCodeIn(anyCollection())).thenReturn(List.of());
        when(settingRepository.findAllByCategoryOrderBySettingKeyAsc(
                CompanySettingKeys.CATEGORY_FEATURES)).thenReturn(List.of());
        when(userRoleRepository.existsByUserIdAndRoleId(any(), any())).thenReturn(false);
    }

    private CompanyRole seeded(RoleStatus status) {
        CompanyRole role = CompanyRole.builder()
                .roleCode(DefaultRoleCatalog.BRANCH_MANAGER)
                .roleName("Branch Manager")
                .systemRole(true)
                .status(status)
                .build();
        role.setId(ROLE_ID);
        role.setCompanyId(COMPANY);
        return role;
    }

    @Test
    @DisplayName("the company's existing role is reused, not duplicated")
    void reusesExistingRole() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.of(seeded(RoleStatus.ACTIVE)));

        var result = service.ensureBranchManagerRole(COMPANY, USER);

        assertThat(result.roleCreated()).isFalse();
        assertThat(result.role().getId()).isEqualTo(ROLE_ID);
        verify(roleRepository, never()).save(any(CompanyRole.class));
    }

    @Test
    @DisplayName("a company with no BRANCH_MANAGER role gets one from the catalogue")
    void createsMissingRoleFromCatalogue() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.empty());

        var result = service.ensureBranchManagerRole(COMPANY, USER);

        assertThat(result.roleCreated()).isTrue();
        ArgumentCaptor<CompanyRole> saved = ArgumentCaptor.forClass(CompanyRole.class);
        verify(roleRepository).save(saved.capture());
        assertThat(saved.getValue().getRoleCode()).isEqualTo(DefaultRoleCatalog.BRANCH_MANAGER);
        // Seeded, so the company may re-permission it but never delete it — the same
        // footing the role has when company provisioning creates it.
        assertThat(saved.getValue().isSystemRole()).isTrue();
        assertThat(saved.getValue().isDefaultRole()).isFalse();
        assertThat(saved.getValue().getStatus()).isEqualTo(RoleStatus.ACTIVE);
        verify(auditService).record(eqAction(AuditAction.ROLE_CREATED), any(), any(), any());
    }

    @Test
    @DisplayName("the created role's permissions are filtered by the company's plan features")
    void createdRoleRespectsPlanGating() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.empty());
        // BULK_BOOKING off — SHIPMENT_IMPORT, which the catalogue gives a branch manager,
        // must not survive. Reading the seeded feature.* rows rather than the plan is what
        // keeps this answer identical to the one the roles screen would give.
        when(settingRepository.findAllByCategoryOrderBySettingKeyAsc(
                CompanySettingKeys.CATEGORY_FEATURES))
                .thenReturn(List.of(feature("BULK_BOOKING", "false")));
        Permission shipmentImport = Permission.builder()
                .permissionCode("SHIPMENT_IMPORT")
                .module(PermissionModule.SHIPMENT)
                .action(PermissionAction.IMPORT)
                .build();
        shipmentImport.setId(UUID.randomUUID());
        when(permissionRepository.findAllByPermissionCodeIn(anyCollection()))
                .thenAnswer(invocation -> {
                    java.util.Collection<String> codes = invocation.getArgument(0);
                    return codes.contains("SHIPMENT_IMPORT") ? List.of(shipmentImport) : List.<Permission>of();
                });

        service.ensureBranchManagerRole(COMPANY, USER);

        ArgumentCaptor<java.util.Collection<String>> codes =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(permissionRepository).findAllByPermissionCodeIn(codes.capture());
        assertThat(codes.getValue()).doesNotContain("SHIPMENT_IMPORT");
        assertThat(codes.getValue()).contains("BRANCH_READ", "SHIPMENT_CREATE");
    }

    @Test
    @DisplayName("a deactivated role is reactivated rather than left granting nothing")
    void reactivatesWithdrawnRole() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.of(seeded(RoleStatus.INACTIVE)));

        var result = service.ensureBranchManagerRole(COMPANY, USER);

        assertThat(result.roleCreated()).isFalse();
        assertThat(result.role().getStatus()).isEqualTo(RoleStatus.ACTIVE);
    }

    @Test
    @DisplayName("the grant is written once and audited")
    void grantsTheRole() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.of(seeded(RoleStatus.ACTIVE)));

        var result = service.ensureBranchManagerRole(COMPANY, USER);

        assertThat(result.granted()).isTrue();
        ArgumentCaptor<UserRole> grant = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(grant.capture());
        assertThat(grant.getValue().getUserId()).isEqualTo(USER);
        assertThat(grant.getValue().getRoleId()).isEqualTo(ROLE_ID);
        assertThat(grant.getValue().getRoleCode()).isEqualTo(DefaultRoleCatalog.BRANCH_MANAGER);
        verify(auditService).record(eqAction(AuditAction.USER_ROLE_ASSIGNED), any(), any(), any());
    }

    @Test
    @DisplayName("a user who already holds the role is not granted it twice")
    void grantIsIdempotent() {
        when(roleRepository.findByRoleCode(DefaultRoleCatalog.BRANCH_MANAGER))
                .thenReturn(Optional.of(seeded(RoleStatus.ACTIVE)));
        when(userRoleRepository.existsByUserIdAndRoleId(USER, ROLE_ID)).thenReturn(true);

        var result = service.ensureBranchManagerRole(COMPANY, USER);

        assertThat(result.granted()).isFalse();
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    @DisplayName("the catalogue still defines BRANCH_MANAGER — branch creation depends on it")
    void catalogueStillCarriesTheRole() {
        assertThat(DefaultRoleCatalog.systemRoleCodes())
                .contains(DefaultRoleCatalog.BRANCH_MANAGER);
    }

    private static CompanySetting feature(String flag, String value) {
        return CompanySetting.builder()
                .settingKey(CompanySettingKeys.FEATURE_PREFIX + flag)
                .settingValue(value)
                .category(CompanySettingKeys.CATEGORY_FEATURES)
                .planDerived(true)
                .build();
    }

    private static AuditAction eqAction(AuditAction action) {
        return org.mockito.ArgumentMatchers.eq(action);
    }

    /** Guards the assumption the whole class rests on. */
    @Test
    @DisplayName("the catalogue's branch-manager definition is not empty")
    void catalogueDefinitionHasPermissions() {
        Set<String> permissions = DefaultRoleCatalog.definitions().stream()
                .filter(d -> d.code().equals(DefaultRoleCatalog.BRANCH_MANAGER))
                .findFirst().orElseThrow().permissions();
        assertThat(permissions).isNotEmpty();
    }
}
