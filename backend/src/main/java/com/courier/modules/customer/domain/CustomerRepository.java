package com.courier.modules.customer.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Customers, within a company.
 *
 * <p>Company-owned: every derived query relies on {@code CompanyContext} being bound.
 * Single-row loads go through {@link #findByIdWithinCompany} — a primary-key load
 * bypasses the Hibernate filter. Uniqueness checks are native and (for the code)
 * count soft-deleted rows, the same reasoning {@code BranchRepository} documents.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID>,
        JpaSpecificationExecutor<Customer> {

    @Query("select c from Customer c where c.id = :id and c.companyId = :companyId")
    Optional<Customer> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    long countByCompanyId(UUID companyId);

    default boolean isCodeTaken(UUID companyId, String customerCode, UUID excludeId) {
        return countByCodeIncludingDeleted(TimeOrderedUuid.toBytes(companyId), customerCode,
                excludeId == null ? null : TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /**
     * Mobile is unique within the company but, unlike the code, is <b>not</b> reserved
     * past a soft delete — see {@link Customer}'s class comment.
     */
    default boolean isMobileTaken(UUID companyId, String mobile, UUID excludeId) {
        return countByMobile(TimeOrderedUuid.toBytes(companyId), mobile,
                excludeId == null ? null : TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    @Query(value = """
            SELECT COUNT(*) FROM customers
            WHERE company_id = :companyId AND customer_code = :customerCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByCodeIncludingDeleted(@Param("companyId") byte[] companyId,
                                     @Param("customerCode") String customerCode,
                                     @Param("excludeId") byte[] excludeId);

    @Query(value = """
            SELECT COUNT(*) FROM customers
            WHERE company_id = :companyId AND mobile = :mobile AND deleted = FALSE
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByMobile(@Param("companyId") byte[] companyId,
                       @Param("mobile") String mobile,
                       @Param("excludeId") byte[] excludeId);
}
