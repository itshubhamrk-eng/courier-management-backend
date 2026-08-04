package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.CompanySetting;
import com.courier.modules.company.domain.CompanySettingKeys;
import com.courier.modules.company.domain.CompanySettingRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.PermissionStatus;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Granting rules, with repositories and the audit trail mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolePermissionServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private CompanyRoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private CompanySettingRepository settingRepository;
    @Mock private AuditService auditService;

    private RolePermissionServiceImpl service;
    private CompanyRole role;
    private Permission shipmentCreate;
    private Permission shipmentImport;
    private Permission roleUpdate;

    @BeforeEach
    void setUp() {
        service = new RolePermissionServiceImpl(roleRepository, permissionRepository,
                rolePermissionRepository, settingRepository, auditService);
        CompanyContext.setCompanyId(TENANT);

        role = CompanyRole.builder()
                .roleCode("BOOKING_OPERATOR")
                .roleName("Booking Operator")
                .roleType(RoleType.OPERATIONS)
                .status(RoleStatus.ACTIVE)
                .build();
        role.setCompanyId(TENANT);

        shipmentCreate = permission(PermissionModule.SHIPMENT, PermissionAction.CREATE, null);
        shipmentImport = permission(PermissionModule.SHIPMENT, PermissionAction.IMPORT, "bulkBooking");
        roleUpdate = permission(PermissionModule.ROLE, PermissionAction.UPDATE, null);

        when(roleRepository.findByIdWithinCompany(role.getId(), TENANT)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(any()))
                .thenReturn(List.of());
        when(rolePermissionRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(settingRepository.findAllByCategoryOrderBySettingKeyAsc(any())).thenReturn(List.of());

        // A role holding ROLE_UPDATE exists, so the lockout guard passes by default.
        when(permissionRepository.findByPermissionCode("ROLE_UPDATE"))
                .thenReturn(Optional.of(roleUpdate));
        when(rolePermissionRepository.findRoleIdsByPermissionId(roleUpdate.getId()))
                .thenReturn(List.of(role.getId()));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    private Permission permission(PermissionModule module, PermissionAction action, String flag) {
        Permission permission = Permission.builder()
                .module(module)
                .action(action)
                .status(PermissionStatus.ACTIVE)
                .requiredFeatureFlag(flag)
                .build();
        permission.applyInvariants();
        return permission;
    }

    private void planEnables(String flag, boolean enabled) {
        CompanySetting setting = CompanySetting.builder()
                .settingKey(CompanySettingKeys.FEATURE_PREFIX + flag)
                .settingValue(String.valueOf(enabled))
                .category(CompanySettingKeys.CATEGORY_FEATURES)
                .planDerived(true)
                .build();
        when(settingRepository.findAllByCategoryOrderBySettingKeyAsc(
                CompanySettingKeys.CATEGORY_FEATURES)).thenReturn(List.of(setting));
    }

    @Test
    @DisplayName("assign grants the requested permissions and records the codes")
    void assignGrants() {
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("shipment_create"), false);

        // Codes are normalised, so a lowercase request still matches.
        assertThat(result.granted()).containsExactly("SHIPMENT_CREATE");
        assertThat(result.rejected()).isEmpty();
        verify(auditService).record(eq(AuditAction.ROLE_PERMISSIONS_ASSIGNED), eq("RolePermission"),
                eq(role.getId()), any());
    }

    @Test
    @DisplayName("a permission the plan does not include is rejected, not granted")
    void planGatedPermissionIsRejected() {
        planEnables("bulkBooking", false);
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentImport));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_IMPORT"), false);

        assertThat(result.rejected()).containsExactly("SHIPMENT_IMPORT");
        assertThat(result.granted()).isEmpty();
    }

    @Test
    @DisplayName("the same permission is granted once the plan includes it")
    void planGatedPermissionIsAllowedWhenEnabled() {
        planEnables("bulkBooking", true);
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentImport));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_IMPORT"), false);

        assertThat(result.granted()).containsExactly("SHIPMENT_IMPORT");
        assertThat(result.rejected()).isEmpty();
    }

    @Test
    @DisplayName("a deactivated permission cannot be granted")
    void inactivePermissionIsRejected() {
        shipmentCreate.deactivate();
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_CREATE"), false);

        assertThat(result.rejected()).containsExactly("SHIPMENT_CREATE");
    }

    @Test
    @DisplayName("a permission the role already holds is skipped, not duplicated")
    void alreadyHeldIsSkipped() {
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));
        when(rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(role.getId()))
                .thenReturn(List.of(RolePermission.grant(role.getId(), shipmentCreate)));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_CREATE"), false);

        assertThat(result.skipped()).containsExactly("SHIPMENT_CREATE");
        assertThat(result.granted()).isEmpty();
    }

    @Test
    @DisplayName("replaceExisting revokes what was not requested")
    void replaceRevokesSurplus() {
        RolePermission held = RolePermission.grant(role.getId(), shipmentImport);
        when(rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(role.getId()))
                .thenReturn(new ArrayList<>(List.of(held)));
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_CREATE"), true);

        assertThat(result.granted()).containsExactly("SHIPMENT_CREATE");
        assertThat(result.revoked()).containsExactly("SHIPMENT_IMPORT");
        assertThat(held.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("an unknown permission code is a 404, not a silent no-op")
    void unknownCodeIsRejected() {
        when(permissionRepository.findAllByPermissionCodeIn(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.assign(role.getId(), List.of("NOT_A_PERMISSION"), false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NOT_A_PERMISSION");
    }

    @Test
    @DisplayName("a change that takes away the last ROLE_UPDATE is refused")
    void lockoutIsPrevented() {
        // Held before the change, gone after: the company could never fix its own
        // permissions again, and only support could rescue it.
        when(rolePermissionRepository.findRoleIdsByPermissionId(roleUpdate.getId()))
                .thenReturn(List.of(role.getId()), List.of());
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        assertThatThrownBy(() -> service.assign(role.getId(), List.of("SHIPMENT_CREATE"), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ROLE_UPDATE");
    }

    @Test
    @DisplayName("a company that never had ROLE_UPDATE can still be granted permissions")
    void guardDoesNotBlockACompanyThatAlreadyLacksIt() {
        // Guarding on the after-state alone would refuse every grant in exactly the
        // situation that needs fixing — including granting ROLE_UPDATE itself.
        when(rolePermissionRepository.findRoleIdsByPermissionId(roleUpdate.getId()))
                .thenReturn(List.of());
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        RolePermissionService.GrantResult result =
                service.assign(role.getId(), List.of("SHIPMENT_CREATE"), false);

        assertThat(result.granted()).containsExactly("SHIPMENT_CREATE");
    }

    @Test
    @DisplayName("an inactive role holding ROLE_UPDATE does not count as administrable")
    void inactiveHolderDoesNotSatisfyTheGuard() {
        CompanyRole retired = CompanyRole.builder()
                .roleCode("OLD_ADMIN").roleName("Old Admin")
                .roleType(RoleType.ADMINISTRATION).status(RoleStatus.INACTIVE).build();
        retired.setCompanyId(TENANT);

        // Active holder before, only the retired one after.
        when(rolePermissionRepository.findRoleIdsByPermissionId(roleUpdate.getId()))
                .thenReturn(List.of(role.getId()), List.of(retired.getId()));
        when(roleRepository.findByIdWithinCompany(retired.getId(), TENANT))
                .thenReturn(Optional.of(retired));
        when(permissionRepository.findAllByPermissionCodeIn(any()))
                .thenReturn(List.of(shipmentCreate));

        assertThatThrownBy(() -> service.assign(role.getId(), List.of("SHIPMENT_CREATE"), true))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("assign refuses to touch another company's role")
    void assignIsCompanyScoped() {
        UUID foreignRole = UUID.randomUUID();
        when(roleRepository.findByIdWithinCompany(foreignRole, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(foreignRole, List.of("SHIPMENT_CREATE"), false))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(rolePermissionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("assign without a bound company is refused")
    void assignWithoutCompany() {
        CompanyContext.clear();

        assertThatThrownBy(() -> service.assign(role.getId(), List.of("SHIPMENT_CREATE"), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No company is bound");
    }

    @Test
    @DisplayName("revoking a permission the role does not hold is a no-op, not an error")
    void revokeIsIdempotent() {
        when(rolePermissionRepository.findByRoleIdAndPermissionId(any(), any()))
                .thenReturn(Optional.empty());

        service.revoke(role.getId(), shipmentCreate.getId());

        verify(rolePermissionRepository, never()).save(any());
        verify(auditService, never()).record(eq(AuditAction.ROLE_PERMISSION_REVOKED),
                any(), any(), any());
    }

    @Test
    @DisplayName("revoke soft deletes the grant and audits it")
    void revoke() {
        RolePermission grant = RolePermission.grant(role.getId(), shipmentCreate);
        when(rolePermissionRepository.findByRoleIdAndPermissionId(role.getId(),
                shipmentCreate.getId())).thenReturn(Optional.of(grant));

        service.revoke(role.getId(), shipmentCreate.getId());

        assertThat(grant.isDeleted()).isTrue();
        verify(rolePermissionRepository).save(grant);
        verify(auditService).record(eq(AuditAction.ROLE_PERMISSION_REVOKED), eq("RolePermission"),
                eq(role.getId()), any());
    }

    @Test
    @DisplayName("effective codes across roles are distinct and sorted")
    void resolveEffectiveCodes() {
        UUID second = UUID.randomUUID();
        when(rolePermissionRepository.findAllByRoleIdIn(any())).thenReturn(List.of(
                RolePermission.grant(role.getId(), shipmentImport),
                RolePermission.grant(role.getId(), shipmentCreate),
                RolePermission.grant(second, shipmentCreate)));

        assertThat(service.resolveEffectiveCodes(List.of(role.getId(), second)))
                .containsExactly("SHIPMENT_CREATE", "SHIPMENT_IMPORT");
    }

    @Test
    @DisplayName("counts are batched per role for a list page")
    void countByRoles() {
        UUID second = UUID.randomUUID();
        when(rolePermissionRepository.findAllByRoleIdIn(any())).thenReturn(List.of(
                RolePermission.grant(role.getId(), shipmentCreate),
                RolePermission.grant(role.getId(), shipmentImport),
                RolePermission.grant(second, shipmentCreate)));

        assertThat(service.countByRoles(List.of(role.getId(), second)))
                .containsEntry(role.getId(), 2)
                .containsEntry(second, 1);
        assertThat(service.countByRoles(List.of())).isEmpty();
    }
}
