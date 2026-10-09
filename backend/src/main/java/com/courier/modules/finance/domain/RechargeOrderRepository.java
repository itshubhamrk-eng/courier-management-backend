package com.courier.modules.finance.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, UUID>,
        JpaSpecificationExecutor<RechargeOrder> {

    @Query("select r from RechargeOrder r where r.gatewayOrderId = :orderId and r.companyId = :companyId")
    Optional<RechargeOrder> findByGatewayOrderIdWithinCompany(@Param("orderId") String orderId,
                                                               @Param("companyId") UUID companyId);

    /** Scheduler feed — genuinely cross-company (runs with no CompanyContext bound). PENDING
     *  attempts old enough that the browser has had its chance, not checked in the last minute. */
    @Query("select r from RechargeOrder r where r.status = com.courier.modules.finance.domain.PaymentStatus.PENDING "
            + "and r.createdAt < :createdBefore and (r.lastCheckedAt is null or r.lastCheckedAt < :checkedBefore) "
            + "order by r.createdAt asc")
    List<RechargeOrder> findPendingToReconcile(@Param("createdBefore") Instant createdBefore,
                                               @Param("checkedBefore") Instant checkedBefore,
                                               Pageable pageable);
}
