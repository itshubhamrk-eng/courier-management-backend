package com.courier.modules.company.application;

import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.modules.company.domain.PermissionModule;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Tree assembly and the catalogue's own guard rules, repositories mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuServiceImplTest {

    @Mock private MenuItemRepository repository;
    @Mock private AuditService auditService;

    private MenuServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MenuServiceImpl(repository, auditService);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MenuItem node(UUID id, UUID parentId, String code, int order) {
        MenuItem item = MenuItem.builder().parentId(parentId).code(code).title(code)
                .displayOrder(order).active(true).build();
        item.setId(id);
        return item;
    }

    @Test
    @DisplayName("flat rows assemble into a nested, order-sorted tree at every level")
    void treeNestsByParentAndOrder() {
        UUID group = UUID.randomUUID();
        UUID leafA = UUID.randomUUID();
        UUID leafB = UUID.randomUUID();
        UUID grandchild = UUID.randomUUID();

        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                node(group, null, "group", 1),
                node(leafB, group, "leaf-b", 2),
                node(leafA, group, "leaf-a", 1),
                node(grandchild, leafA, "grandchild", 1)));

        List<MenuService.MenuNode> tree = service.tree();

        assertThat(tree).hasSize(1);
        MenuService.MenuNode groupNode = tree.get(0);
        assertThat(groupNode.item().getCode()).isEqualTo("group");
        assertThat(groupNode.children()).extracting(n -> n.item().getCode())
                .containsExactly("leaf-a", "leaf-b"); // order 1 before order 2
        assertThat(groupNode.children().get(0).children()).hasSize(1);
        assertThat(groupNode.children().get(0).children().get(0).item().getCode()).isEqualTo("grandchild");
    }

    @Test
    @DisplayName("an inactive node is excluded from the tree")
    void inactiveNodeExcluded() {
        MenuItem inactive = node(UUID.randomUUID(), null, "off", 1);
        inactive.setActive(false);
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(inactive));

        assertThat(service.tree()).isEmpty();
    }

    @Test
    @DisplayName("a duplicate code is refused")
    void duplicateCodeRefused() {
        when(repository.existsByCode("users")).thenReturn(true);
        MenuItem request = MenuItem.builder().code("Users").title("Users")
                .permissionModule(PermissionModule.USER).build();

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("a system menu item cannot be deleted")
    void systemItemUndeletable() {
        UUID id = UUID.randomUUID();
        MenuItem system = node(id, null, "administration", 1);
        system.setSystem(true);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(system));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("an item with children cannot be deleted before they are removed")
    void itemWithChildrenUndeletable() {
        UUID id = UUID.randomUUID();
        MenuItem parent = node(id, null, "group", 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(parent));
        when(repository.findAllByParentId(id)).thenReturn(List.of(node(UUID.randomUUID(), id, "child", 1)));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(BusinessRuleException.class);
    }
}
