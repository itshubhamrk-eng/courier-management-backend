package com.courier.modules.company.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Users, from the company-administration context.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound.
 * A {@code SUPER_ADMIN} request carries no company, so the filter is inactive and reads
 * span every company — intentional for the platform-wide user listing, and the reason
 * writes are pinned to the caller's company in the service.
 *
 * <p>This is the company context's own repository over the shared {@code users} table;
 * auth has its own. See {@link User} for why the table is a shared kernel.
 */
public interface CompanyUserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    /**
     * Explicit company predicate rather than {@code findById}: a primary-key load bypasses
     * the Hibernate filter, so one company could otherwise fetch another's user by id.
     */
    @Query("select u from CompanyUser u where u.id = :id and u.companyId = :companyId")
    Optional<User> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    /**
     * Users of one company by id. Used to bulk-assign users to a branch: every requested
     * id must resolve to a user of the caller's company, so a foreign id is simply absent
     * from the result rather than silently updated.
     */
    @Query("select u from CompanyUser u where u.companyId = :companyId and u.id in :ids")
    java.util.List<User> findAllByIdInWithinCompany(@Param("ids") java.util.Collection<UUID> ids,
                                                   @Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);

    /** Headcount by lifecycle state, for the company statistics view. */
    long countByCompanyIdAndStatus(UUID companyId, UserStatus status);

    // ---------------------------------------------------------------- uniqueness
    // All count soft-deleted rows too: the unique keys do not know about `deleted`, and
    // @SQLRestriction cannot be switched off per query. Count, not boolean — MySQL has no
    // boolean type, so COUNT(*) > 0 comes back BIGINT and mapping it to boolean throws.

    default boolean isEmailTaken(UUID companyId, String email, UUID excludeId) {
        return countByCompanyEmailIncludingDeleted(TimeOrderedUuid.toBytes(companyId), email,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    default boolean isEmployeeCodeTaken(UUID companyId, String employeeCode, UUID excludeId) {
        return employeeCode != null && countByCompanyEmployeeCodeIncludingDeleted(
                TimeOrderedUuid.toBytes(companyId), employeeCode, TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /** Username is globally unique, so this check is not company-scoped. */
    default boolean isUsernameTaken(String username, UUID excludeId) {
        return username != null && countByUsernameIncludingDeleted(
                username, TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE company_id = :companyId AND email = :email
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByCompanyEmailIncludingDeleted(@Param("companyId") byte[] companyId,
                                            @Param("email") String email,
                                            @Param("excludeId") byte[] excludeId);

    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE company_id = :companyId AND employee_code = :employeeCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByCompanyEmployeeCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                                   @Param("employeeCode") String employeeCode,
                                                   @Param("excludeId") byte[] excludeId);

    @Query(value = """
            SELECT COUNT(*) FROM users
            WHERE username = :username
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByUsernameIncludingDeleted(@Param("username") String username,
                                         @Param("excludeId") byte[] excludeId);
}
