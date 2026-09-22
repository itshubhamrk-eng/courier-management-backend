package com.courier.shared.activity.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ActivityLogSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ActivityLogSpecifications() {
    }

    public static Specification<ActivityLog> matching(ActivityLogCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // companyId is always bound by the service, never left null for a company
            // caller — a null here means the caller is genuinely platform-tier.
            if (criteria.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), criteria.companyId()));
            }
            if (criteria.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), criteria.userId()));
            }
            if (criteria.module() != null && !criteria.module().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("module")), criteria.module().toUpperCase()));
            }
            if (criteria.action() != null && !criteria.action().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("action")), criteria.action().toUpperCase()));
            }
            if (criteria.entityType() != null && !criteria.entityType().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), criteria.entityType().toUpperCase()));
            }
            if (criteria.entityId() != null && !criteria.entityId().isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), criteria.entityId()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.sessionId() != null) {
                predicates.add(cb.equal(root.get("sessionId"), criteria.sessionId()));
            }
            if (criteria.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), criteria.dateFrom()));
            }
            if (criteria.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), criteria.dateTo()));
            }
            if (criteria.search() != null && !criteria.search().isBlank()) {
                String pattern = "%" + escapeLike(criteria.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("entityId")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
