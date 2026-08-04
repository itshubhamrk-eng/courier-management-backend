package com.courier.modules.company.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.courier.shared.exception.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user, from the company-administration bounded context.
 *
 * <p><b>This maps the same {@code users} table as {@code auth.User}.</b> The table is a
 * shared kernel: the auth context authenticates the person, the company context
 * administers them (HR profile, roles, branch, hub). Each context has its own model of
 * the shared row — a recognised pattern, and the least-plumbing way to honour "extend
 * the users table" without either a second table that drifts or company reaching into
 * auth's repository. Neither entity maps the other's private columns:
 * <ul>
 *   <li>auth maps {@code roles} (the JWT-authority element collection) and the token
 *       bookkeeping; this entity does not.</li>
 *   <li>this maps the HR/profile fields added in {@code V7}; auth does not.</li>
 * </ul>
 * Both map the columns they share — email, status, the audit/lock fields — with
 * identical types, so {@code ddl-auto: validate} passes for both.
 *
 * <p>Roles are held in {@link UserRole} (a join to {@code company_roles}), not here — a
 * user may have several, and they carry permissions. The auth {@code Role} enum
 * collection is a separate, JWT-only concern.
 */
@Entity(name = "CompanyUser")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_company_email", columnNames = {"company_id", "email"}),
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_company_employee_code",
                        columnNames = {"company_id", "employee_code"})
        },
        indexes = {
                @Index(name = "idx_users_company_status", columnList = "company_id, status"),
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_company_branch", columnList = "company_id, branch_id"),
                @Index(name = "idx_users_company_hub", columnList = "company_id, hub_id")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class User extends CompanyOwnedEntity {

    // --- identity -----------------------------------------------------------

    /** Stable HR key, unique per company, uppercased, immutable. */
    @Column(name = "employee_code", updatable = false, length = 50)
    private String employeeCode;

    /** Free-form external HR/payroll id. Not a key. */
    @Column(name = "employee_id", length = 50)
    private String employeeId;

    // --- name ---------------------------------------------------------------

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 150)
    private String displayName;

    // --- contact ------------------------------------------------------------

    /** Login address, unique per company. Always stored lowercased. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Login handle, **globally** unique. Always stored lowercased. */
    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "alternate_mobile", length = 20)
    private String alternateMobile;

    /**
     * BCrypt hash. {@code @JsonIgnore} is a backstop — no DTO exposes the entity — and
     * the hash must never reach a log or an audit payload.
     */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // --- HR profile ---------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "gender", length = 20)
    @Builder.Default
    private Gender gender = Gender.UNSPECIFIED;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    /** Self-reference to another user in the same company. Nullable. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "reporting_manager_id", columnDefinition = "BINARY(16)")
    private UUID reportingManagerId;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Column(name = "remarks", length = 500)
    private String remarks;

    // --- placement ----------------------------------------------------------
    // Single columns, not join tables: a user belongs to at most one branch and one
    // hub, so the constraint is structural. No FK yet — the branch and hub modules do
    // not exist, the same reason the users.company_id FK is still deferred.

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hub_id", columnDefinition = "BINARY(16)")
    private UUID hubId;

    // --- state --------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING;

    /**
     * Admin hard-lock, distinct from auth's automatic {@code locked_until} (which lapses
     * on its own after failed logins). When true the status is {@code LOCKED} and stays
     * there until an admin unlocks — a scheduled job never clears it.
     */
    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    /** Read-only here; written by the auth login path. Surfaced for the admin UI. */
    @Column(name = "last_login_at", insertable = false, updatable = false)
    private Instant lastLogin;

    /** Shares auth's {@code failed_attempts} column. */
    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedLoginCount = 0;

    // ---------------------------------------------------------------- behaviour

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static String normaliseUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normaliseEmployeeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim().toUpperCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String fullName() {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, firstName);
        appendPart(sb, middleName);
        appendPart(sb, lastName);
        return sb.isEmpty() ? email : sb.toString();
    }

    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? fullName() : displayName;
    }

    public boolean isOperational() {
        return status != null && status.operational() && !locked;
    }

    // --- lifecycle transitions ---------------------------------------------

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.locked = false;
    }

    /** Admin off-switch. Reversible with {@link #activate()}. */
    public void deactivate() {
        this.status = UserStatus.DISABLED;
    }

    public void lock() {
        this.locked = true;
        this.status = UserStatus.LOCKED;
    }

    /**
     * Clears the admin lock. Also resets the failed-login counter, since an unlock is a
     * deliberate "this account is fine again" and leaving the counter primed would
     * re-lock it on the next mistake.
     */
    public void unlock() {
        this.locked = false;
        this.failedLoginCount = 0;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void assignBranch(UUID branchId) {
        this.branchId = branchId;
    }

    public void assignHub(UUID hubId) {
        this.hubId = hubId;
    }

    public void setNewPassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }

    /** Normalises the matched/derived fields. Called before every save. */
    public void applyInvariants() {
        this.email = normaliseEmail(email);
        this.username = normaliseUsername(username);
        this.employeeCode = normaliseEmployeeCode(employeeCode);
        this.firstName = trimToNull(firstName);
        this.middleName = trimToNull(middleName);
        this.lastName = trimToNull(lastName);
        if (this.gender == null) {
            this.gender = Gender.UNSPECIFIED;
        }
        if (this.status == null) {
            this.status = UserStatus.PENDING;
        }
        if (this.email == null || this.email.isBlank()) {
            throw new BusinessRuleException("A user must have an email address.");
        }
        if (reportingManagerId != null && reportingManagerId.equals(getId())) {
            throw new BusinessRuleException("A user cannot report to themselves.");
        }
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
