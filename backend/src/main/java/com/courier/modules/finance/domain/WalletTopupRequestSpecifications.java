package com.courier.modules.finance.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Criteria API predicates for the top-up request search. All combine with {@code AND}. */
public final class WalletTopupRequestSpecifications {

    private WalletTopupRequestSpecifications() {
    }

    public static Specification<WalletTopupRequest> matching(WalletTopupRequestCriteria criteria) {
        WalletTopupRequestCriteria safe =
                criteria == null ? WalletTopupRequestCriteria.none() : criteria;

        return (root, query, cb) -> {
            // Fails closed: no company scope means no rows, never "every row".
            if (safe.companyId() == null) {
                return cb.disjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), safe.companyId()));

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
