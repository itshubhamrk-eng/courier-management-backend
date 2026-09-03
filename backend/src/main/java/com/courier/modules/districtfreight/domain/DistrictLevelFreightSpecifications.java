package com.courier.modules.districtfreight.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the District Level Freight search. All combine with
 * {@code AND}; an absent filter contributes nothing. Company scoping is the Hibernate
 * filter's job, not this class's.
 */
public final class DistrictLevelFreightSpecifications {

    private DistrictLevelFreightSpecifications() {
    }

    public static Specification<DistrictLevelFreight> matching(DistrictLevelFreightCriteria criteria) {
        DistrictLevelFreightCriteria safe = criteria == null ? DistrictLevelFreightCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (safe.branchIds() != null && !safe.branchIds().isEmpty()) {
                predicates.add(root.get("branchId").in(safe.branchIds()));
            }
            if (safe.districtIds() != null && !safe.districtIds().isEmpty()) {
                predicates.add(root.get("districtId").in(safe.districtIds()));
            }
            if (safe.statuses() != null && !safe.statuses().isEmpty()) {
                predicates.add(root.get("status").in(safe.statuses()));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
