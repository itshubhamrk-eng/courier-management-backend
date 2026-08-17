package com.courier.modules.support.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Criteria API predicates for the ticket search. All top-level filters combine with {@code AND}. */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> matching(TicketCriteria criteria) {
        TicketCriteria safe = criteria == null ? TicketCriteria.none() : criteria;

        return (root, query, cb) -> {
            // Fails closed: no company scope means no rows, never "every row" — unless the
            // service has explicitly confirmed a SUPER_ADMIN cross-tenant request.
            if (safe.companyId() == null && !safe.crossTenant()) {
                return cb.disjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }

            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }
            if (safe.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), safe.priority()));
            }
            if (safe.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), safe.categoryId()));
            }
            if (safe.subCategoryId() != null) {
                predicates.add(cb.equal(root.get("subCategoryId"), safe.subCategoryId()));
            }
            if (safe.relatedBranchId() != null) {
                predicates.add(cb.equal(root.get("relatedBranchId"), safe.relatedBranchId()));
            }
            if (safe.assigneeUserId() != null) {
                predicates.add(cb.equal(root.get("assigneeUserId"), safe.assigneeUserId()));
            }
            if (safe.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), safe.createdFrom()));
            }
            if (safe.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), safe.createdTo()));
            }
            if (safe.search() != null && !safe.search().isBlank()) {
                String like = "%" + safe.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ticketNumber")), like),
                        cb.like(cb.lower(root.get("subject")), like)));
            }

            // Non-admin scoping: requester OR assignee OR (own branch), whichever apply.
            if (safe.requesterOrAssignee() != null) {
                List<Predicate> scope = new ArrayList<>();
                scope.add(cb.equal(root.get("createdByUserId"), safe.requesterOrAssignee()));
                scope.add(cb.equal(root.get("assigneeUserId"), safe.requesterOrAssignee()));
                if (safe.visibleBranchId() != null) {
                    scope.add(cb.equal(root.get("relatedBranchId"), safe.visibleBranchId()));
                }
                predicates.add(cb.or(scope.toArray(Predicate[]::new)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
