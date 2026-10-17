package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Per-user, per-menu ticks. Company-owned, same filtering posture as {@link UserPermissionOverrideRepository}. */
public interface UserMenuGrantRepository extends JpaRepository<UserMenuGrant, UUID> {

    List<UserMenuGrant> findAllByUserId(UUID userId);
}
