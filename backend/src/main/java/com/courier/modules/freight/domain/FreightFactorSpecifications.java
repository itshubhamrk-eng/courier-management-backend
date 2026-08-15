package com.courier.modules.freight.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the freight factor search. Company scoping is the
 * Hibernate filter's job, not this class's, same split {@code RateSpecifications} draws.
 */
public final class FreightFactorSpecifications {

    private FreightFactorSpecifications() {
    }

    public static Specification<FreightFactor> matching(FreightFactorCriteria criteria) {
        FreightFactorCriteria safe = criteria == null ? FreightFactorCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
