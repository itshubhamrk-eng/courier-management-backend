package com.courier.modules.master.domain;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates shared by every master-data search. All combine with
 * {@code AND}; an absent filter contributes nothing.
 *
 * <p>Not the company boundary — the Hibernate filter is. {@link MasterDataCriteria#companyId()}
 * narrows a super-admin listing; {@link MasterDataCriteria#ids()} pins a caller to an
 * explicit scope.
 *
 * <p>The free-text search runs over {@code code}, {@code name} and {@code description},
 * which every master row has. LIKE wildcards in the term are escaped, so a search for
 * {@code 100%} does not match everything.
 */
public final class MasterDataSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private MasterDataSpecifications() {
    }

    public static <E extends MasterDataEntity> Specification<E> matching(MasterDataCriteria criteria) {
        MasterDataCriteria safe = criteria == null ? MasterDataCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }
            if (safe.ids() != null) {
                // Explicit scope. Empty -> matches nothing, which is intended.
                predicates.add(safe.ids().isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(safe.ids()));
            }

            safe.equalities().forEach((attribute, value) -> {
                Path<Object> path = root.get(attribute);
                if (value instanceof String text) {
                    // Codes and zones are stored uppercase but typed however the caller
                    // liked, so compare case-insensitively rather than rejecting "a".
                    predicates.add(cb.equal(cb.upper(path.as(String.class)),
                            text.trim().toUpperCase()));
                } else {
                    predicates.add(cb.equal(path, value));
                }
            });

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE)));
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
