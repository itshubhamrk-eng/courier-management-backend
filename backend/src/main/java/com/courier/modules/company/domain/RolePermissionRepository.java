package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Grants of permissions to roles.
 *
 * <p>Company-owned: every query relies on {@code CompanyContext} being bound, and
 * {@code CompanyFilterAspect} appends {@code company_id = :companyId}. Provisioning and
 * platform-level reads bind it explicitly with {@code CompanyContext.runAs}.
 */
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findAllByRoleIdOrderByPermissionCodeAsc(UUID roleId);

    Optional<RolePermission> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    boolean existsByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

    long countByRoleId(UUID roleId);

    /** Grants held by several roles at once — how a user's effective rights are resolved. */
    List<RolePermission> findAllByRoleIdIn(Collection<UUID> roleIds);

    /** Just the codes, for authorisation checks that never need the rows. */
    @Query("select rp.permissionCode from RolePermission rp where rp.roleId = :roleId")
    List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);

    /**
     * Every role in the company holding this permission. Used before a permission is
     * retired, and to answer "who can cancel shipments?".
     */
    @Query("select rp.roleId from RolePermission rp where rp.permissionId = :permissionId")
    List<UUID> findRoleIdsByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * Deliberately crosses companies: it answers "is this catalogue entry still in use
     * anywhere", which is what a {@code SUPER_ADMIN} must know before deleting it. It
     * returns a count and no rows, so it exposes nothing about any company.
     */
    @Query(value = "SELECT COUNT(*) FROM role_permissions WHERE permission_id = :permissionId "
            + "AND deleted = FALSE", nativeQuery = true)
    long countGrantsAcrossAllCompanies(@Param("permissionId") byte[] permissionId);
}
