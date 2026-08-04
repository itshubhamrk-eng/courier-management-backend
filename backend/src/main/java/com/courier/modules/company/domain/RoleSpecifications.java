package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the role search.
 *
 * <p>All predicates combine with {@code AND}; an absent filter contributes nothing.
 *
 * <p><b>These predicates are not the company boundary.</b> Isolation comes from the
 * Hibernate filter, which is applied whenever a company is bound. {@link #matching}'s
 * optional {@code companyId} narrows a platform-wide listing; it must never be relied on
 * to keep one company out of another's data.
 */
public final class RoleSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private RoleSpecifications() {
    }

    public static Specification<CompanyRole> matching(RoleCriteria criteria) {
        RoleCriteria safe = criteria == null ? RoleCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }

            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }

            if (safe.roleTypes() != null && !safe.roleTypes().isEmpty()) {
                predicates.add(root.get("roleType").in(safe.roleTypes()));
            }

            if (safe.systemRole() != null) {
                predicates.add(cb.equal(root.get("systemRole"), safe.systemRole()));
            }

            if (safe.defaultRole() != null) {
                predicates.add(cb.equal(root.get("defaultRole"), safe.defaultRole()));
            }

            if (hasText(safe.permissionCode())) {
                // Grants live in role_permissions now, so this is an EXISTS subquery
                // rather than a collection join — which also avoids the duplicate rows a
                // join would produce and keeps the count query honest.
                jakarta.persistence.criteria.Subquery<java.util.UUID> grants =
                        query.subquery(java.util.UUID.class);
                jakarta.persistence.criteria.Root<RolePermission> grant =
                        grants.from(RolePermission.class);
                grants.select(grant.get("roleId"))
                        .where(cb.and(
                                cb.equal(grant.get("roleId"), root.get("id")),
                                cb.equal(grant.get("permissionCode"),
                                        Permission.normaliseCode(safe.permissionCode())),
                                cb.isFalse(grant.get("deleted"))));
                predicates.add(cb.exists(grants));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("roleCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("roleName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Role codes legitimately contain underscores, so wildcards are escaped rather than
     * rejected — otherwise a search for {@code BOOKING_OPERATOR} would match
     * {@code BOOKINGXOPERATOR}, and a search for {@code %} would return everything.
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
