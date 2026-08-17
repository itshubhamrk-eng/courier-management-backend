package com.courier.modules.support.application;

import com.courier.modules.support.application.command.CreateTicketCategoryCommand;
import com.courier.modules.support.application.command.CreateTicketSubCategoryCommand;
import com.courier.modules.support.domain.TicketCategory;
import com.courier.modules.support.domain.TicketCategoryRepository;
import com.courier.modules.support.domain.TicketSubCategory;
import com.courier.modules.support.domain.TicketSubCategoryRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.DuplicateResourceException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketCategoryServiceImpl implements TicketCategoryService {

    private static final String SUPER_ADMIN_ONLY = "hasRole('" + Roles.SUPER_ADMIN + "')";
    private static final String CATEGORY = "TicketCategory";
    private static final String SUB_CATEGORY = "TicketSubCategory";

    private final TicketCategoryRepository categoryRepository;
    private final TicketSubCategoryRepository subCategoryRepository;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<TicketCategory> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketSubCategory> listSubCategories(UUID categoryId) {
        return subCategoryRepository.findAllByCategoryIdOrderByNameAsc(categoryId);
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketCategory createCategory(CreateTicketCategoryCommand command) {
        String name = requireName(command.name());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A category named '" + name + "' already exists.");
        }
        TicketCategory saved = categoryRepository.save(TicketCategory.builder().name(name).active(true).build());
        auditService.record(AuditAction.TICKET_CATEGORY_CREATED, CATEGORY, saved.getId(), Map.of("name", name));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketCategory renameCategory(UUID id, String name) {
        TicketCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY, id));
        category.setName(requireName(name));
        TicketCategory saved = categoryRepository.save(category);
        auditService.record(AuditAction.TICKET_CATEGORY_UPDATED, CATEGORY, saved.getId(), Map.of("name", saved.getName()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketCategory setCategoryActive(UUID id, boolean active) {
        TicketCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY, id));
        category.setActive(active);
        TicketCategory saved = categoryRepository.save(category);
        auditService.record(AuditAction.TICKET_CATEGORY_UPDATED, CATEGORY, saved.getId(),
                Map.of("active", active));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketSubCategory createSubCategory(CreateTicketSubCategoryCommand command) {
        if (command.categoryId() == null) {
            throw new BusinessRuleException("A parent category is required.");
        }
        categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(CATEGORY, command.categoryId()));
        String name = requireName(command.name());
        if (subCategoryRepository.existsByCategoryIdAndNameIgnoreCase(command.categoryId(), name)) {
            throw new DuplicateResourceException("A sub-category named '" + name + "' already exists here.");
        }
        TicketSubCategory saved = subCategoryRepository.save(TicketSubCategory.builder()
                .categoryId(command.categoryId()).name(name).active(true).build());
        auditService.record(AuditAction.TICKET_SUB_CATEGORY_CREATED, SUB_CATEGORY, saved.getId(),
                Map.of("name", name, "categoryId", command.categoryId().toString()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketSubCategory renameSubCategory(UUID id, String name) {
        TicketSubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(SUB_CATEGORY, id));
        subCategory.setName(requireName(name));
        TicketSubCategory saved = subCategoryRepository.save(subCategory);
        auditService.record(AuditAction.TICKET_SUB_CATEGORY_UPDATED, SUB_CATEGORY, saved.getId(),
                Map.of("name", saved.getName()));
        return saved;
    }

    @Override
    @Transactional
    @PreAuthorize(SUPER_ADMIN_ONLY)
    public TicketSubCategory setSubCategoryActive(UUID id, boolean active) {
        TicketSubCategory subCategory = subCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(SUB_CATEGORY, id));
        subCategory.setActive(active);
        TicketSubCategory saved = subCategoryRepository.save(subCategory);
        auditService.record(AuditAction.TICKET_SUB_CATEGORY_UPDATED, SUB_CATEGORY, saved.getId(),
                Map.of("active", active));
        return saved;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Name is required.");
        }
        return name.trim();
    }
}
