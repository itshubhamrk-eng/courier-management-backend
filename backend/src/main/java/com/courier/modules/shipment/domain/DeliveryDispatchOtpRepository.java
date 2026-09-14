package com.courier.modules.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryDispatchOtpRepository extends JpaRepository<DeliveryDispatchOtp, UUID> {

    @Query("select o from DeliveryDispatchOtp o where o.companyId = :companyId and o.deliveryUserId = :deliveryUserId")
    Optional<DeliveryDispatchOtp> findByCompanyIdAndDeliveryUserId(@Param("companyId") UUID companyId,
                                                                    @Param("deliveryUserId") UUID deliveryUserId);
}
