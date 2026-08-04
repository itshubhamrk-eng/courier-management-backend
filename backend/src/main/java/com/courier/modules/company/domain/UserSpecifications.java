package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the user search. All combine with {@code AND}; an absent
 * filter contributes nothing.
 *
 * <p>These predicates are not the company boundary — isolation comes from the Hibernate
 * filter. {@link UserCriteria#companyId()} narrows a super-admin listing; it must never be
 * relied on to keep one company out of another's data.
 */
public final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    public static Specification<User> matching(UserCriteria criteria) {
        UserCriteria safe = criteria == null ? UserCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (safe.locked() != null) {
                predicates.add(cb.equal(root.get("locked"), safe.locked()));
            }
            if (safe.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), safe.branchId()));
            }
            if (safe.hubId() != null) {
                predicates.add(cb.equal(root.get("hubId"), safe.hubId()));
            }
            addEqualsIgnoreCase(predicates, cb, root.get("department"), safe.department());
            addEqualsIgnoreCase(predicates, cb, root.get("designation"), safe.designation());

            if (safe.joinedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("joiningDate"), safe.joinedFrom()));
            }
            if (safe.joinedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("joiningDate"), safe.joinedTo()));
            }

            if (hasText(safe.roleCode())) {
                // Role assignments live in user_company_roles, so this is an EXISTS
                // subquery rather than a join — no duplicate rows, and the count query
                // stays honest.
                jakarta.persistence.criteria.Subquery<java.util.UUID> assigned =
                        query.subquery(java.util.UUID.class);
                jakarta.persistence.criteria.Root<UserRole> ur = assigned.from(UserRole.class);
                assigned.select(ur.get("userId"))
                        .where(cb.and(
                                cb.equal(ur.get("userId"), root.get("id")),
                                cb.equal(ur.get("roleCode"),
                                        safe.roleCode().trim().toUpperCase().replace(' ', '_')),
                                cb.isFalse(ur.get("deleted"))));
                predicates.add(cb.exists(assigned));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("displayName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("employeeCode")), pattern, LIKE_ESCAPE),
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

    private static String escapeLike(String raw) {
        return raw.replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "\\")
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
