package com.courier.modules.company.application;

import com.courier.modules.company.domain.CompanyUserRepository;
import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.PermissionStatus;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.User;
import com.courier.modules.company.domain.UserPermissionOverride;
import com.courier.modules.company.domain.UserPermissionOverrideRepository;
import com.courier.modules.company.domain.UserRole;
import com.courier.modules.company.domain.UserRoleRepository;
import com.courier.modules.company.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Effective-permission resolution and the diff-only override persistence, with
 * repositories and the audit trail mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPermissionServiceImplTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    @Mock private CompanyUserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private AuditService auditService;

    private UserPermissionServiceImpl service;
    private User targetUser;
    private Permission shipmentCreate;
    private Permission shipmentRead;
    private MenuItem bookingLeaf;

    @BeforeEach
    void setUp() {
        service = new UserPermissionServiceImpl(userRepository, userRoleRepository,
                rolePermissionRepository, permissionRepository, overrideRepository,
                menuItemRepository, auditService);
        CompanyContext.setCompanyId(TENANT);

        targetUser = User.builder()
                .email("clerk@acme.test").username("clerk").employeeCode("EMP01")
                .firstName("Ops").lastName("Clerk").passwordHash("h").status(UserStatus.ACTIVE)
                .build();
        targetUser.setId(USER_ID);
        targetUser.setCompanyId(TENANT);
        when(userRepository.findByIdWithinCompany(USER_ID, TENANT)).thenReturn(Optional.of(targetUser));

        shipmentCreate = permission(PermissionModule.SHIPMENT, PermissionAction.CREATE);
        shipmentRead = permission(PermissionModule.SHIPMENT, PermissionAction.READ);
        when(permissionRepository.findAllByModuleOrderByDisplayOrderAsc(PermissionModule.SHIPMENT))
                .thenReturn(List.of(shipmentCreate, shipmentRead));

        UserRole assignment = UserRole.builder().userId(USER_ID).roleId(ROLE_ID).roleCode("BOOKING_OPERATOR").build();
        when(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(USER_ID)).thenReturn(List.of(assignment));
        // Role default: SHIPMENT_CREATE only, not SHIPMENT_READ.
        RolePermission grant = RolePermission.grant(ROLE_ID, shipmentCreate);
        when(rolePermissionRepository.findAllByRoleIdIn(List.of(ROLE_ID))).thenReturn(List.of(grant));

        when(overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(USER_ID)).thenReturn(List.of());
        when(overrideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        bookingLeaf = MenuItem.builder().code("booking").title("Shipment Booking")
                .permissionModule(PermissionModule.SHIPMENT).displayOrder(1).active(true).build();
        UUID leafId = UUID.randomUUID();
        bookingLeaf.setId(leafId);
        when(menuItemRepository.findAllById(any())).thenAnswer(i -> {
            List<UUID> ids = i.getArgument(0);
            return ids != null && ids.contains(leafId) ? List.of(bookingLeaf) : List.of();
        });
        when(menuItemRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(bookingLeaf));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Permission permission(PermissionModule module, PermissionAction action) {
        Permission p = Permission.builder()
                .permissionCode(module.name() + "_" + action.name())
                .permissionName(action + " " + module).module(module).resource("x").action(action)
                .status(PermissionStatus.ACTIVE).displayOrder(1).build();
        p.setId(UUID.randomUUID());
        return p;
    }

    private void plantedCompanyAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, TENANT, "admin@acme.test", Set.of(Roles.COMPANY_ADMIN), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private void plantedBranchManager(UUID ownBranchId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                CALLER, TENANT, "manager@acme.test", Set.of(Roles.BRANCH_MANAGER), "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        User self = User.builder().email("manager@acme.test").username("mgr").employeeCode("EMP02")
                .firstName("B").lastName("M").passwordHash("h").status(UserStatus.ACTIVE).build();
        self.setId(CALLER);
        self.setCompanyId(TENANT);
        self.setBranchId(ownBranchId);
        when(userRepository.findByIdWithinCompany(CALLER, TENANT)).thenReturn(Optional.of(self));
    }

    // ------------------------------------------------------- resolveEffectivePermissionCodes

    @Test
    @DisplayName("with no overrides, effective codes are exactly the role's own")
    void effectiveCodesDefaultToRole() {
        assertThat(service.resolveEffectivePermissionCodes(USER_ID)).containsExactly("SHIPMENT_CREATE");
    }

    @Test
    @DisplayName("a granted override adds a right the role does not have")
    void grantedOverrideAdds() {
        when(overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(USER_ID)).thenReturn(
                List.of(override(shipmentRead, true)));

        assertThat(service.resolveEffectivePermissionCodes(USER_ID))
                .containsExactlyInAnyOrder("SHIPMENT_CREATE", "SHIPMENT_READ");
    }

    @Test
    @DisplayName("a revoked override removes a right the role does have")
    void revokedOverrideRemoves() {
        when(overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(USER_ID)).thenReturn(
                List.of(override(shipmentCreate, false)));

        assertThat(service.resolveEffectivePermissionCodes(USER_ID)).isEmpty();
    }

    private UserPermissionOverride override(Permission permission, boolean granted) {
        return UserPermissionOverride.of(USER_ID, permission, granted);
    }

    // ------------------------------------------------------------------- updateUserPermissions

    @Test
    @DisplayName("granting a right the role lacks creates exactly one override row")
    void updateCreatesOverrideForExtraGrant() {
        plantedCompanyAdmin();

        var result = service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(bookingLeaf.getId(), true, true, false, false)));

        assertThat(result.granted()).containsExactly("SHIPMENT_READ");
        assertThat(result.revoked()).isEmpty();
    }

    @Test
    @DisplayName("revoking a right the role grants creates exactly one override row")
    void updateCreatesOverrideForRevoke() {
        plantedCompanyAdmin();

        var result = service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(bookingLeaf.getId(), false, false, false, false)));

        assertThat(result.revoked()).containsExactly("SHIPMENT_CREATE");
        assertThat(result.granted()).isEmpty();
    }

    @Test
    @DisplayName("requesting exactly the role default leaves no override, and removes a stale one")
    void updateAtRoleDefaultLeavesNoOverride() {
        plantedCompanyAdmin();
        UserPermissionOverride stale = override(shipmentRead, true);
        stale.setId(UUID.randomUUID());
        when(overrideRepository.findAllByUserIdOrderByPermissionCodeAsc(USER_ID)).thenReturn(List.of(stale));

        // Back to exactly the role default: SHIPMENT_CREATE granted (matches role), READ not (matches role).
        var result = service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(bookingLeaf.getId(), true, false, false, false)));

        assertThat(result.granted()).isEmpty();
        // The stale READ override is reverted (READ goes from effective-true to effective-false).
        assertThat(result.revoked()).containsExactly("SHIPMENT_READ");
        assertThat(stale.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("a group menu item id is ignored rather than rejected")
    void updateIgnoresGroupNode() {
        plantedCompanyAdmin();
        MenuItem group = MenuItem.builder().code("shipment-group").title("Shipment Management")
                .permissionModule(null).displayOrder(0).active(true).build();
        UUID groupId = UUID.randomUUID();
        group.setId(groupId);
        when(menuItemRepository.findAllById(any())).thenReturn(List.of(group));

        var result = service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(groupId, true, true, true, true)));

        assertThat(result.granted()).isEmpty();
        assertThat(result.revoked()).isEmpty();
    }

    @Test
    @DisplayName("a branch manager may update their own branch's user")
    void branchManagerUpdatesOwnBranchUser() {
        UUID branchId = UUID.randomUUID();
        targetUser.setBranchId(branchId);
        plantedBranchManager(branchId);

        var result = service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(bookingLeaf.getId(), true, true, false, false)));

        assertThat(result.granted()).containsExactly("SHIPMENT_READ");
    }

    @Test
    @DisplayName("a branch manager may not update a user of another branch")
    void branchManagerRefusedForOtherBranch() {
        targetUser.setBranchId(UUID.randomUUID());
        plantedBranchManager(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateUserPermissions(USER_ID, List.of(
                new UserPermissionService.MenuPermissionUpdateItem(bookingLeaf.getId(), true, true, false, false))))
                .isInstanceOf(ForbiddenException.class);
    }

    // --------------------------------------------------------------------- getMenuPermissions

    @Test
    @DisplayName("the annotated tree marks the role default and only the actions the module has")
    void menuTreeAnnotatesRoleDefaultAndAvailability() {
        plantedCompanyAdmin();

        UserPermissionService.MenuPermissionNode root = service.getMenuPermissions(USER_ID);
        UserPermissionService.MenuPermissionNode leaf = root.children().get(0);

        assertThat(leaf.roleDefault()).containsEntry(PermissionAction.CREATE, true);
        assertThat(leaf.roleDefault()).containsEntry(PermissionAction.READ, false);
        // UPDATE/DELETE were never seeded for this module in the mock, so they are absent
        // rather than false — "not offered", not "denied".
        assertThat(leaf.roleDefault()).doesNotContainKey(PermissionAction.UPDATE);
        assertThat(leaf.effective()).isEqualTo(leaf.roleDefault());
        assertThat(leaf.overridden().values()).allMatch(v -> !v);
    }
}
