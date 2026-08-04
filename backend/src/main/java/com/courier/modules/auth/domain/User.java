package com.courier.modules.auth.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
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

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A user account, owned by exactly one company.
 *
 * <p>Email is unique <em>per company</em>, not globally: the same person may hold an
 * account at two different courier companies. That is why every lookup binds the
 * company first — see {@code MEMORY/modules/auth.md}.
 *
 * <p>This entity holds account state but no policy. Decisions such as "how many
 * failures before locking" live in {@code AuthProperties}; the entity only exposes
 * the state transitions those decisions drive.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_company_email", columnNames = {"company_id", "email"}),
        indexes = {
                @Index(name = "idx_users_company_status", columnList = "company_id, status"),
                @Index(name = "idx_users_email", columnList = "email")
        })
// Repeated deliberately: Hibernate does not inherit @Filter from a @MappedSuperclass,
// and an unfiltered company-owned entity is a cross-company data leak.
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class User extends CompanyOwnedEntity {

    /** Always stored lowercased — see {@link #normaliseEmail}. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * BCrypt hash. {@code @JsonIgnore} is a backstop only; no DTO ever exposes the
     * entity directly, and the hash must never reach a log or an audit payload.
     */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    /**
     * The branch/hub this account is staffed at, if any — the same {@code branch_id}/
     * {@code hub_id} columns {@code company.User} maps on the shared {@code users}
     * table (see {@code MEMORY/modules/user.md}'s "shared kernel" note). Read here so
     * a login response can tell a booking desk its own branch without a second
     * cross-module call; auth never writes either column.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hub_id", columnDefinition = "BINARY(16)")
    private UUID hubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /**
     * Eager because every authentication needs the roles immediately, and the set
     * is tiny. A lazy collection here would mean a guaranteed second query plus a
     * {@code LazyInitializationException} risk once the session closes
     * ({@code open-in-view} is off).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_user_roles_user")))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Drives the optional expiry policy, and is reset on every password change. */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    /** Null when not locked. A past value means the lock has lapsed. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    // ------------------------------------------------------------------ behaviour

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public String displayName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? email : combined;
    }

    /**
     * @return true while a failed-login lock is still in force. A lapsed
     *         {@code lockedUntil} reports false, which is what makes the lock
     *         self-clearing without a scheduled job.
     */
    public boolean isTemporarilyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED || status == UserStatus.LOCKED;
    }

    /**
     * @param maxAge zero or null disables expiry, which is the default. NIST
     *               SP 800-63B advises against forced rotation; the mechanism
     *               exists for compliance regimes that still mandate it.
     */
    public boolean isPasswordExpired(Duration maxAge) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return false;
        }
        Instant changed = passwordChangedAt != null ? passwordChangedAt : getCreatedAt();
        return changed != null && changed.plus(maxAge).isBefore(Instant.now());
    }

    /** Records a failed attempt and locks the account once the threshold is hit. */
    public void registerFailedAttempt(int threshold, Duration lockDuration) {
        this.failedAttempts++;
        if (this.failedAttempts >= threshold) {
            this.lockedUntil = Instant.now().plus(lockDuration);
        }
    }

    /** Clears counters after a successful authentication. */
    public void registerSuccessfulLogin(String ipAddress) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ipAddress;
    }

    /** Used by the password-reset flow, which doubles as the unlock path. */
    public void clearLock() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
        if (this.status == UserStatus.PENDING) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public Set<String> roleNames() {
        return roles.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
    }
}
