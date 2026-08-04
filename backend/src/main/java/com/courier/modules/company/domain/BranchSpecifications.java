package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Criteria API predicates for the branch search. All combine with {@code AND}; an absent
 * filter contributes nothing.
 *
 * <p>Not the company boundary — the Hibernate filter is. {@link BranchCriteria#companyId()}
 * narrows a super-admin listing; {@link BranchCriteria#branchIds()} pins a non-admin to
 * the branches they may see. An <b>empty</b> branchIds set means "no branches", which is
 * the correct answer for a user assigned to none.
 */
public final class BranchSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private BranchSpecifications() {
    }

    public static Specification<Branch> matching(BranchCriteria criteria) {
        BranchCriteria safe = criteria == null ? BranchCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }
            if (safe.branchTypes() != null && !safe.branchTypes().isEmpty()) {
                predicates.add(root.get("branchType").in(safe.branchTypes()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (safe.managerId() != null) {
                predicates.add(cb.equal(root.get("managerId"), safe.managerId()));
            }
            addEqualsIgnoreCase(predicates, cb, root.get("city"), safe.city());
            addEqualsIgnoreCase(predicates, cb, root.get("state"), safe.state());
            if (hasText(safe.postalCode())) {
                predicates.add(cb.equal(root.get("postalCode"), safe.postalCode().trim()));
            }
            addBoolean(predicates, cb, root.get("allowBooking"), safe.allowBooking());
            addBoolean(predicates, cb, root.get("allowDelivery"), safe.allowDelivery());
            addBoolean(predicates, cb, root.get("allowPickup"), safe.allowPickup());

            if (safe.branchIds() != null) {
                // Explicit scope. Empty -> matches nothing, which is intended.
                predicates.add(safe.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(safe.branchIds()));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("branchCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("branchName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("city")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("postalCode")), pattern, LIKE_ESCAPE)));
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

    private static void addBoolean(List<Predicate> predicates,
                                   jakarta.persistence.criteria.CriteriaBuilder cb,
                                   jakarta.persistence.criteria.Path<Boolean> path,
                                   Boolean value) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
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
