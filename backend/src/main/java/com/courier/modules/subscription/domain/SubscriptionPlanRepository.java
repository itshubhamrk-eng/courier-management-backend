package com.courier.modules.subscription.domain;

import com.courier.shared.domain.TimeOrderedUuid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription plan catalogue.
 *
 * <p>Not company-owned: the catalogue is platform-wide, so no company filter applies and
 * {@code findById} is safe here — unlike in {@code UserRepository}, where a primary-key
 * load would bypass the company filter.
 *
 * <p>{@link JpaSpecificationExecutor} backs the filtering and searching API; the
 * predicates themselves live in {@link SubscriptionPlanSpecifications}.
 */
public interface SubscriptionPlanRepository
        extends JpaRepository<SubscriptionPlan, UUID>, JpaSpecificationExecutor<SubscriptionPlan> {

    Optional<SubscriptionPlan> findByPlanCode(String planCode);

    /** The catalogue offered to new companies, in display order. */
    List<SubscriptionPlan> findAllByActiveTrueOrderByDisplayOrderAscPlanCodeAsc();

    /**
     * Is this plan code already taken, counting soft-deleted rows?
     *
     * @param excludeId the row being updated, so it does not clash with itself;
     *                  null when creating
     */
    default boolean isPlanCodeTaken(String planCode, UUID excludeId) {
        return countByPlanCodeIncludingDeleted(planCode, TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /** Name counterpart of {@link #isPlanCodeTaken}. Case-insensitive. */
    default boolean isPlanNameTaken(String planName, UUID excludeId) {
        return countByPlanNameIncludingDeleted(planName, TimeOrderedUuid.toBytes(excludeId)) > 0;
    }

    /**
     * Native, and takes the id as raw bytes, for two reasons.
     *
     * <p><b>Native:</b> {@code @SQLRestriction("deleted = false")} is appended to every
     * HQL query for this entity and cannot be switched off per query. Without a native
     * query, a code still held by a soft-deleted row would pass the service's
     * pre-check and then be rejected by the database unique key as an opaque 409.
     * The {@code nativeQuery} ban in {@code MEMORY/ARCHITECTURE.md} §3 exists because
     * native SQL escapes the <em>company</em> filter; this entity is platform-level, so
     * there is no company filter to escape.
     *
     * <p><b>Bytes:</b> native SQL has no entity mapping to consult, so a {@code UUID}
     * would be sent in its string form and never match a {@code BINARY(16)} column.
     *
     * <p>Returns a count rather than a boolean: MySQL has no boolean type, so
     * {@code SELECT COUNT(*) > 0} comes back as {@code BIGINT} and mapping it to
     * {@code Boolean} fails at runtime with a {@code ClassCastException}. The
     * comparison is therefore done in Java.
     *
     * <p>Callers should use {@link #isPlanCodeTaken} instead of this method.
     */
    @Query(value = """
            SELECT COUNT(*) FROM subscription_plans
            WHERE plan_code = :planCode
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByPlanCodeIncludingDeleted(@Param("planCode") String planCode,
                                         @Param("excludeId") byte[] excludeId);

    /** See {@link #countByPlanCodeIncludingDeleted}; use {@link #isPlanNameTaken} instead. */
    @Query(value = """
            SELECT COUNT(*) FROM subscription_plans
            WHERE LOWER(plan_name) = LOWER(:planName)
              AND (:excludeId IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    long countByPlanNameIncludingDeleted(@Param("planName") String planName,
                                         @Param("excludeId") byte[] excludeId);
}
