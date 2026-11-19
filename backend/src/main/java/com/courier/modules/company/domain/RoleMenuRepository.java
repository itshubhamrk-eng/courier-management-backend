package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Menus a company role holds. Company-owned, same filtering posture as {@link UserMenuGrantRepository}. */
public interface RoleMenuRepository extends JpaRepository<RoleMenu, UUID> {

    List<RoleMenu> findAllByRoleId(UUID roleId);

    List<RoleMenu> findAllByRoleIdIn(Collection<UUID> roleIds);
}
