package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the company search.
 *
 * <p>Specifications rather than derived queries: ten optional filters would otherwise
 * mean an unmaintainable number of {@code findByXAndY} methods. All predicates combine
 * with {@code AND}; an absent filter contributes nothing.
 */
public final class CompanySpecifications {

    /** Escapes characters that would otherwise act as LIKE wildcards. */
    private static final char LIKE_ESCAPE = '\\';

    private CompanySpecifications() {
    }

    public static Specification<Company> matching(CompanyCriteria criteria) {
        CompanyCriteria safe = criteria == null ? CompanyCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }

            if (safe.active() != null) {
                predicates.add(cb.equal(root.get("active"), safe.active()));
            }

            if (safe.subscriptionPlanId() != null) {
                predicates.add(cb.equal(root.get("subscriptionPlanId"), safe.subscriptionPlanId()));
            }

            addEqualsIgnoreCase(predicates, cb, root.get("country"), safe.country());
            addEqualsIgnoreCase(predicates, cb, root.get("state"), safe.state());
            addEqualsIgnoreCase(predicates, cb, root.get("city"), safe.city());

            if (safe.expiringBefore() != null) {
                // Either window ending qualifies: a trial company has no subscription
                // end date, and a paying one has no trial end date.
                predicates.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("subscriptionEndDate"), safe.expiringBefore()),
                        cb.lessThanOrEqualTo(root.get("trialEndDate"), safe.expiringBefore())));
            }

            if (safe.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), startOfDay(safe.createdFrom())));
            }

            if (safe.createdTo() != null) {
                // Inclusive of the whole day: created_at is a timestamp, so the bound is
                // the start of the following day, exclusive.
                predicates.add(cb.lessThan(
                        root.get("createdAt"), startOfDay(safe.createdTo().plusDays(1))));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("companyCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("companyName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("legalName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("mobile")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEqualsIgnoreCase(List<Predicate> predicates,
                                            jakarta.persistence.criteria.CriteriaBuilder cb,
                                            jakarta.persistence.criteria.Path<String> path,
                                            String value) {
        if (hasText(value)) {
            predicates.add(cb.equal(cb.upper(path), value.trim().toUpperCase()));
        }
    }

    /** Dates are interpreted in UTC, matching how {@code created_at} is stored. */
    private static Instant startOfDay(LocalDate date) {
        return date.atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC);
    }

    /**
     * Without this, a search for {@code "%"} matches every company regardless of the
     * other filters.
     */
    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
