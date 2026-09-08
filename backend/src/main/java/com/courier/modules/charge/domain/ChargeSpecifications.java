package com.courier.modules.charge.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the charge search. All combine with {@code AND}; an absent
 * filter contributes nothing. Company scoping is the Hibernate filter's job, not this
 * class's.
 */
public final class ChargeSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ChargeSpecifications() {
    }

    public static Specification<Charge> matching(ChargeCriteria criteria) {
        ChargeCriteria safe = criteria == null ? ChargeCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.serviceTypeIds() != null && !safe.serviceTypeIds().isEmpty()) {
                predicates.add(root.get("serviceTypeId").in(safe.serviceTypeIds()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("chargeName")), pattern, LIKE_ESCAPE));
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
