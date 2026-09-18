package com.courier.modules.company.application;

import com.courier.modules.company.domain.MenuItem;
import com.courier.modules.company.domain.MenuItemRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private static final String ENTITY = "MenuItem";
    private static final String SUPER_ADMIN_ONLY = "hasRole('" + Roles.SUPER_ADMIN + "')";

    private static final UUID ROOT = new UUID(0L, 0L);

    private final MenuItemRepository repository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<MenuItem> listAll() {
        return repository.findAllByOrderByDisplayOrderAsc().stream()
                .filter(MenuItem::isActive)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuNode> tree() {
        Map<UUID, List<MenuItem>> byParent = listAll().stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? ROOT : m.getParentId()));
        return build(null, byParent);
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public MenuItem create(MenuItem item) {
        if (item.getCode() == null || item.getCode().isBlank()) {
            throw new BusinessRuleException("A menu item needs a code.");
        }
        String code = item.getCode().trim().toLowerCase().replace(' ', '-');
        if (repository.existsByCode(code)) {
            throw new DuplicateResourceException(ENTITY, "code", code);
        }
        if (item.getParentId() != null) {
            repository.findById(item.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem (parent)", item.getParentId()));
        }
        item.setCode(code);
        item.setSystem(false);
        MenuItem saved = repository.save(item);
        auditService.record(AuditAction.MENU_ITEM_CREATED, ENTITY, saved.getId(),
                Map.of("code", saved.getCode(), "title", saved.getTitle()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public MenuItem update(UUID id, MenuItem changes) {
        MenuItem existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        existing.setTitle(changes.getTitle());
        existing.setIcon(changes.getIcon());
        existing.setRoute(changes.getRoute());
        existing.setPermissionModule(changes.getPermissionModule());
        existing.setDisplayOrder(changes.getDisplayOrder());
        existing.setActive(changes.isActive());
        MenuItem saved = repository.save(existing);
        auditService.record(AuditAction.MENU_ITEM_UPDATED, ENTITY, saved.getId(),
                Map.of("code", saved.getCode()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public void delete(UUID id) {
        MenuItem existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
        if (existing.isSystem()) {
            throw new BusinessRuleException("%s is a system menu item and cannot be deleted."
                    .formatted(existing.getCode()));
        }
        if (!repository.findAllByParentId(id).isEmpty()) {
            throw new BusinessRuleException("Remove this item's children first.");
        }
        existing.softDelete(SecurityUtils.getCurrentUserId().orElse(null));
        repository.save(existing);
        auditService.record(AuditAction.MENU_ITEM_DELETED, ENTITY, id, Map.of("code", existing.getCode()));
    }

    private List<MenuNode> build(UUID parentId, Map<UUID, List<MenuItem>> byParent) {
        List<MenuItem> children = byParent.getOrDefault(parentId == null ? ROOT : parentId, List.of());
        List<MenuNode> nodes = new ArrayList<>();
        for (MenuItem item : children.stream()
                .sorted(Comparator.comparingInt(MenuItem::getDisplayOrder)).toList()) {
            nodes.add(new MenuNode(item, build(item.getId(), byParent)));
        }
        return nodes;
    }
}
