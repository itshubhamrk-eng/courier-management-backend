package com.courier.modules.master.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The head every master-data row shares: a code, a display name, an optional description,
 * a status and a sort key.
 *
 * <p>This is what lets one {@code AbstractMasterDataService} serve twelve lists. Anything
 * a particular list needs beyond it — a parent id, a weight range, a branch pair — is
 * declared on the concrete entity.
 *
 * <p><b>The code is immutable.</b> Shipments, manifests and rate cards will store it, and
 * a code that can be edited turns every historical record that quotes it into a lie. The
 * name is editable, because a name is a label and nothing keys off it.
 *
 * <p>Concrete subclasses must repeat
 * {@code @Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")}
 * on themselves — Hibernate does not inherit {@code @Filter} through a
 * {@code @MappedSuperclass}, and a silently unfiltered entity is a cross-company leak.
 *
 * <p>No Lombok builder here on purpose: {@code @SuperBuilder} would have to be threaded
 * through {@code CompanyOwnedEntity} and {@code BaseEntity}, which every other module
 * builds without. Services construct with the no-arg constructor and setters.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class MasterDataEntity extends CompanyOwnedEntity {

    /** Stable identifier, uppercased, unique within the company. Never updatable. */
    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private MasterStatus status = MasterStatus.ACTIVE;

    /** Ascending sort key for pickers. Ties break on name, so duplicates are harmless. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    // ---------------------------------------------------------------- behaviour

    /**
     * Uppercases and underscores a code. Applied on create so that "pune main",
     * "Pune Main" and "PUNE_MAIN" cannot become three rows that a human reads as one.
     */
    public static String normaliseCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase().replace(' ', '_');
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }

    public void activate() {
        this.status = MasterStatus.ACTIVE;
    }

    /** Withdraws the row from new work. Existing references keep resolving. */
    public void deactivate() {
        this.status = MasterStatus.INACTIVE;
    }

    /**
     * Normalises and validates the shared head, then the subclass's own fields.
     *
     * <p>Called by the service before every save. Subclasses override
     * {@link #applySpecificInvariants()} rather than this, so the shared normalisation can
     * never be skipped by forgetting a {@code super} call.
     */
    public final void applyInvariants() {
        this.code = normaliseCode(code);
        this.name = name == null ? null : name.trim();
        this.description = blankToNull(description);
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("A code is required.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("A name is required.");
        }
        if (status == null) {
            this.status = MasterStatus.ACTIVE;
        }
        if (displayOrder == null) {
            this.displayOrder = 0;
        }
        applySpecificInvariants();
    }

    /** Rules that belong to one list only. Default: none. */
    protected void applySpecificInvariants() {
    }

    protected static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected static String upperOrNull(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}
