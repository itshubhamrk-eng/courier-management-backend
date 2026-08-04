package com.courier.modules.company.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
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
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * A user holds a company role. The many-to-many between {@link User} and
 * {@link CompanyRole}, as its own entity so each assignment carries who granted it and
 * when.
 *
 * <p><b>Maps {@code user_company_roles}, not {@code user_roles}.</b> That other table is
 * auth's element collection of the JWT-authority {@code Role} enum and is a different
 * thing entirely — this join points at {@code company_roles}, the permissioned roles a
 * company manages. Keeping them apart is why assigning a company role here does not (yet)
 * change the JWT; wiring authorisation onto these grants is the deferred follow-up.
 *
 * <p>Company-owned. {@code roleCode} is denormalised from the role so a user's role list
 * reads without a join to {@code company_roles}; the code is immutable, so it cannot go
 * stale.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "user_company_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_company_roles_user_role",
                columnNames = {"company_id", "user_id", "role_id"}),
        indexes = {
                @Index(name = "idx_user_company_roles_user", columnList = "company_id, user_id"),
                @Index(name = "idx_user_company_roles_role", columnList = "company_id, role_id")
        })
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class UserRole extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "role_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "role_code", nullable = false, updatable = false, length = 50)
    private String roleCode;

    public static UserRole assign(UUID userId, CompanyRole role) {
        return UserRole.builder()
                .userId(userId)
                .roleId(role.getId())
                .roleCode(role.getRoleCode())
                .build();
    }
}
