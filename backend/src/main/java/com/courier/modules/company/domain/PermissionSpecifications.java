package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Criteria API predicates for the permission catalogue. All combine with {@code AND};
 * an absent filter contributes nothing.
 *
 * @see PermissionCriteria
 */
public final class PermissionSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private PermissionSpecifications() {
    }

    public static Specification<Permission> matching(PermissionCriteria criteria) {
        PermissionCriteria safe = criteria == null ? PermissionCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            addIn(predicates, root.get("module"), safe.modules());
            addIn(predicates, root.get("action"), safe.actions());

            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }
            if (safe.systemPermission() != null) {
                predicates.add(cb.equal(root.get("systemPermission"), safe.systemPermission()));
            }
            if (safe.resource() != null && !safe.resource().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("resource")),
                        safe.resource().trim().toLowerCase()));
            }
            if (Boolean.TRUE.equals(safe.planGatedOnly())) {
                predicates.add(cb.isNotNull(root.get("requiredFeatureFlag")));
            }
            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("permissionCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("permissionName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("resource")), pattern, LIKE_ESCAPE)));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static <T> void addIn(List<Predicate> predicates,
                                  jakarta.persistence.criteria.Path<T> path,
                                  Set<T> values) {
        if (values != null && !values.isEmpty()) {
            predicates.add(path.in(values));
        }
    }

    /**
     * Permission codes are full of underscores, so wildcards are escaped rather than
     * rejected — a search for {@code SHIPMENT_CREATE} must not also match
     * {@code SHIPMENTXCREATE}, and a search for {@code %} must not return the catalogue.
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
