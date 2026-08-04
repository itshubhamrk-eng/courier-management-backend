package com.courier.modules.customer.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the customer search. All combine with {@code AND}; an
 * absent filter contributes nothing. Company scoping is the Hibernate filter's job, not
 * this class's — a {@code SUPER_ADMIN} never reaches a customer at all (see the
 * invariant in {@code MEMORY/AI_CONTEXT.md}), so there is no cross-company override to
 * express here the way {@code BranchSpecifications} needs one.
 */
public final class CustomerSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private CustomerSpecifications() {
    }

    public static Specification<Customer> matching(CustomerCriteria criteria) {
        CustomerCriteria safe = criteria == null ? CustomerCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.customerTypes() != null && !safe.customerTypes().isEmpty()) {
                predicates.add(root.get("customerType").in(safe.customerTypes()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("customerCode")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("firstName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("companyName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("mobile")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE)));
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
