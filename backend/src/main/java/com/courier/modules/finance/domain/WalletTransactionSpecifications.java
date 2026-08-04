package com.courier.modules.finance.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API predicates for the ledger search. All combine with {@code AND}; an absent
 * filter contributes nothing.
 *
 * <p>Not the company boundary — the Hibernate filter is. What this <em>does</em> enforce is
 * the wallet boundary: {@link WalletTransactionCriteria#walletId()} is set by the service,
 * and if it is somehow absent the specification matches nothing rather than returning the
 * whole company's ledger. Failing closed is the only safe default for a money query.
 */
public final class WalletTransactionSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private WalletTransactionSpecifications() {
    }

    public static Specification<WalletTransaction> matching(WalletTransactionCriteria criteria) {
        WalletTransactionCriteria safe =
                criteria == null ? WalletTransactionCriteria.none() : criteria;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Fails closed: no wallet scope means no rows, never "every row".
            if (safe.walletId() == null) {
                return cb.disjunction();
            }
            predicates.add(cb.equal(root.get("walletId"), safe.walletId()));

            if (safe.companyId() != null) {
                predicates.add(cb.equal(root.get("companyId"), safe.companyId()));
            }
            if (isNotEmpty(safe.transactionTypes())) {
                predicates.add(root.get("transactionType").in(safe.transactionTypes()));
            }
            if (isNotEmpty(safe.subTransactionTypes())) {
                predicates.add(root.get("subTransactionType").in(safe.subTransactionTypes()));
            }
            if (isNotEmpty(safe.referenceTypes())) {
                predicates.add(root.get("referenceType").in(safe.referenceTypes()));
            }
            if (isNotEmpty(safe.paymentStatuses())) {
                predicates.add(root.get("paymentStatus").in(safe.paymentStatuses()));
            }

            addExact(predicates, cb, root.get("referenceId"), safe.referenceId());
            addExact(predicates, cb, root.get("transactionNo"), safe.transactionNo());
            addExact(predicates, cb, root.get("paymentReference"), safe.paymentReference());

            addFrom(predicates, cb, root.get("createdAt"), safe.from());
            addBefore(predicates, cb, root.get("createdAt"), safe.to());

            if (safe.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), safe.minAmount()));
            }
            if (safe.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), safe.maxAmount()));
            }

            if (hasText(safe.search())) {
                String pattern = "%" + escapeLike(safe.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("transactionNo")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("remarks")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("referenceId")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("paymentReference")), pattern, LIKE_ESCAPE)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addExact(List<Predicate> predicates, CriteriaBuilder cb,
                                 Path<String> path, String value) {
        if (hasText(value)) {
            predicates.add(cb.equal(path, value.trim()));
        }
    }

    private static void addFrom(List<Predicate> predicates, CriteriaBuilder cb,
                                Path<Instant> path, Instant value) {
        if (value != null) {
            predicates.add(cb.greaterThanOrEqualTo(path, value));
        }
    }

    private static void addBefore(List<Predicate> predicates, CriteriaBuilder cb,
                                  Path<Instant> path, Instant value) {
        if (value != null) {
            predicates.add(cb.lessThan(path, value));
        }
    }

    private static boolean isNotEmpty(java.util.Set<?> values) {
        return values != null && !values.isEmpty();
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
