package com.courier.modules.customerpricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pricing zones, within a company. Single-row loads go through {@link #findByIdWithinCompany}. */
public interface PricingZoneRepository extends JpaRepository<PricingZone, UUID> {

    @Query("select z from PricingZone z where z.id = :id and z.companyId = :companyId")
    Optional<PricingZone> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select z from PricingZone z where z.companyId = :companyId"
            + " and (:customerId is null or z.customerId = :customerId)"
            + " and (:status is null or z.status = :status)"
            + " and (:search is null or lower(z.zoneName) like :search)"
            + " order by z.zoneName")
    List<PricingZone> search(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                             @Param("status") ZoneStatus status, @Param("search") String search);

    @Query("select count(z) > 0 from PricingZone z where z.companyId = :companyId and z.customerId = :customerId"
            + " and z.fallbackScope = :scope and (:excludeId is null or z.id <> :excludeId)")
    boolean existsFallback(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                           @Param("scope") FallbackScope scope, @Param("excludeId") UUID excludeId);

    @Query("select z from PricingZone z where z.companyId = :companyId and z.customerId = :customerId"
            + " and z.fallbackScope = :scope")
    List<PricingZone> findFallback(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                                   @Param("scope") FallbackScope scope);

    @Query("select count(z) > 0 from PricingZone z where z.companyId = :companyId and z.customerId = :customerId"
            + " and lower(z.zoneName) = lower(:name) and (:excludeId is null or z.id <> :excludeId)")
    boolean existsByName(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                         @Param("name") String name, @Param("excludeId") UUID excludeId);
}
