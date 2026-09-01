package com.courier.modules.communication.domain;

import org.springframework.data.jpa.domain.Specification;

/** The Hibernate company filter already scopes every query to the caller's company — this
 *  only ever adds the optional search filters on top, the same split every other
 *  Specification class in this project keeps. */
public final class CommunicationLogSpecifications {

    private CommunicationLogSpecifications() {
    }

    public static Specification<CommunicationLog> matching(CommunicationLogCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (criteria.shipmentId() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("shipmentId"), criteria.shipmentId()));
            }
            if (criteria.customerId() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("customerId"), criteria.customerId()));
            }
            if (criteria.eventType() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("eventType"), criteria.eventType()));
            }
            if (criteria.channel() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("channel"), criteria.channel()));
            }
            if (criteria.status() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), criteria.status()));
            }
            return predicates;
        };
    }
}
