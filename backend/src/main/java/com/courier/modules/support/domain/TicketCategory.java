package com.courier.modules.support.domain;

import com.courier.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * A top-level ticket category. Global, not company-owned — one company's "Payment/Billing"
 * is the same concept as another's, same reasoning any shared catalogue uses. Managed by
 * {@code SUPER_ADMIN} only; every company reads the same list.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_categories_name", columnNames = "name"))
@SQLRestriction("deleted = false")
public class TicketCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;
}
