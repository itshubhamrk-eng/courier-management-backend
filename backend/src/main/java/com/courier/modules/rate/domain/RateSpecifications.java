package com.courier.modules.rate.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the rate search. All combine with {@code AND}; an absent
 * filter contributes nothing. Company scoping is the Hibernate filter's job, not this
 * class's — a {@code SUPER_ADMIN} never reaches a rate at all, a company's own operational
 * record.
 */
public final class RateSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private RateSpecifications() {
    }

    public static Specification<Rate> matching(RateCriteria criteria) {
        RateCriteria safe = criteria == null ? RateCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.routeIds() != null && !safe.routeIds().isEmpty()) {
                predicates.add(root.get("routeId").in(safe.routeIds()));
            }
            if (safe.serviceTypeIds() != null && !safe.serviceTypeIds().isEmpty()) {
                predicates.add(root.get("serviceTypeId").in(safe.serviceTypeIds()));
            }
            if (safe.packageTypeIds() != null && !safe.packageTypeIds().isEmpty()) {
                predicates.add(root.get("packageTypeId").in(safe.packageTypeIds()));
            }
            if (safe.paymentModeIds() != null && !safe.paymentModeIds().isEmpty()) {
                predicates.add(root.get("paymentModeId").in(safe.paymentModeIds()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("rateCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("rateName")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
