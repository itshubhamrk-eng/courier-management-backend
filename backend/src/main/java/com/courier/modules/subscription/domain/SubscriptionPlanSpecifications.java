package com.courier.modules.subscription.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the plan catalogue.
 *
 * <p>Specifications rather than a pile of {@code findByXAndYAndZ} derived queries: the
 * filters combine freely, and every extra optional parameter would otherwise double
 * the method count.
 *
 * <p>All predicates are composed with {@code AND}. An absent filter contributes no
 * predicate at all, so it neither narrows nor widens the result.
 */
public final class SubscriptionPlanSpecifications {

    /** Characters that would otherwise be treated as wildcards in a LIKE pattern. */
    private static final char LIKE_ESCAPE = '\\';

    private SubscriptionPlanSpecifications() {
    }

    public static Specification<SubscriptionPlan> matching(SubscriptionPlanCriteria criteria) {
        SubscriptionPlanCriteria safe = criteria == null ? SubscriptionPlanCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.planType() != null) {
                predicates.add(cb.equal(root.get("planType"), safe.planType()));
            }

            if (safe.active() != null) {
                predicates.add(cb.equal(root.get("active"), safe.active()));
            }

            if (hasText(safe.currency())) {
                predicates.add(cb.equal(
                        cb.upper(root.get("currency")), safe.currency().trim().toUpperCase()));
            }

            if (safe.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("monthlyPrice"), scaled(safe.minPrice())));
            }

            if (safe.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("monthlyPrice"), scaled(safe.maxPrice())));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("planCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("planName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Active plans only, in the order a pricing page should render them. */
    public static Specification<SubscriptionPlan> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /**
     * A caller-supplied {@code %} or {@code _} must match itself, not act as a
     * wildcard — otherwise a search for {@code "%"} scans and returns the whole
     * catalogue regardless of the other filters.
     */
    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    /** Aligns the bound with the column's {@code DECIMAL(19,4)} scale. */
    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
