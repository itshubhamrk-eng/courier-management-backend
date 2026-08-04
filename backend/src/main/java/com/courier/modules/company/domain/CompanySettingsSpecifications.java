package com.courier.modules.company.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Criteria predicates over company settings.
 *
 * <p>A single-row-per-company config has no per-company search surface, so these exist
 * for the one legitimate cross-company use: a {@code SUPER_ADMIN} report such as "every
 * company with wallet enabled". All predicates combine with {@code AND}; an absent filter
 * contributes nothing.
 */
public final class CompanySettingsSpecifications {

    private CompanySettingsSpecifications() {
    }

    public static Specification<CompanySettings> forCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<CompanySettings> matching(Boolean walletEnabled,
                                                          Boolean codEnabled,
                                                          ThemePreference theme) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (walletEnabled != null) {
                predicates.add(cb.equal(root.get("walletEnabled"), walletEnabled));
            }
            if (codEnabled != null) {
                predicates.add(cb.equal(root.get("codEnabled"), codEnabled));
            }
            if (theme != null) {
                predicates.add(cb.equal(root.get("theme"), theme));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
