package com.courier.modules.pdp.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PdpStatementRepository extends JpaRepository<PdpStatement, UUID> {

    @Query("select s from PdpStatement s where s.id = :id and s.companyId = :companyId")
    Optional<PdpStatement> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
            select s from PdpStatement s
            where s.companyId = :companyId
              and (:branchId is null or s.branchId = :branchId)
              and (:status is null or s.status = :status)
            order by s.createdAt desc""")
    Page<PdpStatement> search(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                              @Param("status") PdpStatementStatus status, Pageable pageable);

    @Query("select count(s) from PdpStatement s where s.companyId = :companyId and s.createdAt >= :from and s.createdAt < :to")
    long countCreatedBetween(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    /** A live statement of the partner whose period overlaps [from, to]. */
    @Query("select count(s) from PdpStatement s where s.companyId = :companyId and s.branchId = :branchId "
            + "and s.periodFrom <= :to and s.periodTo >= :from")
    long countOverlapping(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                          @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    /** End date of the partner's latest statement period, if any. */
    @Query("select max(s.periodTo) from PdpStatement s where s.companyId = :companyId and s.branchId = :branchId")
    java.time.LocalDate latestPeriodEnd(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId);
}
