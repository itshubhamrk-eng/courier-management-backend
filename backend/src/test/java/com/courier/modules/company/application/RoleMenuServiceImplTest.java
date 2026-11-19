package com.courier.modules.company.application;

import com.courier.modules.company.application.command.RoleMenuItem;
import com.courier.modules.company.domain.CompanyRole;
import com.courier.modules.company.domain.CompanyRoleRepository;
import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.modules.company.domain.Permission;
import com.courier.modules.company.domain.PermissionAction;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.modules.company.domain.PermissionRepository;
import com.courier.modules.company.domain.PermissionStatus;
import com.courier.modules.company.domain.RoleMenu;
import com.courier.modules.company.domain.RoleMenuRepository;
import com.courier.modules.company.domain.RolePermission;
import com.courier.modules.company.domain.RolePermissionRepository;
import com.courier.modules.company.domain.RoleStatus;
import com.courier.modules.company.domain.RoleType;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleMenuServiceImplTest {

    private static final UUID COMPANY = UUID.randomUUID();

    @Mock private CompanyRoleRepository roleRepository;
    @Mock private RoleMenuRepository roleMenuRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private RolePermissionService rolePermissionService;

    private RoleMenuServiceImpl service;
    private CompanyRole role;
    private MenuItem tickets;
    private MenuItem communication;
    private MenuItem group;
    private Permission notifRead;
    private Permission notifCreate;
    private Permission shipmentRead;
    private final List<RoleMenu> stored = new ArrayList<>();
    private final List<RolePermission> held = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new RoleMenuServiceImpl(roleRepository, roleMenuRepository, menuItemRepository,
                permissionRepository, rolePermissionRepository, rolePermissionService);
        CompanyContext.setCompanyId(COMPANY);

        role = CompanyRole.builder().roleCode("CRM").roleName("CRM")
                .roleType(RoleType.OPERATIONS).status(RoleStatus.ACTIVE).build();
        role.setCompanyId(COMPANY);
        when(roleRepository.findByIdWithinCompany(role.getId(), COMPANY)).thenReturn(Optional.of(role));

        tickets = menu("support-tickets", PermissionModule.NOTIFICATION);
        communication = menu("communication-logs", PermissionModule.NOTIFICATION);
        group = menu("support", null);
        when(menuItemRepository.findAllById(any())).thenAnswer(i -> {
            Collection<UUID> ids = i.getArgument(0);
            return List.of(tickets, communication, group).stream()
                    .filter(m -> ids.contains(m.getId())).toList();
        });

        notifRead = permission(PermissionModule.NOTIFICATION, PermissionAction.READ);
        notifCreate = permission(PermissionModule.NOTIFICATION, PermissionAction.CREATE);
        shipmentRead = permission(PermissionModule.SHIPMENT, PermissionAction.READ);
        when(permissionRepository.findAllByPermissionCodeIn(any())).thenAnswer(i -> {
            Collection<String> codes = i.getArgument(0);
            return List.of(notifRead, notifCreate, shipmentRead).stream()
                    .filter(p -> codes.contains(p.getPermissionCode())).toList();
        });

        when(roleMenuRepository.findAllByRoleId(role.getId())).thenAnswer(i -> List.copyOf(stored));
        when(roleMenuRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(rolePermissionRepository.findAllByRoleIdOrderByPermissionCodeAsc(role.getId()))
                .thenAnswer(i -> List.copyOf(held));
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void replace_storesOneRowPerTickedMenu_andDropsUnticked() {
        RoleMenu old = stored(communication, true, false, false, false);

        service.replace(role.getId(), List.of(item(tickets, true, false, false, false)));

        ArgumentCaptor<List<RoleMenu>> saved = ArgumentCaptor.forClass(List.class);
        verify(roleMenuRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(RoleMenu::getMenuItemId)
                .contains(tickets.getId()).doesNotContain(group.getId());
        assertThat(old.isDeleted()).isTrue();
    }

    @Test
    void replace_treatsAnAllFalseItemAsUnticked() {
        service.replace(role.getId(), List.of(item(tickets, false, false, false, false)));

        ArgumentCaptor<List<RoleMenu>> saved = ArgumentCaptor.forClass(List.class);
        verify(roleMenuRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).isEmpty();
    }

    @Test
    void replace_grantsModulePermissionsForTickedActions() {
        service.replace(role.getId(), List.of(item(tickets, true, true, false, false)));

        assertThat(finalCodes()).contains("NOTIFICATION_READ", "NOTIFICATION_CREATE");
    }

    @Test
    void replace_keepsModulePermissionWhenAnotherTickedMenuSameModuleStillNeedsIt() {
        stored(tickets, true, false, false, false);
        stored(communication, true, false, false, false);
        held.add(RolePermission.grant(role.getId(), notifRead));

        service.replace(role.getId(), List.of(item(tickets, true, false, false, false)));

        assertThat(finalCodes()).contains("NOTIFICATION_READ");
    }

    @Test
    void replace_revokesModulePermissionWhenNoTickedMenuNeedsIt_butLeavesOtherModules() {
        stored(communication, true, false, false, false);
        held.add(RolePermission.grant(role.getId(), notifRead));
        held.add(RolePermission.grant(role.getId(), shipmentRead));

        service.replace(role.getId(), List.of());

        assertThat(finalCodes()).containsExactly("SHIPMENT_READ");
    }

    @Test
    void replace_ignoresGroupMenus() {
        service.replace(role.getId(), List.of(item(group, true, true, true, true)));

        ArgumentCaptor<List<RoleMenu>> saved = ArgumentCaptor.forClass(List.class);
        verify(roleMenuRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).isEmpty();
    }

    @Test
    void replace_rejectsARoleOfAnotherCompany() {
        UUID foreign = UUID.randomUUID();

        assertThatThrownBy(() -> service.replace(foreign, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ helpers

    private List<String> finalCodes() {
        ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);
        verify(rolePermissionService).assign(eq(role.getId()), codes.capture(), eq(true));
        return List.copyOf(codes.getValue());
    }

    private RoleMenu stored(MenuItem menu, boolean read, boolean create, boolean update, boolean delete) {
        RoleMenu row = RoleMenu.builder().roleId(role.getId()).menuItemId(menu.getId())
                .canRead(read).canCreate(create).canUpdate(update).canDelete(delete).build();
        stored.add(row);
        return row;
    }

    private RoleMenuItem item(MenuItem menu, boolean read, boolean create, boolean update, boolean delete) {
        return new RoleMenuItem(menu.getId(), create, read, update, delete);
    }

    private MenuItem menu(String code, PermissionModule module) {
        return MenuItem.builder().code(code).title(code).permissionModule(module).build();
    }

    private Permission permission(PermissionModule module, PermissionAction action) {
        Permission permission = Permission.builder().module(module).action(action)
                .status(PermissionStatus.ACTIVE).build();
        permission.applyInvariants();
        return permission;
    }
}
