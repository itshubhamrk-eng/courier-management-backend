package com.courier.modules.distance.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolved distances, within a company. Company isolation via the Hibernate filter. */
public interface AddressDistanceRepository extends JpaRepository<AddressDistance, UUID> {

    Optional<AddressDistance> findByAddressTypeAndFromIdAndToId(
            AddressType addressType, UUID fromId, UUID toId);

    /** A primary-key load bypasses the Hibernate filter — go through this instead. */
    @Query("select d from AddressDistance d where d.id = :id and d.companyId = :companyId")
    Optional<AddressDistance> findByIdWithinCompany(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("select d from AddressDistance d where d.companyId = :companyId "
            + "and (:addressType is null or d.addressType = :addressType) "
            + "and (:fromId is null or d.fromId = :fromId) "
            + "and (:toId is null or d.toId = :toId) "
            + "order by d.createdAt desc")
    List<AddressDistance> search(@Param("companyId") UUID companyId,
                                 @Param("addressType") AddressType addressType,
                                 @Param("fromId") UUID fromId,
                                 @Param("toId") UUID toId);

    /**
     * Whether a <em>soft-deleted</em> row already occupies this pair — {@code
     * uk_address_distance_pair} is not scoped by {@code deleted} (same choice
     * {@code branches}' own code/name uniqueness makes), so before inserting a fresh row
     * the service must check this first: an insert straight into an occupied slot fails at
     * flush time, by which point the transaction's persistence context is no longer usable
     * for a same-transaction recovery. Native, since {@code @SQLRestriction} hides deleted
     * rows from every ORM-level query including this one if left as HQL.
     */
    @Query(value = "SELECT COUNT(*) FROM address_distance WHERE company_id = :companyId "
            + "AND address_type = :addressType AND from_id = :fromId AND to_id = :toId AND deleted = true",
            nativeQuery = true)
    long countDeletedPair(@Param("companyId") byte[] companyId, @Param("addressType") String addressType,
                          @Param("fromId") byte[] fromId, @Param("toId") byte[] toId);

    /** Un-deletes and refreshes the row {@link #countDeletedPair} found, instead of
     *  inserting a new one — native for the same {@code @SQLRestriction} reason. */
    @Modifying
    @Query(value = "UPDATE address_distance SET deleted = false, deleted_at = NULL, deleted_by = NULL, "
            + "distance_km = :distanceKm, distance_meter = :distanceMeter, "
            + "required_time_minutes = :requiredTimeMinutes, updated_at = CURRENT_TIMESTAMP(6), "
            + "version = version + 1 "
            + "WHERE company_id = :companyId AND address_type = :addressType "
            + "AND from_id = :fromId AND to_id = :toId AND deleted = true",
            nativeQuery = true)
    int restoreAndUpdate(@Param("companyId") byte[] companyId, @Param("addressType") String addressType,
                         @Param("fromId") byte[] fromId, @Param("toId") byte[] toId,
                         @Param("distanceKm") BigDecimal distanceKm,
                         @Param("distanceMeter") BigDecimal distanceMeter,
                         @Param("requiredTimeMinutes") BigDecimal requiredTimeMinutes);
}
