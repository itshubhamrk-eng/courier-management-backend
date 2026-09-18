package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The menu catalogue. Platform-level: no company filter applies, same posture as
 * {@link PermissionRepository}.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findAllByOrderByDisplayOrderAsc();

    Optional<MenuItem> findByCode(String code);

    boolean existsByCode(String code);

    List<MenuItem> findAllByParentId(UUID parentId);
}
