package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Role assignments to users. Company-owned; the company is bound from the caller's JWT or,
 * for a super-admin read, left unbound so the query spans companies.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findAllByUserIdOrderByRoleCodeAsc(UUID userId);

    Optional<UserRole> findByUserIdAndRoleId(UUID userId, UUID roleId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    long countByUserId(UUID userId);

    /** Assignments for several users at once, so a list page avoids an N+1 per row. */
    List<UserRole> findAllByUserIdIn(Collection<UUID> userIds);

    /** Users a role is assigned to — consulted before a role is deleted or deactivated. */
    @Query("select ur.userId from UserRole ur where ur.roleId = :roleId")
    List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * Drops every assignment for a user outright, not a soft delete. {@code User.delete()}
     * leaves these rows in place (deliberately — see its comment), so reviving a
     * soft-deleted user must clear them first: re-assigning the same role would otherwise
     * collide with the still-active {@code (company_id, user_id, role_id)} row from before
     * the deletion.
     */
    long deleteByUserId(UUID userId);
}
