package com.courier.modules.support.domain;

import com.courier.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** A sub-category nested under one {@link TicketCategory}. Global, same as its parent. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ticket_sub_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_sub_categories_name",
                columnNames = {"category_id", "name"}),
        indexes = @Index(name = "idx_ticket_sub_categories_category", columnList = "category_id"))
@SQLRestriction("deleted = false")
public class TicketSubCategory extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "category_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;
}
