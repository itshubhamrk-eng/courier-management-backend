package com.courier.modules.followup.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Criteria API predicates for the follow-up search. All top-level filters combine with
 *  {@code AND}, same shape as {@code TicketSpecifications}. */
public final class FollowUpSpecifications {

    private FollowUpSpecifications() {
    }

    public static Specification<FollowUp> matching(FollowUpCriteria criteria) {
        FollowUpCriteria safe = criteria == null ? FollowUpCriteria.none() : criteria;

        return (root, query, cb) -> {
            // Fails closed: no company scope means no rows, never "every row".
            if (safe.companyId() == null) {
                return cb.disjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), safe.companyId()));

            if (safe.status() != null) {
                predicates.add(cb.equal(root.get("status"), safe.status()));
            }
            if (safe.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), safe.priority()));
            }
            if (safe.type() != null) {
                predicates.add(cb.equal(root.get("followUpType"), safe.type()));
            }
            if (safe.assignedUserId() != null) {
                predicates.add(cb.equal(root.get("assignedUserId"), safe.assignedUserId()));
            }
            if (safe.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), safe.customerId()));
            }
            if (safe.shipmentId() != null) {
                predicates.add(cb.equal(root.get("shipmentId"), safe.shipmentId()));
            }
            if (safe.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), safe.branchId()));
            }
            if (safe.dueDate() != null) {
                LocalDate day = safe.dueDate().atZone(ZoneOffset.UTC).toLocalDate();
                Instant startOfDay = day.atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant startOfNextDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), startOfDay));
                predicates.add(cb.lessThan(root.get("dueDate"), startOfNextDay));
            }
            if (safe.overdue()) {
                predicates.add(cb.lessThan(root.get("dueDate"), Instant.now()));
                predicates.add(root.get("status").in(FollowUpStatus.COMPLETED, FollowUpStatus.CANCELLED).not());
            }
            if (safe.search() != null && !safe.search().isBlank()) {
                String like = "%" + safe.search().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), like));
            }

            // Non-admin scoping: own branch OR requester OR assignee, whichever apply.
            if (safe.visibleBranchId() != null || safe.requesterOrAssignee() != null) {
                List<Predicate> scope = new ArrayList<>();
                if (safe.visibleBranchId() != null) {
                    scope.add(cb.equal(root.get("branchId"), safe.visibleBranchId()));
                }
                if (safe.requesterOrAssignee() != null) {
                    scope.add(cb.equal(root.get("createdBy"), safe.requesterOrAssignee()));
                    scope.add(cb.equal(root.get("assignedUserId"), safe.requesterOrAssignee()));
                }
                predicates.add(cb.or(scope.toArray(Predicate[]::new)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
