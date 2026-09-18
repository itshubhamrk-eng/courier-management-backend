package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-user permission deltas. Company-owned, same filtering posture as
 * {@link RolePermissionRepository}.
 */
public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, UUID> {

    List<UserPermissionOverride> findAllByUserIdOrderByPermissionCodeAsc(UUID userId);

    Optional<UserPermissionOverride> findByUserIdAndPermissionId(UUID userId, UUID permissionId);

    /**
     * Including soft-deleted rows — {@code @SQLRestriction} filters every derived and
     * JPQL query against this entity, so only a native query can see a row a prior
     * "reverted to role default" call soft-deleted. Needed because the unique key
     * {@code (company_id, user_id, permission_id)} does not know about {@code deleted}
     * either: toggling the same right off, back to default, then off again must
     * resurrect that original row rather than insert a second one, which would collide
     * with it.
     */
    @Query(value = "SELECT * FROM user_permission_overrides "
            + "WHERE company_id = :companyId AND user_id = :userId AND permission_id = :permissionId "
            + "LIMIT 1", nativeQuery = true)
    Optional<UserPermissionOverride> findAnyByUserAndPermissionIncludingDeleted(
            @Param("companyId") byte[] companyId, @Param("userId") byte[] userId,
            @Param("permissionId") byte[] permissionId);
}
