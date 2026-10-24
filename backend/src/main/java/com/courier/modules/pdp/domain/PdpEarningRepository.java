package com.courier.modules.pdp.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PdpEarningRepository extends JpaRepository<PdpEarning, UUID> {

    @Query("select e from PdpEarning e where e.id = :id and e.companyId = :companyId")
    Optional<PdpEarning> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select e from PdpEarning e where e.companyId = :companyId and e.earningType = :type and e.sourceId = :sourceId")
    Optional<PdpEarning> findBySource(@Param("companyId") UUID companyId, @Param("type") PdpEarningType type,
                                      @Param("sourceId") UUID sourceId);

    @Query("""
            select e from PdpEarning e
            where e.companyId = :companyId
              and (:branchId is null or e.branchId = :branchId)
              and (:type is null or e.earningType = :type)
              and (:status is null or e.status = :status)
              and (:from is null or e.earnedOn >= :from)
              and (:to is null or e.earnedOn <= :to)
            order by e.earnedOn desc, e.createdAt desc""")
    Page<PdpEarning> search(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                            @Param("type") PdpEarningType type, @Param("status") PdpEarningStatus status,
                            @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    /** Unbilled, priced earnings of a partner in a period: what a new statement is built from. */
    @Query("""
            select e from PdpEarning e
            where e.companyId = :companyId and e.branchId = :branchId and e.status = 'ACCRUED'
              and e.statementId is null and e.earnedOn >= :from and e.earnedOn <= :to
            order by e.earningType, e.earnedOn, e.createdAt""")
    List<PdpEarning> findBillable(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(e) from PdpEarning e where e.companyId = :companyId and e.branchId = :branchId "
            + "and e.status = 'UNRATED' and e.earnedOn >= :from and e.earnedOn <= :to")
    long countUnrated(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e from PdpEarning e where e.companyId = :companyId and e.statementId = :statementId "
            + "order by e.earningType, e.earnedOn, e.createdAt")
    List<PdpEarning> findByStatement(@Param("companyId") UUID companyId, @Param("statementId") UUID statementId);

    /** Earliest date among a partner's unbilled, priced earnings up to {@code to}; null when there are none. */
    @Query("select min(e.earnedOn) from PdpEarning e where e.companyId = :companyId and e.branchId = :branchId "
            + "and e.status = 'ACCRUED' and e.statementId is null and e.earnedOn <= :to")
    LocalDate earliestUnbilled(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId,
                               @Param("to") LocalDate to);

    @Query("select count(e) from PdpEarning e where e.companyId = :companyId and e.branchId = :branchId "
            + "and e.status = 'ACCRUED' and e.statementId is null")
    long countUnbilled(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId);

    /** Count and money per (partner, type, status) for earnings dated on/after {@code from}; pass a very early date for all time. */
    @Query("select e.branchId, e.earningType, e.status, count(e), coalesce(sum(e.totalAmount), 0) from PdpEarning e "
            + "where e.companyId = :companyId and (:branchId is null or e.branchId = :branchId) "
            + "and e.earnedOn >= :from "
            + "group by e.branchId, e.earningType, e.status")
    List<Object[]> summarise(@Param("companyId") UUID companyId, @Param("branchId") UUID branchId, @Param("from") LocalDate from);
}
