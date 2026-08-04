package com.courier.modules.company.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Roles within a company.
 *
 * <p><b>Company-owned.</b> Every derived query here relies on {@code CompanyContext} being
 * bound — {@code CompanyFilterAspect} then appends {@code company_id = :companyId}
 * automatically. A {@code COMPANY_ADMIN} request is bound from the JWT; provisioning and
 * platform-level reads bind it explicitly with {@code CompanyContext.runAs}.
 *
 * <p>A {@code SUPER_ADMIN} request carries <em>no</em> company, so the filter is inactive
 * and these queries span every company. That is intentional for the platform-wide role
 * listing and nowhere else — writes are restricted to {@code COMPANY_ADMIN}, who always
 * has a company.
 */
public interface CompanyRoleRepository extends JpaRepository<CompanyRole, UUID>,
        JpaSpecificationExecutor<CompanyRole> {

    Optional<CompanyRole> findByRoleCode(String roleCode);

    List<CompanyRole> findAllByOrderByRoleCodeAsc();

    List<CompanyRole> findAllByStatusOrderByRoleCodeAsc(RoleStatus status);

    boolean existsByRoleCode(String roleCode);

    /**
     * Explicit company predicate rather than {@code findById}: a primary-key load
     * bypasses the Hibernate filter entirely, so one company could otherwise fetch
     * another's role by guessing an id.
     */
    @Query("select r from CompanyRole r where r.id = :id and r.companyId = :companyId")
    Optional<CompanyRole> findByIdWithinCompany(@Param("id") UUID id,
                                               @Param("companyId") UUID companyId);

    /** The role a new user receives when none is specified. At most one per company. */
    @Query("select r from CompanyRole r where r.companyId = :companyId and r.defaultRole = true")
    Optional<CompanyRole> findDefaultRole(@Param("companyId") UUID companyId);

    @Query("select r from CompanyRole r where r.companyId = :companyId and r.defaultRole = true "
            + "and r.id <> :excludeId")
    List<CompanyRole> findOtherDefaultRoles(@Param("companyId") UUID companyId,
                                            @Param("excludeId") UUID excludeId);

    long countByCompanyId(UUID companyId);

    // ---------------------------------------------------------------- uniqueness

    /**
     * Is this code taken within the company, counting soft-deleted rows?
     *
     * @param excludeId the row being updated, so it does not clash with itself
     */
    default boolean isRoleCodeTaken(UUID companyId, String roleCode, UUID excludeId) {
        return countByRoleCodeIncludingDeleted(TimeOrderedUuid.toBytes(companyId), roleCode,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /** Name counterpart of {@link #isRoleCodeTaken}. Case-insensitive. */
    default boolean isRoleNameTaken(UUID companyId, String roleName, UUID excludeId) {
        return countByRoleNameIncludingDeleted(TimeOrderedUuid.toBytes(companyId), roleName,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /**
     * Native, and takes ids as raw bytes, for three reasons.
     *
     * <p><b>Native:</b> {@code @SQLRestriction("deleted = false")} is appended to every
     * HQL query for this entity and cannot be disabled per query, but the database unique
     * key {@code (company_id, role_code)} does not know about {@code deleted} — so a code
     * still held by a soft-deleted role would pass an HQL pre-check and then be rejected
     * by the constraint as an opaque 409.
     *
     * <p><b>Bytes:</b> native SQL has no entity mapping to consult, so a {@code UUID}
     * would be sent in string form and never match a {@code BINARY(16)} column.
     *
     * <p><b>Count, not boolean:</b> MySQL has no boolean type, so {@code COUNT(*) > 0}
     * returns {@code BIGINT} and mapping it to {@code boolean} throws
     * {@code ClassCastException} at runtime. That exact bug shipped once already.
     *
     * <p>The company is named explicitly because native SQL escapes the Hibernate filter.
     * Callers should use {@link #isRoleCodeTaken}.
     */
    @Query(value = """
            SELECT COUNT(*) FROM company_roles
            WHERE company_id = :companyId
              AND role_code = :roleCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByRoleCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                         @Param("roleCode") String roleCode,
                                         @Param("excludeId") byte[] excludeId);

    /** See {@link #countByRoleCodeIncludingDeleted}; use {@link #isRoleNameTaken}. */
    @Query(value = """
            SELECT COUNT(*) FROM company_roles
            WHERE company_id = :companyId
              AND LOWER(role_name) = LOWER(:roleName)
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByRoleNameIncludingDeleted(@Param("companyId") byte[] companyId,
                                         @Param("roleName") String roleName,
                                         @Param("excludeId") byte[] excludeId);
}
