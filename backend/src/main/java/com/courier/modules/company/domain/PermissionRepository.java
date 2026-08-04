package com.courier.modules.company.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The permission catalogue.
 *
 * <p>Platform-level: no company filter applies, so {@code findById} is safe here. Writes
 * are restricted to {@code SUPER_ADMIN} at the service layer.
 */
public interface PermissionRepository extends JpaRepository<Permission, UUID>,
        JpaSpecificationExecutor<Permission> {

    Optional<Permission> findByPermissionCode(String permissionCode);

    List<Permission> findAllByPermissionCodeIn(Collection<String> permissionCodes);

    List<Permission> findAllByModuleOrderByDisplayOrderAsc(PermissionModule module);

    List<Permission> findAllByStatusOrderByDisplayOrderAsc(PermissionStatus status);

    boolean existsByModuleAndAction(PermissionModule module, PermissionAction action);

    /**
     * Counts a code across soft-deleted rows too, because the unique key does not know
     * about {@code deleted} and {@code @SQLRestriction} cannot be disabled per query.
     * Returns a count rather than a boolean: MySQL has no boolean type, so
     * {@code COUNT(*) > 0} comes back {@code BIGINT} and mapping it to {@code boolean}
     * throws at runtime.
     */
    @Query(value = """
            SELECT COUNT(*) FROM permissions
            WHERE permission_code = :permissionCode
            """, nativeQuery = true)
    long countByCodeIncludingDeleted(@Param("permissionCode") String permissionCode);

    default boolean isCodeTaken(String permissionCode) {
        return countByCodeIncludingDeleted(permissionCode) > 0;
    }
}
