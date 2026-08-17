package com.courier.modules.crossing.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Criteria API predicates for the crossing search. All combine with {@code AND}. */
public final class CrossingDetailSpecifications {

    private CrossingDetailSpecifications() {
    }

    public static Specification<CrossingDetail> matching(CrossingDetailCriteria criteria) {
        CrossingDetailCriteria safe = criteria == null ? CrossingDetailCriteria.none() : criteria;

        return (root, query, cb) -> {
            // Fails closed: no company scope means no rows, never "every row".
            if (safe.companyId() == null) {
                return cb.disjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), safe.companyId()));

            if (safe.shipmentId() != null) {
                predicates.add(cb.equal(root.get("shipmentId"), safe.shipmentId()));
            }
            if (safe.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), safe.branchId()));
            }
            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
