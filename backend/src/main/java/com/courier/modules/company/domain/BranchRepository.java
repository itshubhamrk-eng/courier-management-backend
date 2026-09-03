package com.courier.modules.company.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Branches, within a company.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound, and
 * a super-admin read (no company) spans companies. Single-row loads go through
 * {@link #findByIdWithinCompany} — a primary-key load bypasses the Hibernate filter.
 *
 * <p>Uniqueness checks are native and count soft-deleted rows: the unique keys do not
 * know about {@code deleted}, and MySQL returns {@code BIGINT} for {@code COUNT(*) > 0},
 * so the count is compared in Java.
 */
public interface BranchRepository extends JpaRepository<Branch, UUID>,
        JpaSpecificationExecutor<Branch> {

    Optional<Branch> findByBranchCode(String branchCode);

    /** Used by District Level Freight's Excel import to resolve a "From Station" cell
     *  against either the branch code or the branch name, case-insensitively. */
    Optional<Branch> findByBranchCodeIgnoreCase(String branchCode);

    Optional<Branch> findByBranchNameIgnoreCase(String branchName);

    @Query("select b from Branch b where b.id = :id and b.companyId = :companyId")
    Optional<Branch> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);

    List<Branch> findAllByCompanyIdAndStatusOrderByBranchCodeAsc(UUID companyId, BranchStatus status);

    /** Branch count by state, for the company statistics view. */
    long countByCompanyIdAndStatus(UUID companyId, BranchStatus status);

    /**
     * Branches this user manages. A user manages at most one, but {@code manager_id} has no
     * unique key, so this returns a page rather than an {@code Optional} that would throw
     * on an unexpected second row.
     */
    @Query("select b from Branch b where b.companyId = :companyId and b.managerId = :managerId "
            + "order by b.branchCode asc")
    List<Branch> findFirstByCompanyIdAndManagerId(@Param("companyId") UUID companyId,
                                                 @Param("managerId") UUID managerId,
                                                 Pageable pageable);

    default boolean isCodeTaken(UUID companyId, String branchCode, UUID excludeId) {
        return countByCodeIncludingDeleted(TimeOrderedUuid.toBytes(companyId), branchCode,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    default boolean isNameTaken(UUID companyId, String branchName, UUID excludeId) {
        return countByNameIncludingDeleted(TimeOrderedUuid.toBytes(companyId), branchName,
                TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM branches
            WHERE company_id = :companyId AND branch_code = :branchCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                     @Param("branchCode") String branchCode,
                                     @Param("excludeId") byte[] excludeId);

    @Query(value = """
            SELECT COUNT(*) FROM branches
            WHERE company_id = :companyId AND LOWER(branch_name) = LOWER(:branchName)
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByNameIncludingDeleted(@Param("companyId") byte[] companyId,
                                     @Param("branchName") String branchName,
                                     @Param("excludeId") byte[] excludeId);
}
