package com.courier.shared.domain;

import com.courier.shared.company.CompanyEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Base class for every entity owned by a company.
 *
 * <p>Isolation is enforced in two directions:
 * <ul>
 *   <li><b>Write</b> — {@link CompanyEntityListener} stamps {@code companyId} from the
 *       {@code CompanyContext} on persist and rejects cross-company updates. Feature
 *       code must never set {@code companyId} by hand.</li>
 *   <li><b>Read</b> — the {@code companyFilter} Hibernate filter appends
 *       {@code company_id = :companyId} to every query. It is enabled per request by
 *       {@code CompanyFilterAspect}.</li>
 * </ul>
 *
 * <p><b>Required of every concrete subclass:</b> repeat the filter annotation on the
 * entity class itself —
 * {@code @Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")}.
 * Hibernate does not reliably inherit {@code @Filter} from a {@code @MappedSuperclass},
 * and a silently un-filtered entity is a cross-company data leak. The {@code @FilterDef}
 * below only registers the filter's name and parameter type; it does not apply it.
 *
 * <p>The physical column is {@code company_id} on every company-owned table, matching
 * the Java field name — the column was originally {@code tenant_id} and was renamed in
 * a dedicated migration once "tenant" left the vocabulary everywhere else. Joined to
 * Java in exactly this one place.
 *
 * <p>See {@code MEMORY/adr/ADR-001.md}.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(CompanyEntityListener.class)
@FilterDef(
        name = CompanyOwnedEntity.COMPANY_FILTER,
        parameters = @ParamDef(name = CompanyOwnedEntity.COMPANY_PARAM, type = UUID.class)
)
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public abstract class CompanyOwnedEntity extends BaseEntity {

    public static final String COMPANY_FILTER = "companyFilter";
    public static final String COMPANY_PARAM = "companyId";

    /**
     * Owning company. Not updatable: a row never moves between companies.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "company_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID companyId;
}
