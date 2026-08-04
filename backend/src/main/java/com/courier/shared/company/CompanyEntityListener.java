package com.courier.shared.company;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.CompanyIsolationException;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Stamps and defends the company discriminator at the JPA lifecycle level, so that
 * isolation does not depend on every service remembering to do it.
 */
@Slf4j
public class CompanyEntityListener {

    /**
     * Assigns the current company to a new row. If the entity already carries a
     * company it must match the bound one — a mismatch means code is trying to
     * insert into another company's data.
     */
    @PrePersist
    public void onPrePersist(CompanyOwnedEntity entity) {
        UUID current = CompanyContext.getCompanyId().orElse(null);

        if (entity.getCompanyId() == null) {
            if (current == null) {
                throw new CompanyIsolationException(
                        "Cannot persist %s: no company bound to the current request"
                                .formatted(entity.getClass().getSimpleName()));
            }
            entity.setCompanyId(current);
            return;
        }

        if (current != null && !current.equals(entity.getCompanyId())) {
            throw new CompanyIsolationException(
                    "Cannot persist %s for company %s while bound to company %s"
                            .formatted(entity.getClass().getSimpleName(), entity.getCompanyId(), current));
        }
    }

    /**
     * Last line of defence on writes. The Hibernate filter should already have made
     * another company's row unreachable; if one reaches an update, something bypassed
     * the filter and we fail loudly rather than corrupt data.
     */
    @PreUpdate
    public void onPreUpdate(CompanyOwnedEntity entity) {
        assertSameCompany(entity, "update");
    }

    @PreRemove
    public void onPreRemove(CompanyOwnedEntity entity) {
        assertSameCompany(entity, "remove");
    }

    private void assertSameCompany(CompanyOwnedEntity entity, String operation) {
        UUID current = CompanyContext.getCompanyId().orElse(null);
        if (current == null) {
            // Platform-level maintenance (migrations, jobs) runs without a binding.
            return;
        }
        if (!current.equals(entity.getCompanyId())) {
            log.error("Cross-company {} blocked: entity {} belongs to company {}, request bound to {}",
                    operation, entity.getClass().getSimpleName(), entity.getCompanyId(), current);
            throw new CompanyIsolationException(
                    "Cross-company %s blocked for %s".formatted(operation, entity.getClass().getSimpleName()));
        }
    }
}
